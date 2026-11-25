package com.codepath.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Google Gemini {@code generateContent} client. Runs only inside the Spring Boot backend; the API key is
 * sent in the {@code x-goog-api-key} header and is never logged.
 */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    static final String API_KEY_HEADER = "x-goog-api-key";

    private final RestClient restClient;
    private final AiProperties properties;

    public GeminiLlmClient(@Qualifier("aiRestClient") RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException(
                    "The AI assistant is not configured on this server (CODEPATH_AI_API_KEY is empty)");
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", properties.temperature(),
                        "maxOutputTokens", properties.maxOutputTokens()));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw unavailable(ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("AI provider unreachable: {}", ex.getMessage());
            throw new ServiceUnavailableException("The AI assistant is unreachable right now", ex);
        }

        String text = extractText(response);
        if (text.isBlank()) {
            log.warn("AI provider returned no text (finishReason={})", finishReason(response));
            throw new ServiceUnavailableException("The AI assistant returned an empty answer; please try again");
        }
        return text.strip();
    }

    /** Concatenates the text parts of the first candidate; empty when the reply has none. */
    static String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode value = part.path("text");
            if (value.isTextual()) {
                text.append(value.asText());
            }
        }
        return text.toString();
    }

    private static String finishReason(JsonNode response) {
        return response == null ? "no-response" : response.path("candidates").path(0).path("finishReason").asText("none");
    }

    private static ServiceUnavailableException unavailable(HttpStatusCode status, RestClientResponseException ex) {
        int code = status.value();
        log.error("AI provider returned HTTP {}", code);
        if (code == 429) {
            return new ServiceUnavailableException("The AI assistant is busy (rate limit reached); try again shortly", ex);
        }
        if (code == 400 || code == 401 || code == 403) {
            return new ServiceUnavailableException(
                    "The AI assistant rejected the request; the server's API key or model setting may be wrong", ex);
        }
        return new ServiceUnavailableException("The AI assistant is unavailable right now", ex);
    }
}
