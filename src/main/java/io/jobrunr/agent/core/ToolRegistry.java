package io.jobrunr.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for agent tools. Manages tool callbacks and dispatches tool executions.
 *
 * <p>Tools can be registered as:</p>
 * <ul>
 *   <li>Spring AI ToolCallback instances (from @Tool annotations or MCP)</li>
 *   <li>Custom AgentTool implementations for agent-specific logic (e.g., handoffs)</li>
 * </ul>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolCallback> toolCallbacks = new HashMap<>();
    private final Map<String, FunctionCallback> functionCallbacks = new HashMap<>();
    private final Map<String, AgentTool> agentTools = new HashMap<>();
    private final ObjectMapper objectMapper;

    public ToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a Spring AI tool callback.
     */
    public void registerToolCallback(String name, ToolCallback callback) {
        toolCallbacks.put(name, callback);
        log.debug("Registered tool callback: {}", name);
    }

    /**
     * Registers a Spring AI function callback (e.g., from MCP providers).
     */
    public void registerFunctionCallback(String name, FunctionCallback callback) {
        functionCallbacks.put(name, callback);
        log.debug("Registered function callback: {}", name);
    }

    /**
     * Registers a custom agent tool (supports handoffs and context variable updates).
     */
    public void registerAgentTool(String name, AgentTool tool) {
        agentTools.put(name, tool);
        log.debug("Registered agent tool: {}", name);
    }

    /**
     * Returns tool callbacks for the given tool names.
     */
    public List<ToolCallback> getToolCallbacks(List<String> toolNames) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : toolNames) {
            ToolCallback cb = toolCallbacks.get(name);
            if (cb != null) {
                callbacks.add(cb);
            } else if (!agentTools.containsKey(name)) {
                log.warn("Tool '{}' not found in registry", name);
            }
        }
        return callbacks;
    }

    /**
     * Executes a tool by name with the given arguments.
     *
     * @param toolName  the tool to execute
     * @param arguments JSON string of arguments
     * @param context   the current agent context
     * @return the tool execution result
     */
    public AgentResult executeTool(String toolName, String arguments, AgentContext context) {
        // Check agent tools first (support handoffs)
        AgentTool agentTool = agentTools.get(toolName);
        if (agentTool != null) {
            try {
                Map<String, Object> args = parseArguments(arguments);
                return agentTool.execute(args, context);
            } catch (Exception e) {
                log.error("Error executing agent tool '{}': {}", toolName, e.getMessage(), e);
                return AgentResult.of("Error: " + e.getMessage());
            }
        }

        // Fall back to Spring AI tool callbacks
        ToolCallback callback = toolCallbacks.get(toolName);
        if (callback != null) {
            try {
                String result = callback.call(arguments);
                return AgentResult.of(result);
            } catch (Exception e) {
                log.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                return AgentResult.of("Error: " + e.getMessage());
            }
        }

        // Fall back to function callbacks (MCP)
        FunctionCallback functionCallback = functionCallbacks.get(toolName);
        if (functionCallback != null) {
            try {
                String result = functionCallback.call(arguments);
                return AgentResult.of(result);
            } catch (Exception e) {
                log.error("Error executing MCP tool '{}': {}", toolName, e.getMessage(), e);
                return AgentResult.of("Error: " + e.getMessage());
            }
        }

        log.error("Tool '{}' not found", toolName);
        return AgentResult.of("Error: Tool '" + toolName + "' not found.");
    }

    /**
     * Parses a JSON arguments string into a map.
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", arguments, e);
            return Map.of();
        }
    }

    /**
     * Functional interface for agent-aware tools that can trigger handoffs
     * and update context variables.
     */
    @FunctionalInterface
    public interface AgentTool {
        /**
         * Executes the tool with parsed arguments and access to the agent context.
         *
         * @param arguments parsed arguments map
         * @param context   the current agent context
         * @return the tool result (may include handoff or context updates)
         */
        AgentResult execute(Map<String, Object> arguments, AgentContext context);
    }
}
