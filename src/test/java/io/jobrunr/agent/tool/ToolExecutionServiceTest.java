package io.jobrunr.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jobrunr.agent.core.AgentResult;
import io.jobrunr.agent.core.ToolRegistry;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ToolExecutionServiceTest {

    @Mock
    private JobScheduler jobScheduler;

    private ToolRegistry toolRegistry;
    private JobRunrToolExecutor toolExecutor;
    private ToolExecutionService service;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry(new ObjectMapper());
        toolExecutor = new JobRunrToolExecutor(jobScheduler, null);
        service = new ToolExecutionService(toolRegistry, toolExecutor);
    }

    @Test
    void shouldExecuteToolAndStoreResult() {
        toolRegistry.registerAgentTool("echo", (args, ctx) ->
                AgentResult.of("Echo: " + args.getOrDefault("msg", "empty")));

        service.executeToolJob("track-1", "echo", "{\"msg\":\"hello\"}", Map.of());

        assertTrue(toolExecutor.isComplete("track-1"));
        assertEquals("Echo: hello", toolExecutor.getResult("track-1").value());
    }

    @Test
    void shouldHandleToolError() {
        toolRegistry.registerAgentTool("fail", (args, ctx) -> {
            throw new RuntimeException("Boom!");
        });

        service.executeToolJob("track-2", "fail", "{}", Map.of());

        assertTrue(toolExecutor.isComplete("track-2"));
        assertTrue(toolExecutor.getResult("track-2").value().contains("Error"));
    }

    @Test
    void shouldPassContextVariables() {
        toolRegistry.registerAgentTool("ctx_tool", (args, ctx) ->
                AgentResult.of("User: " + ctx.get("user_name", "unknown")));

        service.executeToolJob("track-3", "ctx_tool", "{}", Map.of("user_name", "Nicholas"));

        assertEquals("User: Nicholas", toolExecutor.getResult("track-3").value());
    }
}
