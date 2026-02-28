package io.jobrunr.agent.tool;

import io.jobrunr.agent.core.AgentContext;
import io.jobrunr.agent.core.AgentResult;
import io.jobrunr.agent.core.ToolRegistry;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes agent tool calls as JobRunr background jobs.
 *
 * <p>This is the key integration point: when an agent calls a tool, instead of
 * executing it synchronously, we can enqueue it as a JobRunr background job.
 * This gives us:</p>
 * <ul>
 *   <li>Automatic retries on failure</li>
 *   <li>Job scheduling and recurring execution</li>
 *   <li>Dashboard visibility into all tool executions</li>
 *   <li>Distributed execution across workers</li>
 * </ul>
 */
@Component
public class JobRunrToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobRunrToolExecutor.class);

    private final JobScheduler jobScheduler;
    private final ToolExecutionService toolExecutionService;

    /**
     * In-memory store for job results. In production, use a persistent store.
     */
    private final ConcurrentHashMap<String, AgentResult> jobResults = new ConcurrentHashMap<>();

    public JobRunrToolExecutor(JobScheduler jobScheduler, ToolExecutionService toolExecutionService) {
        this.jobScheduler = jobScheduler;
        this.toolExecutionService = toolExecutionService;
    }

    /**
     * Enqueues a tool call as a JobRunr background job.
     *
     * @param toolName  the tool to execute
     * @param arguments JSON arguments
     * @param context   the agent context
     * @return a job tracking ID
     */
    public String enqueueToolCall(String toolName, String arguments, AgentContext context) {
        String jobTrackingId = UUID.randomUUID().toString();
        Map<String, String> contextSnapshot = context.toMutableMap();

        log.info("Enqueueing tool '{}' as JobRunr job [{}]", toolName, jobTrackingId);

        jobScheduler.enqueue(() ->
                toolExecutionService.executeToolJob(jobTrackingId, toolName, arguments, contextSnapshot));

        return jobTrackingId;
    }

    /**
     * Stores a job result (called by ToolExecutionService after completion).
     */
    public void storeResult(String jobTrackingId, AgentResult result) {
        jobResults.put(jobTrackingId, result);
        log.debug("Stored result for job [{}]", jobTrackingId);
    }

    /**
     * Retrieves a job result, or null if not yet complete.
     */
    public AgentResult getResult(String jobTrackingId) {
        return jobResults.get(jobTrackingId);
    }

    /**
     * Checks if a job has completed.
     */
    public boolean isComplete(String jobTrackingId) {
        return jobResults.containsKey(jobTrackingId);
    }

    /**
     * Removes a result after it has been consumed.
     */
    public AgentResult consumeResult(String jobTrackingId) {
        return jobResults.remove(jobTrackingId);
    }
}
