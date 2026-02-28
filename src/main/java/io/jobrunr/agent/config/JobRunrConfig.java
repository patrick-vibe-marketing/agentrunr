package io.jobrunr.agent.config;

import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JobRunr configuration. Uses in-memory storage for the POC.
 * In production, configure a database-backed StorageProvider.
 */
@Configuration
public class JobRunrConfig {

    @Bean
    @ConditionalOnMissingBean(StorageProvider.class)
    public StorageProvider storageProvider() {
        return new InMemoryStorageProvider();
    }
}
