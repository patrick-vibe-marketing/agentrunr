package io.agentrunr.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configures Anthropic ChatModel using Claude Code OAuth tokens.
 *
 * <p>When {@code agent.claude-code-oauth.enabled=true} and Claude Code is
 * authenticated on the machine, this creates an Anthropic ChatModel using
 * the OAuth access token from the system keychain — no API key needed.</p>
 *
 * <p>This enables using your Claude Code subscription (Pro/Max/Team) for
 * inference in AgentRunr without a separate Anthropic API key.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "agent.claude-code-oauth", name = "enabled", havingValue = "true")
public class ClaudeCodeAnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeAnthropicConfig.class);

    @Bean
    @Primary
    public AnthropicChatModel claudeCodeAnthropicChatModel(
            ClaudeCodeOAuthProvider oauthProvider,
            @Autowired(required = false) org.springframework.ai.model.tool.ToolCallingManager toolCallingManager
    ) {
        String token = oauthProvider.getAccessToken()
                .orElseThrow(() -> new IllegalStateException(
                        "Claude Code OAuth enabled but no valid token found. " +
                        "Run 'claude auth login' to authenticate Claude Code first."));

        String model = System.getenv().getOrDefault("CLAUDE_CODE_MODEL", "claude-sonnet-4-20250514");

        var api = AnthropicApi.builder()
                .apiKey(token)
                .build();

        var options = AnthropicChatOptions.builder()
                .model(model)
                .build();

        var builder = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options);

        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }

        log.info("Anthropic ChatModel configured via Claude Code OAuth (model: {})", model);
        return builder.build();
    }
}
