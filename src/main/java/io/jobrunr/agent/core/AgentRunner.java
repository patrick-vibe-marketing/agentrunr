package io.jobrunr.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent execution engine, inspired by OpenAI Swarm's core run loop.
 *
 * <p>Executes an agent by repeatedly:</p>
 * <ol>
 *   <li>Sending the conversation to the LLM with available tools</li>
 *   <li>Processing any tool calls the LLM makes</li>
 *   <li>Handling agent handoffs when a tool returns a new agent</li>
 *   <li>Looping until the LLM responds without tool calls, or max turns is reached</li>
 * </ol>
 *
 * <p>This is the Java equivalent of Swarm's {@code client.run()} method.</p>
 */
@Component
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);
    private static final int DEFAULT_MAX_TURNS = 10;

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;

    public AgentRunner(ChatModel chatModel, ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Runs the agent with user messages and default settings.
     *
     * @param agent    the agent to run
     * @param messages the conversation messages
     * @return the agent response
     */
    public AgentResponse run(Agent agent, List<ChatMessage> messages) {
        return run(agent, messages, new AgentContext(), DEFAULT_MAX_TURNS);
    }

    /**
     * Runs the agent with full control over context and turn limits.
     *
     * @param agent    the starting agent
     * @param messages the conversation messages
     * @param context  shared context variables
     * @param maxTurns maximum number of LLM call turns before stopping
     * @return the agent response with final messages, active agent, and context
     */
    public AgentResponse run(Agent agent, List<ChatMessage> messages, AgentContext context, int maxTurns) {
        Agent activeAgent = agent;
        List<ChatMessage> history = new ArrayList<>(messages);
        int turns = 0;

        while (turns < maxTurns) {
            turns++;
            log.debug("Turn {}/{} with agent '{}'", turns, maxTurns, activeAgent.name());

            // Build the prompt with system instructions and conversation history
            String systemInstructions = activeAgent.resolveInstructions(context.toMap());
            List<Message> springMessages = toSpringMessages(systemInstructions, history);

            // Configure tool calling options
            ChatClient.ChatClientRequestSpec requestSpec = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .messages(springMessages);

            // Add tools if the agent has any
            List<String> tools = activeAgent.toolNames();
            if (tools != null && !tools.isEmpty()) {
                var toolCallbacks = toolRegistry.getToolCallbacks(tools);
                if (!toolCallbacks.isEmpty()) {
                    requestSpec = requestSpec.tools(toolCallbacks);
                }
            }

            // Call the LLM
            ChatResponse response = requestSpec.call().chatResponse();
            String assistantContent = response.getResult().getOutput().getText();

            // Check for tool calls in the response
            var toolCalls = response.getResult().getOutput().getToolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                // No tool calls — agent is done speaking
                history.add(ChatMessage.assistant(assistantContent, activeAgent.name()));
                log.debug("Agent '{}' completed with response", activeAgent.name());
                break;
            }

            // Process tool calls
            history.add(ChatMessage.assistant(assistantContent, activeAgent.name()));

            for (var toolCall : toolCalls) {
                log.debug("Agent '{}' calling tool: {}", activeAgent.name(), toolCall.name());

                // Execute the tool via the registry
                AgentResult result = toolRegistry.executeTool(
                        toolCall.name(),
                        toolCall.arguments(),
                        context
                );

                // Merge any context variable updates
                context.merge(result.contextVariables());

                // Add tool result to history
                history.add(ChatMessage.toolResult(toolCall.id(), result.value()));

                // Handle agent handoff
                if (result.handoffAgent() != null) {
                    log.info("Handoff from '{}' to '{}'", activeAgent.name(), result.handoffAgent().name());
                    activeAgent = result.handoffAgent();
                }
            }
        }

        if (turns >= maxTurns) {
            log.warn("Agent '{}' reached max turns ({})", activeAgent.name(), maxTurns);
        }

        return new AgentResponse(history, activeAgent, context.toMap());
    }

    /**
     * Converts our ChatMessage list to Spring AI Message list.
     */
    private List<Message> toSpringMessages(String systemInstructions, List<ChatMessage> messages) {
        List<Message> springMessages = new ArrayList<>();
        springMessages.add(new SystemMessage(systemInstructions));

        for (ChatMessage msg : messages) {
            switch (msg.role()) {
                case USER -> springMessages.add(new UserMessage(msg.content()));
                case ASSISTANT -> springMessages.add(new AssistantMessage(msg.content()));
                case SYSTEM -> springMessages.add(new SystemMessage(msg.content()));
                case TOOL -> {
                    // Tool results are handled by Spring AI's internal mechanism
                    // We include them as user context for the next turn
                    springMessages.add(new UserMessage("[Tool result] " + msg.content()));
                }
            }
        }

        return springMessages;
    }
}
