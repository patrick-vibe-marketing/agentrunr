package io.agentrunr.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(new ObjectMapper());
    }

    @Test
    void shouldRegisterAndExecuteAgentTool() {
        registry.registerAgentTool("greet", (args, ctx) ->
                AgentResult.of("Hello, " + args.getOrDefault("name", "world") + "!"));

        var result = registry.executeTool("greet", "{\"name\":\"Nicholas\"}", new AgentContext());

        assertEquals("Hello, Nicholas!", result.value());
        assertNull(result.handoffAgent());
    }

    @Test
    void shouldHandleHandoffTool() {
        var billingAgent = new Agent("BillingAgent", "Handle billing.");
        registry.registerAgentTool("transfer_to_billing", (args, ctx) ->
                AgentResult.handoff(billingAgent));

        var result = registry.executeTool("transfer_to_billing", "{}", new AgentContext());

        assertEquals(billingAgent, result.handoffAgent());
    }

    @Test
    void shouldUpdateContextViaTools() {
        registry.registerAgentTool("login", (args, ctx) -> {
            ctx.set("authenticated", "true");
            return AgentResult.withContext("Logged in.", Map.of("user_id", "42"));
        });

        var context = new AgentContext();
        var result = registry.executeTool("login", "{}", context);

        assertEquals("true", context.get("authenticated"));
        assertEquals("42", result.contextVariables().get("user_id"));
    }

    @Test
    void shouldReturnErrorForMissingTool() {
        var result = registry.executeTool("nonexistent", "{}", new AgentContext());

        assertTrue(result.value().contains("not found"));
    }

    @Test
    void shouldReturnToolCallbacksForKnownTools() {
        // Agent tools don't show up as callbacks (they're handled differently)
        registry.registerAgentTool("my_tool", (args, ctx) -> AgentResult.of("ok"));

        var callbacks = registry.getToolCallbacks(List.of("my_tool"));

        // Agent tools aren't ToolCallbacks, so list should be empty
        assertTrue(callbacks.isEmpty());
    }

    @Test
    void shouldHandleMalformedArguments() {
        registry.registerAgentTool("echo", (args, ctx) ->
                AgentResult.of("Args: " + args.toString()));

        var result = registry.executeTool("echo", "not-json", new AgentContext());

        // Should not throw, returns empty map
        assertEquals("Args: {}", result.value());
    }

    @Test
    void shouldHandleNullArguments() {
        registry.registerAgentTool("echo", (args, ctx) ->
                AgentResult.of("Args: " + args.toString()));

        var result = registry.executeTool("echo", null, new AgentContext());

        assertEquals("Args: {}", result.value());
    }
}
