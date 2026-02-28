package io.agentrunr.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.util.Arrays;

/**
 * Interactive CLI setup that runs on first start or with --setup flag.
 * Prompts for API keys and stores them encrypted.
 */
@Component
public class SetupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SetupRunner.class);
    private final CredentialStore credentialStore;
    private final Environment environment;

    public SetupRunner(CredentialStore credentialStore, Environment environment) {
        this.credentialStore = credentialStore;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean forceSetup = Arrays.asList(args).contains("--setup");

        if (!forceSetup && credentialStore.isConfigured()) {
            return;
        }

        Console console = System.console();
        if (console == null) {
            // Not running in a terminal (e.g., IDE, background) — skip CLI setup
            if (!credentialStore.isConfigured()) {
                log.info("No AI providers configured. Visit http://localhost:{}/setup to configure.",
                        environment.getProperty("server.port", "8090"));
            }
            return;
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  Welcome to AgentRunr! 🤖                    ║");
        System.out.println("║  Let's set up your AI provider.              ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // OpenAI
        String openaiKey = console.readLine("Paste your OpenAI API key (or press Enter to skip): ");
        if (openaiKey != null && !openaiKey.isBlank()) {
            credentialStore.setApiKey("openai", openaiKey.trim());
            System.out.println("  ✅ OpenAI key saved");
        } else {
            System.out.println("  ⏭️  Skipped OpenAI");
        }

        // Anthropic
        String anthropicKey = console.readLine("Paste your Anthropic API key (or press Enter to skip): ");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            credentialStore.setApiKey("anthropic", anthropicKey.trim());
            System.out.println("  ✅ Anthropic key saved");
        } else {
            System.out.println("  ⏭️  Skipped Anthropic");
        }

        if (credentialStore.isConfigured()) {
            credentialStore.save();
            System.out.println();
            System.out.println("🔐 Credentials encrypted and saved to ~/.agentrunr/credentials.enc");
            System.out.println("🚀 Starting AgentRunr...");
        } else {
            System.out.println();
            System.out.println("⚠️  No API keys configured. You can set them later:");
            System.out.println("   • Run with --setup flag");
            System.out.println("   • Visit http://localhost:" +
                    environment.getProperty("server.port", "8090") + "/setup");
            System.out.println("   • Set OPENAI_API_KEY or ANTHROPIC_API_KEY env vars");
        }
        System.out.println();
    }
}
