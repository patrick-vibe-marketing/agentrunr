package io.agentrunr.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the web-based setup flow.
 * Provides endpoints to check configuration status and save API keys.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);
    private final CredentialStore credentialStore;

    public SetupController(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    /**
     * Returns current setup status — which providers are configured.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "configured", credentialStore.isConfigured(),
                "providers", credentialStore.getProviderStatus()
        ));
    }

    /**
     * Save API keys from the web setup form.
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> keys) {
        try {
            if (keys.containsKey("openai")) {
                credentialStore.setApiKey("openai", keys.get("openai"));
            }
            if (keys.containsKey("anthropic")) {
                credentialStore.setApiKey("anthropic", keys.get("anthropic"));
            }
            credentialStore.save();

            log.info("API keys saved via web setup");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "providers", credentialStore.getProviderStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to save credentials", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
