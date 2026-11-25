package com.codepath.judging;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.Judge0Properties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs Java source in the isolated Judge0 server. The Spring Boot process never executes student code:
 * it only sends it over HTTP and reads the verdicts back.
 *
 * <p>Each test case becomes one Judge0 submission (sent in batches of at most {@value #MAX_BATCH_SIZE}),
 * then the batch is polled until Judge0 has finished every one. Judge0 itself compares stdout with the
 * expected output ignoring trailing whitespace, so this class never re-implements output comparison.
 */
@Slf4j
@Service
public class Judge0Service {

    /** Judge0's default limit for one batch request. */
    static final int MAX_BATCH_SIZE = 20;
    static final int MAX_CAPTURED_CHARS = 10_000;
    /** The JVM needs headroom, so a small question limit is raised to this to avoid false runtime errors. */
    static final int MIN_MEMORY_LIMIT_KB = 131_072;
    /** Judge0 CE's default maximum memory_limit. */
    static final int MAX_MEMORY_LIMIT_KB = 512_000;
    /** Judge0 CE's default maximum cpu_time_limit and wall_time_limit, in seconds. */
    static final double MAX_CPU_SECONDS = 15.0;
    static final double MAX_WALL_SECONDS = 20.0;

    /**
     * Judge0 is a Rails app and reads JSON bodies using Content-Length. RestClient streams an object body
     * with chunked encoding (no Content-Length), which Judge0 sees as an empty body, so the request body is
     * serialized to bytes first and sent with a known length.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String RESULT_FIELDS = "token,status,time,memory,stdout,stderr,compile_output,message";

    private final RestClient restClient;
    private final Judge0Properties properties;

    public Judge0Service(@Qualifier("judge0RestClient") RestClient restClient, Judge0Properties properties) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
    }

    /**
     * Runs {@code sourceCode} once per test case and returns the results in the same order as {@code testCases}.
     *
     * @throws ServiceUnavailableException if Judge0 cannot be reached, errors out, or does not answer in time
     */
    public List<JudgeCaseResult> judge(String sourceCode, List<JudgeTestCase> testCases, int timeLimitMs,
            int memoryLimitKb) {
        Objects.requireNonNull(sourceCode, "sourceCode");
        Objects.requireNonNull(testCases, "testCases");
        if (testCases.isEmpty()) {
            return List.of();
        }
        double cpuSeconds = cpuTimeLimitSeconds(timeLimitMs, properties.javaStartupAllowanceMs());
        double wallSeconds = wallTimeLimitSeconds(cpuSeconds);
        int memoryKb = memoryLimitKb(memoryLimitKb);
        String encodedSource = encode(sourceCode);

        List<JudgeCaseResult> all = new ArrayList<>(testCases.size());
        for (int from = 0; from < testCases.size(); from += MAX_BATCH_SIZE) {
            List<JudgeTestCase> chunk = testCases.subList(from, Math.min(testCases.size(), from + MAX_BATCH_SIZE));
            List<SubmissionPayload> payloads = new ArrayList<>(chunk.size());
            for (JudgeTestCase testCase : chunk) {
                payloads.add(new SubmissionPayload(
                        properties.javaLanguageId(),
                        encodedSource,
                        encode(testCase.input()),
                        encode(testCase.expectedOutput()),
                        cpuSeconds,
                        wallSeconds,
                        memoryKb));
            }
            List<String> tokens = createBatch(payloads);
            for (ResultPayload payload : awaitResults(tokens)) {
                all.add(toCaseResult(payload));
            }
        }
        return all;
    }

    // ---- limits -----------------------------------------------------------------------------------------------

    static double cpuTimeLimitSeconds(int questionLimitMs, int startupAllowanceMs) {
        double seconds = (Math.max(questionLimitMs, 1) + Math.max(startupAllowanceMs, 0)) / 1000.0;
        return Math.min(seconds, MAX_CPU_SECONDS);
    }

    static double wallTimeLimitSeconds(double cpuSeconds) {
        return Math.min(MAX_WALL_SECONDS, Math.max(cpuSeconds + 1.0, cpuSeconds * 2.0));
    }

    static int memoryLimitKb(int questionLimitKb) {
        return Math.min(MAX_MEMORY_LIMIT_KB, Math.max(questionLimitKb, MIN_MEMORY_LIMIT_KB));
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------

    private List<String> createBatch(List<SubmissionPayload> payloads) {
        List<TokenPayload> response;
        try {
            response = restClient.post()
                    .uri("/submissions/batch?base64_encoded=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJsonBytes(new BatchRequest(payloads)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TokenPayload>>() {
                    });
        } catch (RestClientException ex) {
            throw unavailable("submit code to", ex);
        }
        if (response == null || response.size() != payloads.size()) {
            throw new ServiceUnavailableException("The code judge returned an unexpected response. Please try again.");
        }
        List<String> tokens = new ArrayList<>(response.size());
        for (TokenPayload payload : response) {
            if (payload == null || payload.token() == null || payload.token().isBlank()) {
                throw new ServiceUnavailableException(
                        "The code judge rejected the submission. Please try again.");
            }
            tokens.add(payload.token());
        }
        return tokens;
    }

    /** Polls until every token has a final status, returning payloads in the order of {@code tokens}. */
    private List<ResultPayload> awaitResults(List<String> tokens) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.pollTimeoutMs());
        while (true) {
            Map<String, ResultPayload> byToken = new HashMap<>();
            for (ResultPayload payload : fetch(tokens)) {
                if (payload != null && payload.token() != null) {
                    byToken.put(payload.token(), payload);
                }
            }
            List<ResultPayload> ordered = new ArrayList<>(tokens.size());
            boolean allFinished = true;
            for (String token : tokens) {
                ResultPayload payload = byToken.get(token);
                if (payload == null || payload.status() == null
                        || !JudgeVerdictMapper.isFinished(payload.status().id())) {
                    allFinished = false;
                    break;
                }
                ordered.add(payload);
            }
            if (allFinished) {
                return ordered;
            }
            if (System.nanoTime() >= deadline) {
                log.warn("Judge0 did not finish {} submission(s) within {} ms", tokens.size(),
                        properties.pollTimeoutMs());
                throw new ServiceUnavailableException(
                        "The code judge took too long to respond. Please try again.");
            }
            try {
                Thread.sleep(properties.pollIntervalMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ServiceUnavailableException("Waiting for the code judge was interrupted.", ex);
            }
        }
    }

    private List<ResultPayload> fetch(List<String> tokens) {
        BatchResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/submissions/batch")
                            .queryParam("tokens", String.join(",", tokens))
                            .queryParam("base64_encoded", "true")
                            .queryParam("fields", RESULT_FIELDS)
                            .build())
                    .retrieve()
                    .body(BatchResponse.class);
        } catch (RestClientException ex) {
            throw unavailable("read results from", ex);
        }
        if (response == null || response.submissions() == null) {
            throw new ServiceUnavailableException("The code judge returned an unexpected response. Please try again.");
        }
        return response.submissions();
    }

    private static byte[] toJsonBytes(BatchRequest request) {
        try {
            return JSON.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the Judge0 request", ex);
        }
    }

    private static ServiceUnavailableException unavailable(String action, RestClientException ex) {
        log.error("Could not {} Judge0: {}", action, ex.getMessage());
        return new ServiceUnavailableException("The code judge is unavailable right now. Please try again shortly.", ex);
    }

    // ---- mapping ----------------------------------------------------------------------------------------------

    private static JudgeCaseResult toCaseResult(ResultPayload payload) {
        int statusId = payload.status().id();
        if (statusId == JudgeVerdictMapper.INTERNAL_ERROR || statusId == JudgeVerdictMapper.EXEC_FORMAT_ERROR) {
            // The student's code is not at fault; surface Judge0's own explanation in the server log.
            log.error("Judge0 internal error (status {} '{}'): message={} stderr={}", statusId,
                    payload.status().description(), decode(payload.message()), decode(payload.stderr()));
        }
        return new JudgeCaseResult(
                JudgeVerdictMapper.toVerdict(statusId),
                statusId,
                payload.status().description(),
                toMillis(payload.time()),
                payload.memory(),
                decode(payload.stdout()),
                decode(payload.stderr()),
                decode(payload.compileOutput()),
                decode(payload.message()));
    }

    static Integer toMillis(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(seconds) * 1000.0);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String encode(String text) {
        return Base64.getEncoder().encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String base64) {
        if (base64 == null) {
            return null;
        }
        String text;
        try {
            // Judge0 wraps base64 output in newlines, which the MIME decoder tolerates.
            text = new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            text = base64;
        }
        if (text.length() > MAX_CAPTURED_CHARS) {
            return text.substring(0, MAX_CAPTURED_CHARS) + "\n... (output truncated)";
        }
        return text;
    }

    // ---- Judge0 wire format -----------------------------------------------------------------------------------

    public record BatchRequest(List<SubmissionPayload> submissions) {
    }

    public record SubmissionPayload(
            @JsonProperty("language_id") int languageId,
            @JsonProperty("source_code") String sourceCode,
            @JsonProperty("stdin") String stdin,
            @JsonProperty("expected_output") String expectedOutput,
            @JsonProperty("cpu_time_limit") double cpuTimeLimit,
            @JsonProperty("wall_time_limit") double wallTimeLimit,
            @JsonProperty("memory_limit") int memoryLimit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenPayload(@JsonProperty("token") String token) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchResponse(@JsonProperty("submissions") List<ResultPayload> submissions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultPayload(
            @JsonProperty("token") String token,
            @JsonProperty("status") StatusPayload status,
            @JsonProperty("time") String time,
            @JsonProperty("memory") Integer memory,
            @JsonProperty("stdout") String stdout,
            @JsonProperty("stderr") String stderr,
            @JsonProperty("compile_output") String compileOutput,
            @JsonProperty("message") String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusPayload(@JsonProperty("id") int id, @JsonProperty("description") String description) {
    }
}
