# CLAUDE.md — Agent Onboarding Guide for AgentRunr

## What Is This?

AgentRunr is a Java-native AI agent runtime — a port of OpenAI Swarm's agent orchestration pattern to Java using Spring Boot + Spring AI + JobRunr. Think "enterprise-safe Java version of OpenClaw-ish systems powered by JobRunr."

**Repo:** https://github.com/patrick-vibe-marketing/agentrunr
**Local path:** `/Users/nicholas/Projects/jobrunr-agent` (directory name predates rename)

## Tech Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 | Records, pattern matching, text blocks |
| Spring Boot | 3.4.3 | |
| Spring AI | 1.0.0 (GA) | Maven Central, no milestone repos needed |
| JobRunr | 8.4.2 | Background job scheduling |
| MCP SDK | 0.10.0 | `io.modelcontextprotocol.sdk:mcp` |
| Maven | 3.9.12 | `/usr/local/Cellar/maven/3.9.12/bin/mvn` |

## Build & Run

```bash
export JAVA_HOME=/usr/local/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Build + test (120 tests)
mvn clean verify

# Run (port 8090 by default)
mvn spring-boot:run
```

**You MUST export JAVA_HOME and PATH before running mvn.** The system default Java is not 21.

## Project Structure

```
io.agentrunr
├── AgentRunrApplication.java        # Main entry point
├── core/                            # Agent orchestration (Swarm port)
│   ├── Agent.java                   # Agent record (name, model, instructions, tools)
│   ├── AgentRunner.java             # Core loop: send → tool calls → recurse
│   ├── AgentContext.java            # Conversation context with variables
│   ├── AgentResult.java             # Result record
│   ├── AgentResponse.java           # Response wrapper
│   ├── ChatMessage.java             # Message record
│   └── ToolRegistry.java            # Central tool registration
├── config/
│   ├── ModelRouter.java             # Routes to OpenAI/Ollama/Anthropic per request
│   ├── McpConfig.java               # Auto-discovers MCP ToolCallbackProviders
│   ├── PersonalCalendarMcpConfig.java # Programmatic MCP client (n8n, custom headers)
│   ├── ClaudeCodeOAuthProvider.java # (DO NOT USE — Anthropic terms prohibit it)
│   └── ClaudeCodeAnthropicConfig.java # (DO NOT USE — same reason)
├── setup/
│   ├── CredentialStore.java         # AES-256-GCM encrypted key storage (~/.agentrunr/)
│   ├── SetupRunner.java             # CLI first-run prompts (or --setup flag)
│   ├── SetupController.java         # REST API for web setup
│   ├── SetupInterceptor.java        # Redirects to /setup if unconfigured
│   └── SetupWebConfig.java          # Web config for setup flow
├── channel/
│   ├── Channel.java                 # Channel interface (NOT sealed — extensible)
│   ├── ChatController.java          # REST /api/chat + SSE streaming
│   ├── TelegramChannel.java         # Telegram long-polling integration
│   ├── AdminController.java         # Settings, providers, sessions API
│   ├── AgentConfigurer.java         # Runtime agent configuration
│   └── ChannelRegistry.java         # Multi-channel management
├── heartbeat/
│   ├── HeartbeatService.java        # JobRunr-powered periodic task checking
│   └── HeartbeatJob.java            # Reads HEARTBEAT.md, triggers agent
├── cron/
│   ├── CronService.java             # JobRunr cron scheduling for agent tasks
│   └── CronTools.java               # @Tool methods for cron management
├── tool/
│   ├── BuiltInTools.java            # shell_exec, file_read/write/list, web_search, web_fetch
│   ├── SampleTools.java             # Example tools (weather, time, calculate)
│   ├── JobRunrToolExecutor.java      # Executes tools via JobRunr @Job
│   └── ToolExecutionService.java    # Tool execution orchestration
├── memory/
│   └── FileMemoryStore.java         # Daily session logs, context persistence
└── security/
    ├── SecurityConfig.java          # Spring Security config
    ├── ApiKeyFilter.java            # API key authentication
    └── InputSanitizer.java          # Input validation/sanitization
```

