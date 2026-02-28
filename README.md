# AgentRunr 🤖

A Java-native AI agent runtime powered by **Spring Boot**, **Spring AI**, and **JobRunr**.

Inspired by [OpenAI Swarm](https://github.com/openai/swarm)'s lightweight agent orchestration pattern and the Claw ecosystem (nanobot, PicoClaw, ZeroClaw). Think **"ZeroClaw for enterprises that run Java."**

## What is this?

An AI agent framework where:
- **Spring AI** handles LLM communication, tool calling, and MCP integration
- **JobRunr** provides production-grade task execution with retries, scheduling, and observability
- **Swarm's Agent + Handoff pattern** gives you multi-agent orchestration in clean Java

## Quick Start

```bash
# 1. Copy and configure environment variables
cp .env.example .env
# Edit .env with your API keys

# 2. Run the application
source .env && mvn spring-boot:run

# 3. Open the web UI
open http://localhost:8090
```

## Features

### Core
- **Multi-model support** — OpenAI, Anthropic, Ollama per request (`openai:gpt-4o`, `anthropic:claude-sonnet-4-20250514`, `ollama:llama3`)
- **Agent orchestration** — Swarm-inspired agent loop with handoffs and context variables
- **Tool calling** — Unified registry: `@Tool` methods, MCP servers, custom `AgentTool` implementations
- **MCP support** — Programmatic SSE client with custom auth headers (tested with n8n MCP gateway)
- **Streaming** — SSE endpoint for real-time token streaming

### Channels
- **REST API** — POST `/api/chat`, GET `/api/chat/stream` (SSE)
- **Web UI** — Dark-themed chat interface with model selector and settings
- **Telegram** — Long-polling bot with allowed user filtering

### Scheduling (JobRunr-powered 🔥)
- **Heartbeat** — Periodic task checking via HEARTBEAT.md (configurable interval)
- **Cron jobs** — Agent-managed scheduling: cron expressions, intervals, one-shot tasks
- **All JobRunr features** — Retries, dead letter queue, distributed execution, dashboard

### Built-in Tools
- `shell_exec` — Sandboxed shell commands with workspace restrictions and dangerous command blocking
- `file_read` / `file_write` / `file_list` — File operations with path traversal prevention
- `web_search` — Brave Search API integration
- `web_fetch` — URL fetching with redirect following and size limits

### Security
- API key authentication (X-API-Key header)
- Input sanitization (control chars, length limits, message count validation)
- Workspace sandboxing for file and shell tools

## Architecture

```
┌─────────────────────────────────────────┐
│          Spring Boot Application        │
├─────────────────────────────────────────┤
│  Agent Core (Swarm-inspired)            │
│  → Agent definitions with tools         │
│  → AgentRunner: LLM loop + handoffs     │
│  → ToolRegistry: @Tool + MCP + custom   │
├─────────────────────────────────────────┤
│  JobRunr Execution Layer                │
│  → Tool calls as background jobs        │
│  → Heartbeat + Cron scheduling          │
│  → Retries, observability, dashboards   │
├─────────────────────────────────────────┤
│  Channels                               │
│  → REST API + SSE streaming             │
│  → Web UI (chat + settings)             │
│  → Telegram bot                         │
├─────────────────────────────────────────┤
│  MCP Integration                        │
│  → SSE client with custom auth headers  │
│  → Auto-discovery of MCP tools          │
└─────────────────────────────────────────┘
```

## Configuration

All configuration is via environment variables. See [`.env.example`](.env.example) for the full list.

### LLM Providers

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENAI_API_KEY` | OpenAI API key | At least one provider |
| `ANTHROPIC_API_KEY` | Anthropic API key | Optional |
| `ANTHROPIC_ENABLED` | Enable Anthropic provider | Optional |
| `OLLAMA_BASE_URL` | Ollama server URL | Optional |
| `OLLAMA_MODEL` | Default Ollama model | Optional |
| `OLLAMA_ENABLED` | Enable Ollama provider | Optional |

### Channels

| Variable | Description |
|----------|-------------|
| `TELEGRAM_ENABLED` | Enable Telegram bot |
| `TELEGRAM_BOT_TOKEN` | Bot token from @BotFather |
| `TELEGRAM_ALLOWED_USERS` | Comma-separated allowed user IDs |
| `AGENT_API_KEY` | API key for REST authentication |

### MCP Servers

| Variable | Description |
|----------|-------------|
| `PERSONAL_CALENDAR_MCP_ENABLED` | Enable Google Calendar MCP |
| `PERSONAL_CALENDAR_MCP_URL` | n8n MCP SSE endpoint URL |
| `PERSONAL_CALENDAR_MCP_PASSWORD` | n8n MCP auth password |

### Built-in Tools

| Variable | Description |
|----------|-------------|
| `BRAVE_API_KEY` | Brave Search API key |
| `TOOLS_RESTRICT_WORKSPACE` | Sandbox file/shell to workspace |
| `TOOLS_WORKSPACE` | Workspace directory path |
| `TOOLS_SHELL_TIMEOUT` | Shell command timeout (seconds) |

## API Examples

```bash
# Chat (non-streaming)
curl -X POST http://localhost:8090/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-key" \
  -d '{"messages": [{"role": "user", "content": "What time is it?"}]}'

# Chat (streaming SSE)
curl -N http://localhost:8090/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-key" \
  -d '{"messages": [{"role": "user", "content": "Tell me a story"}]}'

# Use a specific model
curl -X POST http://localhost:8090/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}], "model": "anthropic:claude-sonnet-4-20250514"}'

# Schedule a cron task
curl -X POST http://localhost:8090/api/cron \
  -H "Content-Type: application/json" \
  -d '{"name": "Daily Report", "schedule": "0 9 * * *", "message": "Generate daily report"}'

# Health check
curl http://localhost:8090/api/health
```

## Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.4.3 | Application framework |
| Spring AI | 1.0.0 | LLM abstraction + tool calling + MCP |
| JobRunr | 8.4.2 | Background job execution + scheduling |
| Java | 21 | Records, pattern matching, virtual threads |

## Claude Code OAuth Integration

Use your Claude Code subscription (Pro/Max/Team) for Anthropic models without a separate API key.

### Setup

1. Install and authenticate Claude Code:
   ```bash
   # Install Claude Code CLI
   npm install -g @anthropic-ai/claude-code
   
   # Authenticate (opens browser)
   claude auth login
   ```

2. Enable in your `.env`:
   ```bash
   CLAUDE_CODE_OAUTH_ENABLED=true
   CLAUDE_CODE_MODEL=claude-sonnet-4-20250514  # optional, this is the default
   ```

3. Start the app — it reads the OAuth token from your system keychain automatically.

### How it works

- **macOS**: Reads from Keychain (`security find-generic-password -s "Claude Code-credentials"`)
- **Linux**: Reads from `~/.claude/.credentials.json`
- Token format: `sk-ant-oat01-*` (standard Anthropic OAuth access token)
- Tokens expire — run any `claude` command to refresh when needed
- Token metadata (subscription type, rate limit tier) available via `GET /api/claude-code-oauth`

### Admin API

```bash
# Check token status
curl http://localhost:8090/api/claude-code-oauth

# Force token refresh from keychain
curl -X POST http://localhost:8090/api/claude-code-oauth/refresh
```

## Development

```bash
# Run tests (120 tests)
mvn clean verify

# Run with debug logging
mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.io.agentrunr=DEBUG"
```

## Roadmap

- [ ] Semantic memory (vector store + hybrid search)
- [ ] Autonomy levels (readonly/supervised/full)
- [ ] Observability (Micrometer + Prometheus)
- [ ] Discord + Slack channels
- [ ] Config hot-reload
- [ ] Encryption at rest

## License

TBD
