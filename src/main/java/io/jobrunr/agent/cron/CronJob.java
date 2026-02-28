package io.jobrunr.agent.cron;

import io.jobrunr.agent.channel.AgentConfigurer;
import io.jobrunr.agent.channel.ChannelRegistry;
import io.jobrunr.agent.core.*;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Executes scheduled messages through the agent loop.
 * Each scheduled task invokes this job, which sends the message to the agent
 * and routes the result to the last active channel.
 */
@Component
public class CronJob {

    private static final Logger log = LoggerFactory.getLogger(CronJob.class);

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;
    private final ChannelRegistry channelRegistry;

    public CronJob(AgentRunner agentRunner, AgentConfigurer agentConfigurer,
                   ChannelRegistry channelRegistry) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
        this.channelRegistry = channelRegistry;
    }

    @Job(name = "Scheduled task: %0")
    public void execute(String taskName, String message) {
        log.info("Executing scheduled task '{}': {}", taskName, message);

        try {
            Agent agent = agentConfigurer.getDefaultAgent();
            AgentResponse response = agentRunner.run(agent, List.of(ChatMessage.user(message)));
            String result = response.lastMessage();

            if (!result.isBlank()) {
                channelRegistry.sendToLastActive("[Scheduled: %s] %s".formatted(taskName, result));
            }
            log.info("Scheduled task '{}' completed", taskName);
        } catch (Exception e) {
            log.error("Error executing scheduled task '{}'", taskName, e);
            channelRegistry.sendToLastActive("[Scheduled Error: %s] %s".formatted(taskName, e.getMessage()));
        }
    }
}
