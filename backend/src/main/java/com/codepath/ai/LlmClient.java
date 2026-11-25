package com.codepath.ai;

/**
 * Port to an external large language model. The only implementation talks to the provider's HTTP API;
 * tests replace it with a mock so no API key or network is needed.
 */
public interface LlmClient {

    /**
     * Sends one system prompt and one user prompt and returns the model's plain-text reply.
     *
     * @throws com.codepath.common.ServiceUnavailableException when the model is not configured, unreachable,
     *                                                         rate limited or returns nothing usable
     */
    String complete(String systemPrompt, String userPrompt);
}
