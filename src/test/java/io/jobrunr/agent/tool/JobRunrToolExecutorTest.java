package io.jobrunr.agent.tool;

import io.jobrunr.agent.core.AgentContext;
import io.jobrunr.agent.core.AgentResult;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JobRunrToolExecutorTest {

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private ToolExecutionService toolExecutionService;

    private JobRunrToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobRunrToolExecutor(jobScheduler, toolExecutionService);
    }

    @Test
    void shouldStoreAndRetrieveResults() {
        var result = AgentResult.of("Success!");
        executor.storeResult("job-1", result);

        assertTrue(executor.isComplete("job-1"));
        assertEquals("Success!", executor.getResult("job-1").value());
    }

    @Test
    void shouldReturnNullForIncompleteJob() {
        assertFalse(executor.isComplete("nonexistent"));
        assertNull(executor.getResult("nonexistent"));
    }

    @Test
    void shouldConsumeResult() {
        var result = AgentResult.of("Done");
        executor.storeResult("job-2", result);

        var consumed = executor.consumeResult("job-2");
        assertEquals("Done", consumed.value());
        assertFalse(executor.isComplete("job-2"));
    }

    @Test
    void shouldEnqueueToolCallAndReturnTrackingId() {
        var context = new AgentContext();
        String trackingId = executor.enqueueToolCall("get_time", "{}", context);

        assertNotNull(trackingId);
        assertFalse(trackingId.isBlank());
    }
}
