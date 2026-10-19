import { PATHS } from './server';
import fs from 'fs';
import { ResponseBody } from '../types';
import path from 'path';
import { runPythonScript } from '../../../server/src/trpc/socket';
import { execSync } from 'child_process';
import {
    DEFAULT_FRIDAY_RUNTIME,
    FridayConfig,
    FridayRuntime,
} from './friday-common';

// Browser-safe contracts shared with the client. Client code should import
// them from './friday-common' directly, since this module pulls in Node.js
// dependencies that cannot be bundled for the browser.
export * from './friday-common';

export class FridayConfigManager {
    private static instance: FridayConfigManager;
    private config: FridayConfig | null;

    private constructor() {
        this.config = null;
        this.loadFridayConfig();
    }

    static getInstance() {
        if (!FridayConfigManager.instance) {
            FridayConfigManager.instance = new FridayConfigManager();
        }
        return FridayConfigManager.instance;
    }

    private loadFridayConfig() {
        const fridayConfigPath = PATHS.getFridayConfigPath();
        try {
            if (fs.existsSync(fridayConfigPath)) {
                const fridayConfig = JSON.parse(
                    fs.readFileSync(fridayConfigPath, 'utf8'),
                );
                this.config = { ...this.config, ...fridayConfig };
            }
        } catch (error) {
            console.error('Failed to load friday config:', error);
        }
    }

    getConfig() {
        return this.config;
    }

    saveConfig() {
        try {
            const fridayConfigPath = PATHS.getFridayConfigPath();
            fs.mkdirSync(path.dirname(fridayConfigPath), { recursive: true });
            console.debug(
                'Saving friday config to:',
                fridayConfigPath,
                this.config,
            );

            fs.writeFileSync(
                fridayConfigPath,
                JSON.stringify(this.config, null, 2),
            );
        } catch (error) {
            console.error('Failed to save friday config:', error);
        }
    }

    updateConfig(newConfig: FridayConfig) {
        this.config = newConfig;
        this.saveConfig();
    }

    verifyPythonEnv(pythonEnv: string) {
        const pythonPath = path.normalize(pythonEnv);
        // Check if the file exists
        if (!fs.existsSync(pythonPath)) {
            return {
                success: false,
                message: 'The Python environment path does not exist.',
            } as ResponseBody;
        }

        // Check if the path is a file
        try {
            const stats = fs.statSync(pythonPath);
            if (!stats.isFile()) {
                return {
                    success: false,
                    message: 'Not a valid Python environment path.',
                } as ResponseBody;
            }
        } catch (error) {
            return {
                success: false,
                message: `Error accessing Python environment path: ${error}`,
            } as ResponseBody;
        }

        // Check if the python version is 3.10 or higher
        try {
            const cmd =
                process.platform === 'win32'
                    ? `"${pythonPath}" --version`
                    : `${pythonPath} --version`;

            const versionOutput = execSync(cmd, { encoding: 'utf8' })
                .toString()
                .trim();
            const versionMatch = versionOutput.match(/Python (\d+)\.(\d+)/);
            if (!versionMatch) {
                return {
                    success: false,
                    message: 'Failed to get Python version.',
                } as ResponseBody;
            }
            const major = parseInt(versionMatch[1], 10);
            const minor = parseInt(versionMatch[2], 10);
            if (major < 3 || (major === 3 && minor < 10)) {
                return {
                    success: false,
                    message: 'Python version must be 3.10 or higher.',
                } as ResponseBody;
            }
            return {
                success: true,
                message: 'Configuration is valid.',
            } as ResponseBody;
        } catch {
            return {
                success: false,
                message: 'Not a valid Python environment.',
            } as ResponseBody;
        }
    }

    /**
     * Verify the runtime environment of the selected Friday implementation.
     *
     * @param runtime 'python' or 'java'
     * @param env     the python interpreter path, or the java executable path
     */
    verifyRuntimeEnv(runtime: FridayRuntime, env: string) {
        return runtime === 'java'
            ? this.verifyJavaEnv(env)
            : this.verifyPythonEnv(env);
    }

