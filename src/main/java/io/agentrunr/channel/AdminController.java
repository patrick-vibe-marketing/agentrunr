package io.agentrunr.channel;

import io.agentrunr.config.ClaudeCodeOAuthProvider;
import io.agentrunr.config.ModelRouter;
import io.agentrunr.memory.FileMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public AdminController(
            ModelRouter modelRouter,
            FileMemoryStore memoryStore,
            @Autowired(required = false) ClaudeCodeOAuthProvider claudeCodeOAuthProvider
    ) {
        this.modelRouter = modelRouter;
        this.memoryStore = memoryStore;
        this.claudeCodeOAuthProvider = claudeCodeOAuthProvider;
    }

    /**
     * Returns the status of configured AI providers.
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, ProviderInfo>> getProviders() {
        Map<String, ProviderInfo> providers = new HashMap<>();

        // Check each provider by trying to resolve a known model
        providers.put("openai", checkProvider("openai", "gpt-4o"));
        providers.put("ollama", checkProvider("ollama", "llama3.2"));
        providers.put("anthropic", checkProvider("anthropic", "claude-sonnet-4-20250514"));

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

    record ProviderInfo(boolean available, String model) {}
}
