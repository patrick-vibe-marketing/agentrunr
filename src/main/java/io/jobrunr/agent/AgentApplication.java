package io.jobrunr.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JobRunr Agent — AI Agent Runtime powered by Spring AI and JobRunr.
 * Inspired by OpenAI Swarm's lightweight agent orchestration pattern.
 */
@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