    /**
     * Verify the java executable used by the java runtime.
     * AgentScope Java requires JDK 17 or higher.
     */
    verifyJavaEnv(javaEnv: string) {
        const command = (javaEnv ?? '').trim() || 'java';

        try {
            const cmd = process.platform === 'win32' ? `"${command}"` : command;
            const combined = execSync(`${cmd} -version 2>&1`, {
                encoding: 'utf8',
            }).toString();

            // Both `java -version` and `javac -version` print something like
            // openjdk version "17.0.9" 2023-10-17
            const versionMatch = combined.match(/version "(\d+)(?:\.(\d+))?/);
            if (!versionMatch) {
                return {
                    success: false,
                    message: `Failed to get the Java version from: ${command}`,
                } as ResponseBody;
            }
            const major = parseInt(versionMatch[1], 10);
            // Java 1.8 and older report versions like "1.8.0_382".
            const effectiveMajor =
                major === 1 ? parseInt(versionMatch[2], 10) : major;
            if (Number.isNaN(effectiveMajor) || effectiveMajor < 17) {
                return {
                    success: false,
                    message: `Java version must be 17 or higher, got ${combined.split('\n')[0]}.`,
                } as ResponseBody;
            }
            return {
                success: true,
                message: 'Configuration is valid.',
            } as ResponseBody;
        } catch (error) {
            return {
                success: false,
                message: `Not a valid Java environment: ${error}`,
            } as ResponseBody;
        }
    }

    async installRequirements(
        pythonEnv: string,
        runtime: FridayRuntime = DEFAULT_FRIDAY_RUNTIME,
    ) {
        // The java implementation ships as a self-contained fat jar, there is
        // nothing to install at runtime.
        if (runtime === 'java') {
            return {
                success: true,
                message: 'The Java runtime needs no extra installation.',
            } as ResponseBody;
        }

        // Pin agentscope to 1.x: Friday's Python code uses the 1.x API
        // (e.g. `agentscope.agent.ReActAgent` + `formatter`), which was
        // removed in agentscope 2.x.
        // Pin cryptography < 49: 49+ only ships arm64 wheels on macOS, so
        // Intel Macs would fall back to a source build that requires Rust
        // and OpenSSL.
        const res = await runPythonScript(pythonEnv, [
            '-m',
            'pip',
            'install',
            'agentscope[full]==1.0.21',
            'cryptography<49',
        ]);
        console.debug('Install requirements:', res);
        if (res.success) {
            return {
                success: true,
                message: 'Successfully installed requirements',
            } as ResponseBody;
        } else {
            return {
                success: false,
                message: `Failed to install requirements: ${res.error}`,
            } as ResponseBody;
        }
    }

    /**
     * The default entry script of Friday.
     *
     * - python: `app/friday/main.py`
     * - java:   `app/friday_java/friday.jar` in production (copied by
     *           `npm run build:java`), or the maven output
     *           `app/friday_java/target/friday.jar` in development.
     */
    getDefaultMainScriptPath(runtime: FridayRuntime = DEFAULT_FRIDAY_RUNTIME) {
        if (runtime === 'java') {
            if (process.env.NODE_ENV === 'production') {
                // dist/app/friday_java/friday.jar
                return path.join(
                    __dirname,
                    '../../../app/friday_java/friday.jar',
                );
            }
            // packages/app/friday_java/target/friday.jar
            return path.join(
                __dirname,
                '../../../app/friday_java/target/friday.jar',
            );
        }

        if (process.env.NODE_ENV === 'production') {
            // In production, the structure is:
            // dist/
            //   server/
            //     src/
            //       index.js (current __dirname)
            //   app/
            //     friday/
            //       main.py
            return path.join(__dirname, '../../../app/friday/main.py');
        }

        // In development, the structure is:
        // packages/
        //   server/
        //     src/
        //       index.ts (current __dirname)
        //   app/
        //     friday/
        //       main.py
        return path.join(__dirname, '../../../app/friday/main.py');
    }

    /**
     * Build the command that launches Friday.
     *
     * @returns `[command, ...args]`, ready to be handed to `spawn`.
     */
    getLaunchCommand(
        config: FridayConfig,
        scriptArgs: string[],
    ): { command: string; args: string[] } {
        const runtime: FridayRuntime = config.runtime ?? DEFAULT_FRIDAY_RUNTIME;

        if (runtime === 'java') {
            const jarPath =
                config.mainScriptPath || this.getDefaultMainScriptPath('java');
            return {
                command: (config.javaEnv ?? '').trim() || 'java',
                args: ['-jar', jarPath, ...scriptArgs],
            };
        }

        const scriptPath =
            config.mainScriptPath || this.getDefaultMainScriptPath('python');
        return {
            command: (config.pythonEnv ?? '').trim() || 'python3',
            args: [scriptPath, ...scriptArgs],
        };
    }
}
