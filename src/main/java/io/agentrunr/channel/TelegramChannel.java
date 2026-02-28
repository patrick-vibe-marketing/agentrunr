package io.agentrunr.channel;

import io.agentrunr.core.*;
import io.agentrunr.memory.FileMemoryStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telegram bot channel using long polling.
 *
 * <p>Connects the agent to a Telegram bot. Messages from allowed users
 * are forwarded to the agent, and responses are sent back.</p>
 *
 * <p>Configure in application.yml:</p>
 * <pre>
 * agent:
 *   telegram:
 *     enabled: true
 *     token: ${TELEGRAM_BOT_TOKEN}
 *     allowed-users: 123456789,987654321
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "agent.telegram.enabled", havingValue = "true")
public class TelegramChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannel.class);
    private static final String API_BASE = "https://api.telegram.org/bot";

    @Value("${agent.telegram.token}")
    private String botToken;

    @Value("${agent.telegram.allowed-users:}")
    private String allowedUsers;

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;
    private final FileMemoryStore memoryStore;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private long lastUpdateId = 0;

    public TelegramChannel(AgentRunner agentRunner, AgentConfigurer agentConfigurer,
                           FileMemoryStore memoryStore, ObjectMapper objectMapper) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
        this.memoryStore = memoryStore;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Telegram channel starting with long polling...");
        executor.scheduleWithFixedDelay(this::pollUpdates, 0, 1, TimeUnit.SECONDS);
    }

    private void pollUpdates() {
        try {
            String url = API_BASE + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";
            RestClient client = RestClient.create();
            String response = client.get().uri(url).retrieve().body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (!root.path("ok").asBoolean()) {
                log.warn("Telegram API returned not OK: {}", response);
                return;
            }

            JsonNode results = root.path("result");
            for (JsonNode update : results) {
                lastUpdateId = update.path("update_id").asLong();
                handleUpdate(update);
            }
        } catch (Exception e) {
            log.error("Error polling Telegram updates: {}", e.getMessage());
        }
    }

    private void handleUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;

        long chatId = message.path("chat").path("id").asLong();
        long userId = message.path("from").path("id").asLong();
        String text = message.path("text").asText("");
        String userName = message.path("from").path("first_name").asText("User");

        if (text.isBlank()) return;

        // Check allowed users
        if (!allowedUsers.isBlank()) {
            boolean allowed = false;
            for (String id : allowedUsers.split(",")) {
                if (id.trim().equals(String.valueOf(userId))) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                log.warn("Unauthorized Telegram user: {} ({})", userName, userId);
                sendMessage(chatId, "⛔ Unauthorized. Your user ID is not in the allowed list.");
                return;
            }
        }

        log.info("Telegram message from {} ({}): {}", userName, userId, text);

        // Store in memory
        String sessionId = "telegram-" + chatId;
        memoryStore.appendMessage(sessionId, "user", userName + ": " + text);

        // Load context
        var contextVars = memoryStore.loadContext(sessionId);
        contextVars.put("user_name", userName);
        contextVars.put("telegram_chat_id", String.valueOf(chatId));
        var context = new AgentContext(contextVars);

        // Run agent
        try {
            Agent agent = agentConfigurer.getDefaultAgent();
            AgentResponse agentResponse = agentRunner.run(agent, List.of(ChatMessage.user(text)), context, 10);

            String reply = agentResponse.lastMessage();
            if (reply.isBlank()) reply = "(No response)";

            sendMessage(chatId, reply);
            memoryStore.appendMessage(sessionId, "assistant", reply);
            memoryStore.saveContext(sessionId, agentResponse.contextVariables());

        } catch (Exception e) {
            log.error("Error processing Telegram message: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Error: " + e.getMessage());
        }
    }

    /**
     * Sends a message to a Telegram chat.
     */
    public void sendMessage(long chatId, String text) {
        try {
            // Telegram has a 4096 char limit per message
            List<String> chunks = splitMessage(text, 4000);
            RestClient client = RestClient.create();

            for (String chunk : chunks) {
                String url = API_BASE + botToken + "/sendMessage";
                String body = objectMapper.writeValueAsString(new TelegramSendMessage(chatId, chunk, "Markdown"));
                client.post().uri(url)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(String.class);
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private List<String> splitMessage(String text, int maxLen) {
        if (text.length() <= maxLen) return List.of(text);

        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            // Try to split on newline
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start) end = lastNewline;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    record TelegramSendMessage(long chat_id, String text, String parse_mode) {}
}
