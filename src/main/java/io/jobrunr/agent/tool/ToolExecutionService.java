package io.jobrunr.agent.tool;

import io.jobrunr.agent.core.AgentContext;
import io.jobrunr.agent.core.AgentResult;
import io.jobrunr.agent.core.ToolRegistry;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service that executes tool calls within JobRunr jobs.
 *
 * <p>This service is the bridge between JobRunr's job execution and the agent's
 * tool registry. Each method annotated with {@code @Job} becomes a trackable,
 * retryable unit of work in the JobRunr dashboard.</p>
 */
@Service
public class ToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);

    private final ToolRegistry toolRegistry;
    private final JobRunrToolExecutor toolExecutor;

    public ToolExecutionService(ToolRegistry toolRegistry, JobRunrToolExecutor toolExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Executes a tool call as a JobRunr background job.
     * Results are stored for later retrieval by the agent runner.
     *
     * @param jobTrackingId unique tracking ID for this execution
     * @param toolName      the tool to execute
     * @param arguments     JSON arguments string
     * @param contextVars   snapshot of context variables at enqueue time
     */
    @Job(name = "Agent Tool: %1 - %2")
    public void executeToolJob(String jobTrackingId, String toolName, String arguments, Map<String, String> contextVars) {
        log.info("Executing tool '{}' in JobRunr job [{}]", toolName, jobTrackingId);

        AgentContext context = new AgentContext(contextVars);
        AgentResult result;

        try {
            result = toolRegistry.executeTool(toolName, arguments, context);
        } catch (Exception e) {
            log.error("Tool '{}' failed in job [{}]: {}", toolName, jobTrackingId, e.getMessage(), e);
            result = AgentResult.of("Error executing tool '" + toolName + "': " + e.getMessage());
        }

        toolExecutor.storeResult(jobTrackingId, result);
        log.info("Tool '{}' completed in job [{}]", toolName, jobTrackingId);
    }
}
