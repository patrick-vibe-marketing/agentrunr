package io.jobrunr.agent.cron;

import io.jobrunr.agent.channel.AgentConfigurer;
import io.jobrunr.agent.channel.ChannelRegistry;
import io.jobrunr.agent.core.AgentRunner;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CronServiceTest {

    private CronService cronService;
    private JobScheduler jobScheduler;

    @BeforeEach
    void setUp() {
        jobScheduler = mock(JobScheduler.class);
        var agentRunner = mock(AgentRunner.class);
        var agentConfigurer = mock(AgentConfigurer.class);
        var channelRegistry = new ChannelRegistry();
        var cronJob = new CronJob(agentRunner, agentConfigurer, channelRegistry);
        cronService = new CronService(jobScheduler, cronJob);
    }

    @Test
    void shouldAddCronJob() {
        var task = cronService.addJob("Report", "Generate daily report", "0 9 * * *");

        assertNotNull(task.id());
        assertEquals("Report", task.name());
        assertEquals("Generate daily report", task.message());
        assertEquals("0 9 * * *", task.cron());
    }

    @Test
    void shouldAddIntervalJob() {
        var task = cronService.addJob("Poller", "Check status", 300);

        assertNotNull(task.id());
        assertEquals("Poller", task.name());
        assertEquals(300, task.intervalSeconds());
    }

    @Test
    void shouldAddOneShotJob() {
        var executeAt = Instant.now().plusSeconds(3600);
        var task = cronService.addOneShot("Reminder", "Call dentist", executeAt);

        assertNotNull(task.id());
        assertEquals("Reminder", task.name());
        assertEquals(executeAt, task.executeAt());
    }

    @Test
    void shouldListAllJobs() {
        cronService.addJob("A", "msg-a", "0 * * * *");
        cronService.addJob("B", "msg-b", 60);
        cronService.addOneShot("C", "msg-c", Instant.now().plusSeconds(3600));

        var jobs = cronService.listJobs();
        assertEquals(3, jobs.size());
    }

    @Test
    void shouldRemoveJob() {
        var task = cronService.addJob("ToRemove", "msg", "0 * * * *");

        assertTrue(cronService.removeJob(task.id()));
        assertEquals(0, cronService.listJobs().size());
    }

    @Test
    void shouldReturnFalseWhenRemovingNonexistentJob() {
        assertFalse(cronService.removeJob("nonexistent"));
    }

    @Test
    void shouldGetTaskById() {
        var task = cronService.addJob("Find Me", "msg", "0 * * * *");

        var found = cronService.getTask(task.id());
        assertNotNull(found);
        assertEquals("Find Me", found.name());
    }

    @Test
    void shouldReturnNullForUnknownTaskId() {
        assertNull(cronService.getTask("nonexistent"));
    }

    @Test
    void shouldThrowWhenTriggeringNonexistentTask() {
        assertThrows(IllegalArgumentException.class, () -> cronService.triggerNow("nonexistent"));
    }
}
