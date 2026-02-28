package io.jobrunr.agent.channel;

import io.jobrunr.agent.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRunner agentRunner;

    @MockBean
    private AgentConfigurer agentConfigurer;

    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("jobrunr-agent"));
    }

    @Test
    void shouldHandleChatRequest() throws Exception {
        var agent = new Agent("Assistant", "You are helpful.");
        when(agentConfigurer.getDefaultAgent()).thenReturn(agent);

        var response = new AgentResponse(
                List.of(
                        ChatMessage.user("Hello"),
                        ChatMessage.assistant("Hi there!", "Assistant")
                ),
                agent,
                Map.of()
        );
        when(agentRunner.run(any(Agent.class), anyList(), any(AgentContext.class), anyInt()))
                .thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "messages": [{"role": "user", "content": "Hello"}],
                                    "contextVariables": {},
                                    "maxTurns": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hi there!"))
                .andExpect(jsonPath("$.agent").value("Assistant"));
    }
}
