package com.devtrack.app.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();
    private final AiTools aiTools;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AiAssistantService(AiTools aiTools,
                              @Value("${opencode.zen.base-url}") String baseUrl,
                              @Value("${opencode.zen.api-key}") String apiKey,
                              @Value("${opencode.zen.model}") String model) {
        this.aiTools = aiTools;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String chat(String userMessage) {
        String systemPrompt = buildSystemPrompt();

        log.info("Sending to AI: {}", userMessage);
        String reply = callAi(systemPrompt + "\n\nUser: " + userMessage);
        log.info("AI raw reply: {}", reply);

        if (reply == null || reply.isBlank()) return "Sorry, I couldn't process that.";

        reply = reply.trim();

        if (reply.contains("```")) {
            reply = reply.replaceAll("(?s)```\\w*\\s*", "").replaceAll("```", "").trim();
        }

        String jsonPayload = extractJson(reply);

        if (jsonPayload != null) {
            try {
                Map<String, Object> map = json.readValue(jsonPayload, Map.class);
                String action = (String) map.get("action");
                Map<String, Object> params = (Map<String, Object>) map.get("params");
                if (action != null && params != null) {
                    log.info("Executing action: {} with params: {}", action, params);
                    return aiTools.execute(action, params);
                }
            } catch (Exception e) {
                log.error("Failed to parse action JSON: {}", jsonPayload, e);
            }
        }
        return reply;
    }

    private String buildSystemPrompt() {
        String actionList = aiTools.getActionDescriptions().stream()
            .collect(Collectors.joining("\n\n"));

        return """
            You are DevTrack's project management assistant. You execute commands by returning JSON actions.

            CRITICAL RULES:
            1. When the user asks to DO something — respond with ONLY the JSON object. No explanation, no greeting, no extra text.
            2. Only respond conversationally for greetings ("hi", "hello", "thanks") or questions that are NOT actions.
            3. You can use EITHER projectId OR projectName to identify projects. Same for features — use featureId OR featureTitle. Use names when the user provides them.

            AVAILABLE ACTIONS:

            """ + actionList + """

            Project statuses: IDEA, IN_PROGRESS, PAUSED, DONE, ARCHIVED
            Feature statuses: PLANNED, IN_PROGRESS, DONE

            For update actions, only include params the user wants to change. Omit the rest.
            If user does not specify status, use IN_PROGRESS for projects, PLANNED for features.

            NEVER respond with conversational text when the user is asking you to DO something.
            NEVER add explanations before or after the JSON. ONLY the raw JSON object.
            """;
    }

    private String callAi(String prompt) {
        try {
            String url = baseUrl + "/chat/completions";

            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<String> res = rest.postForEntity(url, req, String.class);
            JsonNode root = json.readTree(res.getBody());

            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            return "Unexpected API response: " + res.getBody();
        } catch (Exception e) {
            log.error("AI API call failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
