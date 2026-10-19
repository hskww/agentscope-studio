# Friday (AgentScope Java)

`packages/app/friday` 的 Java 重写版本，基于 **AgentScope Java 2.0**
（`io.agentscope:agentscope-core:2.0.3`）。

命令行参数与原 Python 版**完全一致**，因此可以直接替换 Studio 中 Friday 的运行入口。

## 环境要求

- JDK 17+
- Maven 3.9+

## 构建

```bash
cd packages/app/friday_java
mvn package          # 产出 target/friday.jar（可执行 fat jar）
mvn test             # 冒烟测试：参数解析、工具注册、模型解析、Agent 构建
```

## 运行

```bash
java -jar target/friday.jar \
  --query '"帮我看下当前目录有哪些文件"' \
  --studio_url http://localhost:3000 \
  --llmProvider deepseek \
  --modelName deepseek-chat \
  --apiKey sk-xxxxxx \
  --writePermission false \
  --clientKwargs '{"base_url":"https://api.deepseek.com"}' \
  --generateKwargs '{"temperature":0.2}'
```

参数说明（与 `packages/app/friday/args.py` 一一对应）：

| 参数 | 说明 |
| --- | --- |
| `--query` | 用户输入，JSON 字符串（纯字符串或 content block 数组） |
| `--studio_url` | Studio 服务地址，用于推送消息与接收中断信号 |
| `--llmProvider` | `dashscope` / `openai` / `anthropic` / `gemini` / `ollama` / `deepseek` |
| `--modelName` | 模型名，如 `deepseek-chat`、`qwen-plus` |
| `--apiKey` | 模型 API Key（ollama 可留空） |
| `--writePermission` | 是否注册 `write_text_file` 工具 |
| `--clientKwargs` | JSON，客户端参数；`base_url` / `baseUrl` 会被识别为服务地址 |
| `--generateKwargs` | JSON，生成参数，以 provider option 形式透传 |

## 模块与源码结构

```
src/main/java/io/agentscope/studio/friday/
├── Main.java                       入口：装配 Toolkit / Agent / 会话 / Socket
├── FridayArgs.java                 命令行参数解析
├── ModelFactory.java               模型解析（ModelRegistry + ModelCreationContext）
├── StudioBridge.java               向 Studio 推送消息与结束信号（HTTP）
├── StudioSocket.java               socket.io /friday 命名空间，接收 interrupt
├── StudioForwardingMiddleware.java 从事件流重建消息并推送（对应 pre_print / post_reply）
├── LocalPaths.java                 本地数据目录解析
└── tool/
    ├── CommandTools.java           execute_shell_command / execute_python_code
    ├── FileTools.java              view_text_file / insert_text_file
    ├── WriteFileTools.java         write_text_file（仅 writePermission 时注册）
    └── AgentScopeDocsTools.java    view_agentscope_readme / faq / library
```

## 与 Python 版的对应关系

| Python（agentscope 1.x） | Java（agentscope 2.0） |
| --- | --- |
| `ReActAgent` + `formatter` | `ReActAgent.builder().model(...)`（formatter 由 provider 自动选择） |
| `pre_print` / `post_reply` hook | `StudioForwardingMiddleware`（`onAgent` 扩展点） |
| `JSONSession` | `JsonFileAgentStateStore` + `RuntimeContext(userId, sessionId)` |
| `socketio.AsyncClient` | `io.socket:socket.io-client`（`/friday` 命名空间） |
| `Toolkit.create_tool_group` | `ToolGroup.builder()` + `toolkit.registration().group(...)` |
| `get_model()` 分支 | `ModelRegistry.resolve("provider:model", ModelCreationContext)` |

## 模型 Provider

每个 provider 由独立扩展模块提供，已全部引入：

- `agentscope-extensions-model-dashscope`
- `agentscope-extensions-model-openai`（同时服务 `deepseek:` 前缀）
- `agentscope-extensions-model-anthropic`
- `agentscope-extensions-model-gemini`
- `agentscope-extensions-model-ollama`

DeepSeek 通过 OpenAI 兼容栈支持，`--llmProvider deepseek` 默认指向
`https://api.deepseek.com` 并使用 `DeepSeekFormatter`。

## 接入 Studio（已完成）

Studio 现在支持切换 Friday 的运行实现，配置项 `runtime`：

- `python`（默认）：`<pythonEnv> <main.py> --query ... --studio_url ...`
- `java`：`[javaEnv] -jar <friday.jar> --query ... --studio_url ...`

改动位置：

| 文件 | 改动 |
| --- | --- |
| `packages/shared/src/config/friday.ts` | `FridayConfig` 新增 `runtime` / `javaEnv`；新增 `verifyJavaEnv`、`verifyRuntimeEnv`、`getLaunchCommand`；`installRequirements` 对 java 直接返回成功 |
| `packages/server/src/trpc/socket.ts` | 新增通用 `runCommand`；Friday 启动改用 `getLaunchCommand`；`verifyFridayConfig` / `installFridayRequirements` 接收 `{runtime, env}` |
| `packages/client/src/pages/FridayPage/config.tsx` | 新增 `fridayRuntimeOptions` |
| `packages/client/src/pages/FridayPage/SettingPage/index.tsx` | 新增 Runtime 下拉，按 runtime 切换 Python/Java 环境输入框与入口路径 |
| `packages/client/src/context/FridaySettingRoomContext.tsx` | `verifyRuntimeEnv` / `installFridayRequirements` 适配新协议 |
| `package.json` | 新增 `build:java`（`mvn package` 并把 jar 拷到 `dist/app/friday_java/friday.jar`） |

设置页选择 **Java (agentscope-java 2.0)** 后填：

- Java Environment：`/usr/bin/java`（要求 JDK 17+）
- Friday Jar Path：留空用默认路径，或填 `packages/app/friday_java/target/friday.jar`
- LLM Provider 选 `DeepSeek`，模型填 `deepseek-chat`

生产环境先执行 `npm run build:java` 把 jar 放进 `dist/app/friday_java/friday.jar`。
