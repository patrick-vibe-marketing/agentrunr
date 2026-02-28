package io.agentrunr.cron;

import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages scheduled agent tasks. Wraps JobRunr's scheduling API to provide
 * cron, interval, and one-shot job support for agent messages.
 */
@Service
public class CronService {

    private static final Logger log = LoggerFactory.getLogger(CronService.class);
    private static final String JOB_PREFIX = "agent-cron-";

    private final JobScheduler jobScheduler;
    private final CronJob cronJob;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public CronService(JobScheduler jobScheduler, CronJob cronJob) {
        this.jobScheduler = jobScheduler;
        this.cronJob = cronJob;
    }

    /**
     * Adds a recurring job with a cron expression.
     */
    public ScheduledTask addJob(String name, String message, String cronExpression) {
        String id = generateId();
        String jobId = JOB_PREFIX + id;

        jobScheduler.scheduleRecurrently(jobId, cronExpression, () -> cronJob.execute(name, message));

        var task = ScheduledTask.cron(id, name, message, cronExpression);
        tasks.put(id, task);
        log.info("Added cron task '{}' ({}): {}", name, cronExpression, message);
        return task;
    }

    /**
     * Adds a recurring job with an interval in seconds.
     */
    public ScheduledTask addJob(String name, String message, int intervalSeconds) {
        String id = generateId();
        String jobId = JOB_PREFIX + id;

        // Convert to cron: for intervals < 60s use seconds-based scheduling
        if (intervalSeconds < 60) {
            // JobRunr recurring jobs use cron, minimum is 1 minute
            // For sub-minute, schedule at every minute as best effort
            String cron = "* * * * *";
            jobScheduler.scheduleRecurrently(jobId, cron, () -> cronJob.execute(name, message));
        } else {
            int minutes = intervalSeconds / 60;
            String cron = "*/%d * * * *".formatted(minutes);
            jobScheduler.scheduleRecurrently(jobId, cron, () -> cronJob.execute(name, message));
        }

        var task = ScheduledTask.interval(id, name, message, intervalSeconds);
        tasks.put(id, task);
        log.info("Added interval task '{}' (every {}s): {}", name, intervalSeconds, message);
        return task;
    }

    /**
     * Adds a one-shot job that runs at a specific time.
     */
    public ScheduledTask addOneShot(String name, String message, Instant executeAt) {
        String id = generateId();

        jobScheduler.schedule(executeAt, () -> cronJob.execute(name, message));

        var task = ScheduledTask.oneShot(id, name, message, executeAt);
        tasks.put(id, task);
        log.info("Added one-shot task '{}' at {}: {}", name, executeAt, message);
        return task;
    }

    /**
     * Removes a scheduled job.
     */
    public boolean removeJob(String taskId) {
        ScheduledTask task = tasks.remove(taskId);
        if (task == null) {
            log.warn("Task '{}' not found for removal", taskId);
            return false;
        }

        String jobId = JOB_PREFIX + taskId;
        try {
            jobScheduler.deleteRecurringJob(jobId);
        } catch (Exception e) {
            // One-shot jobs can't be deleted as recurring, which is fine
            log.debug("Could not delete recurring job '{}' (may be one-shot): {}", jobId, e.getMessage());
        }

        log.info("Removed task '{}' ({})", task.name(), taskId);
        return true;
    }

    /**
     * Returns all scheduled tasks.
     */
    public List<ScheduledTask> listJobs() {
        return List.copyOf(tasks.values());
    }

    /**
     * Returns a task by ID, or null if not found.
     */
    public ScheduledTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Triggers a scheduled task immediately (in addition to its schedule).
     */
    public void triggerNow(String taskId) {
        ScheduledTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        log.info("Triggering task '{}' immediately", task.name());
        cronJob.execute(task.name(), task.message());
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
