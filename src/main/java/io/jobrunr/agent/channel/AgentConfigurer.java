package io.jobrunr.agent.channel;

import io.jobrunr.agent.core.Agent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configures the default agent from application properties.
 */
@Component
public class AgentConfigurer {

    @Value("${agent.name:Assistant}")
    private String agentName;

    @Value("${agent.model:gpt-4o}")
    private String agentModel;

    @Value("${agent.instructions:You are a helpful assistant.}")
    private String agentInstructions;

    /**
     * Returns the default agent configured via application properties.
     */
    public Agent getDefaultAgent() {
        return new Agent(agentName, agentModel, agentInstructions, List.of());
    }
}
