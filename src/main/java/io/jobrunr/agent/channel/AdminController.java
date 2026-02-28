package io.jobrunr.agent.channel;

import io.jobrunr.agent.config.ModelRouter;
import io.jobrunr.agent.memory.FileMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminController(ModelRouter modelRouter, FileMemoryStore memoryStore) {
        this.modelRouter = modelRouter;
        this.memoryStore = memoryStore;
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

    record ProviderInfo(boolean available, String model) {}
}
