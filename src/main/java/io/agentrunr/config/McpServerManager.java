package io.agentrunr.config;

import io.agentrunr.core.ToolRegistry;
import io.agentrunr.setup.CredentialStore;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all MCP servers — both Spring bean-based (e.g. PersonalCalendar)
 * and dynamically configured via the settings UI (stored in CredentialStore).
 *
 * <p>Provides status reporting for the admin API and handles lifecycle
 * of dynamically created MCP clients.</p>
 */
@Component
public class McpServerManager implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(McpServerManager.class);

    private final CredentialStore credentialStore;
    private final ToolRegistry toolRegistry;
    private final Map<String, ManagedServer> servers = new ConcurrentHashMap<>();

    private ApplicationContext applicationContext;

    public McpServerManager(CredentialStore credentialStore, ToolRegistry toolRegistry) {
        this.credentialStore = credentialStore;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void init() {
        discoverExistingBeans();
        loadDynamicServers();
    }

    @PreDestroy
    void cleanup() {
        servers.values().stream()
                .filter(s -> s.dynamic && s.client != null)
                .forEach(s -> {
                    try {
                        s.client.close();
                    } catch (Exception e) {
                        log.warn("Error closing MCP client '{}': {}", s.name, e.getMessage());
                    }
                });
    }

    /**
     * Returns status of all tracked MCP servers.
     */
    public List<McpServerStatus> getStatuses() {
        List<McpServerStatus> statuses = new ArrayList<>();
        for (ManagedServer server : servers.values()) {
            statuses.add(checkStatus(server));
        }
        return statuses;
    }

    /**
     * Returns the list of dynamic server names stored in CredentialStore.
     */
    public List<String> getDynamicServerNames() {
        String names = credentialStore.getApiKey("mcp_servers");
        if (names == null || names.isBlank()) return List.of();
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Saves a dynamic MCP server config to CredentialStore.
     */
    public void saveDynamicServer(String name, String url, String authHeader, String authValue) throws Exception {
        credentialStore.setApiKey("mcp_" + name + "_url", url);
        if (authHeader != null && !authHeader.isBlank()) {
            credentialStore.setApiKey("mcp_" + name + "_auth_header", authHeader);
        }
        if (authValue != null && !authValue.isBlank()) {
            credentialStore.setApiKey("mcp_" + name + "_auth_value", authValue);
        }

        // Update server name list
        List<String> existing = new ArrayList<>(getDynamicServerNames());
        if (!existing.contains(name)) {
            existing.add(name);
            credentialStore.setApiKey("mcp_servers", String.join(",", existing));
        }

        credentialStore.save();
        log.info("Saved dynamic MCP server config: {}", name);
    }

    /**
     * Removes a dynamic MCP server config from CredentialStore.
     */
    public void removeDynamicServer(String name) throws Exception {
        credentialStore.setApiKey("mcp_" + name + "_url", null);
        credentialStore.setApiKey("mcp_" + name + "_auth_header", null);
        credentialStore.setApiKey("mcp_" + name + "_auth_value", null);

        List<String> existing = new ArrayList<>(getDynamicServerNames());
        existing.remove(name);
        if (existing.isEmpty()) {
            credentialStore.setApiKey("mcp_servers", null);
        } else {
            credentialStore.setApiKey("mcp_servers", String.join(",", existing));
        }

        credentialStore.save();
        servers.remove(name);
        log.info("Removed dynamic MCP server config: {}", name);
    }

    private void discoverExistingBeans() {
        Map<String, McpSyncClient> beans = applicationContext.getBeansOfType(McpSyncClient.class);
        for (var entry : beans.entrySet()) {
            String beanName = entry.getKey();
            // Convert bean name to readable label: personalCalendarMcpClient -> Personal Calendar
            String label = beanName.replace("McpClient", "").replace("MCP", "")
                    .replaceAll("([a-z])([A-Z])", "$1 $2");
            label = label.substring(0, 1).toUpperCase() + label.substring(1);

            var server = new ManagedServer(label, entry.getValue(), null, false);
            servers.put(label, server);
            log.info("Discovered existing MCP server bean: {} -> {}", beanName, label);
        }
    }

    private void loadDynamicServers() {
        for (String name : getDynamicServerNames()) {
            String url = credentialStore.getApiKey("mcp_" + name + "_url");
            if (url == null || url.isBlank()) {
                log.warn("Dynamic MCP server '{}' has no URL configured, skipping", name);
                continue;
            }

            String authHeader = credentialStore.getApiKey("mcp_" + name + "_auth_header");
            String authValue = credentialStore.getApiKey("mcp_" + name + "_auth_value");

            try {
                McpSyncClient client = createClient(url, authHeader, authValue);
                registerTools(name, client);
                servers.put(name, new ManagedServer(name, client, url, true));
                log.info("Loaded dynamic MCP server: {}", name);
            } catch (Exception e) {
                log.error("Failed to initialize dynamic MCP server '{}': {}", name, e.getMessage());
                servers.put(name, new ManagedServer(name, null, url, true));
            }
        }
    }

    private McpSyncClient createClient(String url, String authHeader, String authValue) {
        URI uri = URI.create(url);
        String fullPath = uri.getPath() == null ? "" : uri.getPath();

        // Determine base URI and SSE endpoint
        String baseUri;
        String sseEndpoint;
        if (fullPath.endsWith("/sse")) {
            String basePath = fullPath.substring(0, fullPath.length() - "sse".length());
            baseUri = uri.getScheme() + "://" + uri.getAuthority() + basePath;
            sseEndpoint = "sse";
        } else {
            baseUri = uri.getScheme() + "://" + uri.getAuthority() + fullPath;
            if (!baseUri.endsWith("/")) baseUri += "/";
            sseEndpoint = "sse";
        }

        var requestBuilder = java.net.http.HttpRequest.newBuilder()
                .header("Content-Type", "application/json");

        if (authHeader != null && !authHeader.isBlank() && authValue != null && !authValue.isBlank()) {
            requestBuilder.header(authHeader, authValue);
        }

        var transport = new HttpClientSseClientTransport(
                java.net.http.HttpClient.newBuilder(),
                requestBuilder,
                baseUri,
                sseEndpoint,
                new com.fasterxml.jackson.databind.ObjectMapper());

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        return client;
    }

    private void registerTools(String serverName, McpSyncClient client) {
        try {
            var provider = new SyncMcpToolCallbackProvider(client);
            for (ToolCallback callback : provider.getToolCallbacks()) {
                String toolName = callback.getToolDefinition().name();
                toolRegistry.registerFunctionCallback(toolName, callback);
                log.debug("Registered dynamic MCP tool from '{}': {}", serverName, toolName);
            }
        } catch (Exception e) {
            log.warn("Failed to register tools for MCP server '{}': {}", serverName, e.getMessage());
        }
    }

    private McpServerStatus checkStatus(ManagedServer server) {
        if (server.client == null) {
            return new McpServerStatus(server.name, false, 0, server.url, server.dynamic);
        }
        try {
            var result = server.client.listTools();
            int toolCount = result.tools() == null ? 0 : result.tools().size();
            return new McpServerStatus(server.name, true, toolCount, server.url, server.dynamic);
        } catch (Exception e) {
            log.debug("MCP server '{}' status check failed: {}", server.name, e.getMessage());
            return new McpServerStatus(server.name, false, 0, server.url, server.dynamic);
        }
    }

    private static class ManagedServer {
        final String name;
        final McpSyncClient client;
        final String url;
        final boolean dynamic;

        ManagedServer(String name, McpSyncClient client, String url, boolean dynamic) {
            this.name = name;
            this.client = client;
            this.url = url;
            this.dynamic = dynamic;
        }
    }

    public record McpServerStatus(String name, boolean connected, int toolCount, String url, boolean dynamic) {}
}
