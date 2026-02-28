package io.agentrunr.channel;

import io.agentrunr.config.ClaudeCodeOAuthProvider;
import io.agentrunr.config.McpServerManager;
import io.agentrunr.config.ModelRouter;
import io.agentrunr.memory.FileMemoryStore;
import io.agentrunr.setup.CredentialStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin API endpoints for the web UI (provider status, sessions, etc.)
 */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final ModelRouter modelRouter;
    private final FileMemoryStore memoryStore;
    private final ClaudeCodeOAuthProvider claudeCodeOAuthProvider;
    private final CredentialStore credentialStore;
    private final McpServerManager mcpServerManager;

    public AdminController(
            ModelRouter modelRouter,
            FileMemoryStore memoryStore,
            CredentialStore credentialStore,
            McpServerManager mcpServerManager,
            @Autowired(required = false) ClaudeCodeOAuthProvider claudeCodeOAuthProvider
    ) {
        this.modelRouter = modelRouter;
        this.memoryStore = memoryStore;
        this.credentialStore = credentialStore;
        this.mcpServerManager = mcpServerManager;
        this.claudeCodeOAuthProvider = claudeCodeOAuthProvider;
    }

    /**
     * Returns the status of configured AI providers.
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, ProviderInfo>> getProviders() {
        Map<String, ProviderInfo> providers = new HashMap<>();

        // Check each provider by trying to resolve a known model
        providers.put("openai", checkProvider("openai", "gpt-4.1"));
        providers.put("ollama", checkProvider("ollama", "llama3.2"));
        providers.put("anthropic", checkProvider("anthropic", "claude-sonnet-4-20250514"));

        // Claude Code OAuth status
        if (claudeCodeOAuthProvider != null) {
            var tokenInfo = claudeCodeOAuthProvider.getTokenInfo();
            if (tokenInfo.isPresent() && tokenInfo.get().valid()) {
                var info = tokenInfo.get();
                String label = info.subscriptionType() != null
                        ? "OAuth (" + info.subscriptionType() + ")"
                        : "OAuth";
                providers.put("claudeCodeOauth", new ProviderInfo(true, label));
            } else {
                providers.put("claudeCodeOauth", new ProviderInfo(false, null));
            }
        } else {
            providers.put("claudeCodeOauth", new ProviderInfo(false, null));
        }

        return ResponseEntity.ok(providers);
    }

    /**
     * Lists all memory sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions() {
        return ResponseEntity.ok(memoryStore.listSessions());
    }

    private ProviderInfo checkProvider(String provider, String defaultModel) {
        try {
            var resolved = modelRouter.resolve(provider + ":" + defaultModel);
            if (resolved.chatModel() != null) {
                return new ProviderInfo(true, defaultModel);
            }
        } catch (Exception e) {
            // Provider not available
        }
        return new ProviderInfo(false, null);
    }

    /**
     * Returns Claude Code OAuth token status.
     */
    @GetMapping("/claude-code-oauth")
    public ResponseEntity<Map<String, Object>> getClaudeCodeOAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        if (claudeCodeOAuthProvider == null) {
            status.put("available", false);
            status.put("reason", "ClaudeCodeOAuthProvider not configured");
            return ResponseEntity.ok(status);
        }

        var tokenInfo = claudeCodeOAuthProvider.getTokenInfo();
        if (tokenInfo.isEmpty()) {
            status.put("available", false);
            status.put("reason", "No Claude Code credentials found. Run 'claude auth login'.");
            return ResponseEntity.ok(status);
        }

        var info = tokenInfo.get();
        status.put("available", true);
        status.put("valid", info.valid());
        status.put("subscriptionType", info.subscriptionType());
        status.put("rateLimitTier", info.rateLimitTier());
        status.put("scopes", info.scopes());
        status.put("expiresAt", info.expiresAt() != null ? info.expiresAt().toString() : null);
        return ResponseEntity.ok(status);
    }

    /**
     * Refreshes the cached Claude Code OAuth token.
     */
    @PostMapping("/claude-code-oauth/refresh")
    public ResponseEntity<Map<String, Object>> refreshClaudeCodeOAuth() {
        if (claudeCodeOAuthProvider == null) {
            return ResponseEntity.ok(Map.of("success", false, "reason", "Not configured"));
        }
        claudeCodeOAuthProvider.invalidateCache();
        var token = claudeCodeOAuthProvider.getAccessToken();
        return ResponseEntity.ok(Map.of("success", token.isPresent()));
    }

    /**
     * Returns current Telegram bot settings (token masked).
     */
    @GetMapping("/telegram/settings")
    public ResponseEntity<Map<String, Object>> getTelegramSettings() {
        Map<String, Object> result = new HashMap<>();
        String token = credentialStore.getApiKey("telegram_token");
        String allowedUsers = credentialStore.getApiKey("telegram_allowed_users");

        result.put("configured", token != null && !token.isBlank());
        result.put("tokenMasked", maskToken(token));
        result.put("allowedUsers", allowedUsers != null ? allowedUsers : "");
        return ResponseEntity.ok(result);
    }

    /**
     * Saves Telegram bot settings to the encrypted credential store.
     */
    @PutMapping("/telegram/settings")
    public ResponseEntity<Map<String, Object>> saveTelegramSettings(@RequestBody Map<String, String> body) {
        try {
            String token = body.get("token");
            String allowedUsers = body.get("allowedUsers");

            if (token != null && !token.isBlank()) {
                credentialStore.setApiKey("telegram_token", token);
            }
            if (allowedUsers != null) {
                credentialStore.setApiKey("telegram_allowed_users", allowedUsers.trim());
            }
            credentialStore.save();

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Returns status of all configured MCP servers.
     */
    @GetMapping("/mcp/servers")
    public ResponseEntity<List<McpServerManager.McpServerStatus>> getMcpServers() {
        return ResponseEntity.ok(mcpServerManager.getStatuses());
    }

    /**
     * Saves a dynamic MCP server configuration.
     */
    @PostMapping("/mcp/servers")
    public ResponseEntity<Map<String, Object>> addMcpServer(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String url = body.get("url");
            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Name and URL are required"));
            }
            mcpServerManager.saveDynamicServer(
                    name.trim(), url.trim(),
                    body.get("authHeader"), body.get("authValue"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Removes a dynamic MCP server configuration.
     */
    @DeleteMapping("/mcp/servers/{name}")
    public ResponseEntity<Map<String, Object>> removeMcpServer(@PathVariable String name) {
        try {
            mcpServerManager.removeDynamicServer(name);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    record ProviderInfo(boolean available, String model) {}
}
