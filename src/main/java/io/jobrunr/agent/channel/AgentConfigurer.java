package io.jobrunr.agent.channel;

import io.jobrunr.agent.core.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configures the default agent from application properties.
 * Supports runtime updates via the settings API.
 */
@Component
public class AgentConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigurer.class);

    private volatile String agentName;
    private volatile String agentModel;
    private volatile String agentInstructions;
    private volatile int maxTurns;

    public AgentConfigurer(
            @Value("${agent.name:Assistant}") String agentName,
            @Value("${agent.model:gpt-4o}") String agentModel,
            @Value("${agent.instructions:You are a helpful assistant.}") String agentInstructions
    ) {
        this.agentName = agentName;
        this.agentModel = agentModel;
        this.agentInstructions = agentInstructions;
        this.maxTurns = 10;
    }

    /**
     * Returns the default agent configured via application properties or runtime settings.
     */
    public Agent getDefaultAgent() {
        return new Agent(agentName, agentModel, agentInstructions, List.of());
    }

    /**
     * Updates agent settings at runtime.
     */
    public void update(String name, String model, String instructions, int maxTurns) {
        if (name != null && !name.isBlank()) this.agentName = name;
        if (model != null && !model.isBlank()) this.agentModel = model;
        if (instructions != null && !instructions.isBlank()) this.agentInstructions = instructions;
        if (maxTurns > 0) this.maxTurns = maxTurns;
        log.info("Agent settings updated: name={}, model={}", agentName, agentModel);
    }

    public int getMaxTurns() {
        return maxTurns;
    }
}
