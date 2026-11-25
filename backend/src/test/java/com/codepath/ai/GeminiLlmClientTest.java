package com.codepath.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.AiProperties;

class GeminiLlmClientTest {

    private static final String BASE = "http://ai.test";
    private static final String URL = BASE + "/v1beta/models/test-model:generateContent";

    private MockRestServiceServer server;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiLlmClient(builder.build(),
                new AiProperties(BASE, "secret-key", "test-model", 1000, 1000, 256, 0.2, 8000));
    }

    @Test
    void sendsSystemAndUserPromptsWithTheKeyHeaderAndReturnsTheText() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("SYSTEM"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("USER"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(256))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"},{\"text\":\"world\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("Hello world", client.complete("SYSTEM", "USER"));
        server.verify();
    }

    @Test
    void emptyCandidatesBecomeServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(ServiceUnavailableException.class, () -> client.complete("s", "u"));
    }

    @Test
    void rateLimitBecomesServiceUnavailableWithoutLeakingTheKey() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> client.complete("s", "u"));
        assertEquals("The AI assistant is busy (rate limit reached); try again shortly", ex.getMessage());
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class, () -> client.complete("s", "u"));
    }

    @Test
    void missingApiKeyFailsFastWithoutCallingTheProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer none = MockRestServiceServer.bindTo(builder).build();
        GeminiLlmClient unconfigured = new GeminiLlmClient(builder.build(),
                new AiProperties(BASE, "", "m", 1000, 1000, 256, 0.2, 8000));

        assertThrows(ServiceUnavailableException.class, () -> unconfigured.complete("s", "u"));
        none.verify();
    }
}
