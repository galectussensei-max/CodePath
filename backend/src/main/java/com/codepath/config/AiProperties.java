package com.codepath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code codepath.ai}. {@code base-url}, {@code api-key} and {@code model} already existed in
 * {@code application.yml}; the rest have defaults. The API key lives only in the backend and is never logged
 * or returned to the frontend.
 *
 * @param baseUrl         LLM API root (Gemini by default)
 * @param apiKey          secret key; blank means the AI assistant is not configured (requests return HTTP 503)
 * @param model           model name
 * @param connectTimeoutMs HTTP connect timeout
 * @param readTimeoutMs   HTTP read timeout (LLM calls are slow)
 * @param maxOutputTokens upper bound on the reply length
 * @param temperature     sampling temperature; low keeps hints focused
 * @param maxSourceChars  longest learner source code sent to the LLM; longer code is cut
 */
@ConfigurationProperties(prefix = "codepath.ai")
public record AiProperties(
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("gemini-2.0-flash") String model,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("30000") int readTimeoutMs,
        @DefaultValue("1024") int maxOutputTokens,
        @DefaultValue("0.3") double temperature,
        @DefaultValue("8000") int maxSourceChars) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