## Key Architecture Decisions

1. **Spring AI over LangChain4j** — Native Spring integration, built-in @Tool + MCP support
2. **ModelRouter with @Nullable injection** — Each provider bean is optional; router selects per-request
3. **Channel interface is NOT sealed** — Designed for extensibility (add Discord, Slack, etc.)
4. **CredentialStore over env vars** — Interactive auth setup like Claude Code; AES-256-GCM encrypted on disk; takes priority over env vars
5. **Programmatic MCP clients** — Spring AI auto-config SSE only supports `url` + `sseEndpoint`, not custom headers. Use manual `HttpClientSseClientTransport` + `McpClient.sync()` for servers needing auth headers
6. **Heartbeat + Cron as killer feature** — JobRunr provides persistent distributed scheduling, unlike in-memory cron

## Spring AI 1.0.0 GA API Notes

These changed from M6 → GA. Don't use old patterns:

- `FunctionCallback` → `ToolCallback`
- `callback.getName()` → `callback.getToolDefinition().name()`
- Artifact IDs renamed:
  - `spring-ai-openai-spring-boot-starter` → `spring-ai-starter-model-openai`
  - `spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`
  - `spring-ai-anthropic-spring-boot-starter` → `spring-ai-starter-model-anthropic`
  - `spring-ai-mcp-client-spring-boot-starter` → `spring-ai-starter-mcp-client`

## MCP Integration Gotchas

If adding new MCP servers with custom auth:

1. **URI resolution:** `URI.resolve("/sse")` with leading slash resets to root. Use `URI.resolve("sse")` (relative) with baseUri ending in `/`
2. **SDK 0.10.0 Builder bug:** `HttpClientSseClientTransport.Builder.customizeRequest()` consumers aren't wired in `build()`. Use the 4-arg public constructor directly: `new HttpClientSseClientTransport(objectMapper, httpClient, requestBuilder, sseUri)`
3. **Credentials via env vars only** — Never hardcode in application.yml

## Configuration

All secrets via env vars (see `application.yml`):
- `OPENAI_API_KEY` — OpenAI
- `ANTHROPIC_API_KEY` + `ANTHROPIC_ENABLED=true` — Anthropic
- `OLLAMA_BASE_URL` + `OLLAMA_ENABLED=true` — Ollama
- `TELEGRAM_BOT_TOKEN` + `TELEGRAM_ENABLED=true` — Telegram bot
- `AGENT_API_KEY` — API authentication
- `PERSONAL_CALENDAR_MCP_*` — MCP calendar integration
- `BRAVE_API_KEY` — Web search tool

Or use the interactive setup: run with `--setup` flag or visit `/setup` in browser.

## Tests

120 tests across 19 test classes. All must pass before committing:
```bash
mvn clean verify
```

## What's NOT Done Yet (Roadmap)

- [ ] Semantic memory (Spring AI vector store + embeddings)
- [ ] Autonomy levels (readonly/supervised/full modes)
- [ ] Observability (Micrometer → Prometheus)
- [ ] Discord + Slack channels
- [ ] Config hot-reload + health diagnostics
- [ ] Encryption at rest for memory/workspace data
- [ ] End-to-end testing with real API keys

## Don'ts

- **DO NOT use Claude Code OAuth tokens** — Anthropic terms (updated 2026-02-19) explicitly prohibit use in other products/services. The `ClaudeCodeOAuthProvider` and `ClaudeCodeAnthropicConfig` exist but should not be enabled.
- **DO NOT hardcode secrets** in application.yml or commit them
- **DO NOT use `FunctionCallback`** — it's the old Spring AI M6 API, use `ToolCallback`
- **DO NOT use Spring AI milestone repos** — 1.0.0 is GA in Maven Central
