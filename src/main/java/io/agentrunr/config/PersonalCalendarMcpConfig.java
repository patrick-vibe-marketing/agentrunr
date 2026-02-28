package io.agentrunr.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Programmatic MCP client for Google Calendar (n8n MCP gateway).
 *
 * <p>We do this manually (instead of Spring AI auto-config) because the n8n gateway
 * requires a custom header for both the SSE and message endpoints.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "mcp.personal-calendar", name = "enabled", havingValue = "true")
public class PersonalCalendarMcpConfig {

    private static final Logger log = LoggerFactory.getLogger(PersonalCalendarMcpConfig.class);

    @Bean(destroyMethod = "close")
    public McpSyncClient personalCalendarMcpClient(PersonalCalendarMcpProperties props) {
        if (!StringUtils.hasText(props.url())) {
            throw new IllegalStateException("mcp.personal-calendar.url must be set when personal calendar MCP is enabled");
        }
        if (!StringUtils.hasText(props.password())) {
            throw new IllegalStateException("mcp.personal-calendar.password must be set when personal calendar MCP is enabled");
        }

        URI uri = URI.create(props.url());
        // Expect URLs like https://host/path/to/sse
        String fullPath = uri.getPath() == null ? "" : uri.getPath();
        if (!fullPath.endsWith("/sse")) {
            throw new IllegalStateException("mcp.personal-calendar.url must end with /sse");
        }

        // URI.resolve("/sse") resolves to root, so we need baseUri ending with "/"
        // and sseEndpoint as relative "sse" for proper URI resolution.
        String basePath = fullPath.substring(0, fullPath.length() - "sse".length());
        String baseUri = uri.getScheme() + "://" + uri.getAuthority() + basePath;

        // Use 4-arg constructor directly to ensure requestBuilder with Password header
        // is passed to both FlowSseClient (SSE subscribe) and sendMessage.
        var requestBuilder = java.net.http.HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("Password", props.password());

        var transport = new HttpClientSseClientTransport(
                java.net.http.HttpClient.newBuilder(),
                requestBuilder,
                baseUri,
                "sse",
                new com.fasterxml.jackson.databind.ObjectMapper());

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(java.time.Duration.ofSeconds(30))
                .initializationTimeout(java.time.Duration.ofSeconds(30))
                .build();
        client.initialize();

        try {
            int tools = client.listTools().tools() == null ? 0 : client.listTools().tools().size();
            log.info("Personal-calendar MCP initialized (tools: {})", tools);
        }
        catch (Exception e) {
            log.warn("Personal-calendar MCP initialized but tool listing failed: {}", e.toString());
        }

        return client;
    }

    @Bean
    public ToolCallbackProvider personalCalendarToolCallbackProvider(McpSyncClient personalCalendarMcpClient) {
        return new SyncMcpToolCallbackProvider(personalCalendarMcpClient);
    }

    @Bean
    public PersonalCalendarMcpProperties personalCalendarMcpProperties(
            org.springframework.core.env.Environment env
    ) {
        return new PersonalCalendarMcpProperties(
                env.getProperty("mcp.personal-calendar.url"),
                env.getProperty("mcp.personal-calendar.password")
        );
    }

    public record PersonalCalendarMcpProperties(String url, String password) {
    }
}
