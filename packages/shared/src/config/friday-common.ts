/**
 * Browser-safe Friday config contracts.
 *
 * Keep this module free of any Node.js dependency (`fs`, `path`,
 * `child_process`, ...) and server-side imports, so that it can be imported
 * from client code. The server-only `FridayConfigManager` lives in
 * `./friday.ts`, which re-exports everything defined here.
 */

/** Which Friday implementation Studio should launch. */
export type FridayRuntime = 'python' | 'java';

export const DEFAULT_FRIDAY_RUNTIME: FridayRuntime = 'python';

export interface FridayConfig {
    /** Absolute path of the python interpreter, required by the python runtime. */
    pythonEnv?: string;
    mainScriptPath?: string;
    llmProvider: string;
    modelName: string;
    writePermission: boolean;
    baseUrl?: string;
    /** Which implementation runs Friday. Defaults to 'python'. */
    runtime?: FridayRuntime;
    /**
     * Absolute path of the java executable (or a bare command such as `java`
     * resolvable through PATH), required by the java runtime.
     */
    javaEnv?: string;
}
