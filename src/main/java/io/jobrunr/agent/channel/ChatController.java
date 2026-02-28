package io.jobrunr.agent.channel;

import io.jobrunr.agent.core.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API channel for the agent.
 * Provides a simple chat endpoint that accepts messages and returns agent responses.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;

    public ChatController(AgentRunner agentRunner, AgentConfigurer agentConfigurer) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
    }

    /**
     * Chat endpoint. Sends a message to the default agent and returns the response.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        Agent agent = agentConfigurer.getDefaultAgent();

        List<ChatMessage> messages = request.messages().stream()
                .map(m -> new ChatMessage(
                        ChatMessage.Role.valueOf(m.role().toUpperCase()),
                        m.content(),
                        null,
                        null
                ))
                .toList();

        AgentContext context = new AgentContext();
        if (request.contextVariables() != null) {
            context.merge(request.contextVariables());
        }

        AgentResponse response = agentRunner.run(agent, messages, context, request.maxTurns() > 0 ? request.maxTurns() : 10);

        return ResponseEntity.ok(new ChatResponseDto(
                response.lastMessage(),
                response.activeAgent().name(),
                response.contextVariables()
        ));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "jobrunr-agent"));
    }

    // --- DTOs ---

    public record ChatRequestDto(
            List<MessageDto> messages,
            Map<String, String> contextVariables,
            int maxTurns
    ) {}

    public record MessageDto(String role, String content) {}

    public record ChatResponseDto(String response, String agent, Map<String, String> contextVariables) {}
}
