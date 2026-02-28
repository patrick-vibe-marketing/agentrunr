# JobRunr Agent 🤖

A Java-native AI agent runtime powered by **Spring Boot**, **Spring AI**, and **JobRunr**.

Inspired by [OpenAI Swarm](https://github.com/openai/swarm)'s lightweight agent orchestration pattern and the *Claw ecosystem ([nanobot](https://github.com/HKUDS/nanobot), [PicoClaw](https://github.com/sipeed/picoclaw), [ZeroClaw](https://github.com/zeroclaw-labs/zeroclaw)).

## What is this?

An AI agent framework where:
- **Spring AI** handles LLM communication and tool calling (including MCP)
- **JobRunr** provides production-grade task execution with retries, scheduling, and observability
- **Swarm's Agent + Handoff pattern** gives you multi-agent orchestration in ~500 lines of Java

## Quick Start

```bash
# Set your OpenAI API key
export OPENAI_API_KEY=sk-...

# Run the application
mvn spring-boot:run
```

### Chat via REST API

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "Hello, what can you do?"}],
    "maxTurns": 10
  }'
```

### JobRunr Dashboard

Open http://localhost:8000 to see the JobRunr dashboard with all agent task activity.

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
│  → Scheduled agent runs                 │
│  → Built-in dashboard                   │
├─────────────────────────────────────────┤
│  Channels                               │
│  → REST API                             │
│  → Telegram (planned)                   │
└─────────────────────────────────────────┘
```

## Core Concepts

### Agent
An agent has a name, model, instructions (system prompt), and tools. Like Swarm, agents are lightweight definitions, not heavy objects.

```java
var agent = new Agent("Assistant", "gpt-4o", "You are helpful.", List.of("search", "calculator"));
```

### Handoff
Tools can return an `AgentResult.handoff(otherAgent)` to transfer the conversation to a different agent. This enables triage patterns (e.g., route customer to billing vs. technical support).

### Context Variables
Shared state across tool calls within a run. Tools can read and write context without relying on the LLM to pass data.

### ToolRegistry
Combines Spring AI's `@Tool` annotated methods, MCP server tools, and custom `AgentTool` implementations into a unified registry.

## Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.4.x | Application framework |
| Spring AI | 1.0.x | LLM abstraction + tool calling + MCP |
| JobRunr | 8.4.x | Background job execution + scheduling |
| Java | 21 | Records, pattern matching, text blocks |

## Multi-Model Support

Agents can use any configured provider. Specify the model per-agent or per-request:

```bash
# Use OpenAI (default)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}], "maxTurns": 10}'

# Use local Ollama
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}], "model": "ollama:llama3", "maxTurns": 10}'

# Use Anthropic
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}], "model": "anthropic:claude-sonnet-4-20250514", "maxTurns": 10}'
```

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENAI_API_KEY` | OpenAI API key | At least one provider |
| `ANTHROPIC_API_KEY` | Anthropic API key | Optional |
| `OLLAMA_BASE_URL` | Ollama server URL (default: http://localhost:11434) | Optional |
| `OLLAMA_MODEL` | Default Ollama model (default: llama3.2) | Optional |
| `TELEGRAM_ENABLED` | Enable Telegram bot (true/false) | Optional |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token from @BotFather | If Telegram enabled |
| `TELEGRAM_ALLOWED_USERS` | Comma-separated Telegram user IDs | Optional |
| `AGENT_API_KEY` | API key for REST endpoint authentication | Optional |
| `AGENT_MEMORY_PATH` | Path for file-based memory (default: ./data/memory) | Optional |

## Project Status

- [x] Phase 1: Core agent loop (Agent, AgentRunner, ToolRegistry, REST API)
- [x] Phase 2: JobRunr integration (tools as background jobs)
- [x] Phase 3: MCP support (auto-discovery via Spring AI)
- [x] Phase 4: Memory (file-based) & Telegram channel
- [x] Phase 5: Security (API key auth, input sanitization)

## License

TBD
