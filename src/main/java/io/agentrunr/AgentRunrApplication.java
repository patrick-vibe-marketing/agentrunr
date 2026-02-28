package io.agentrunr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentRunr — AI Agent Runtime powered by Spring AI and JobRunr.
 * Inspired by OpenAI Swarm's lightweight agent orchestration pattern.
 */
@SpringBootApplication
public class AgentRunrApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentRunrApplication.class, args);
    }
}
