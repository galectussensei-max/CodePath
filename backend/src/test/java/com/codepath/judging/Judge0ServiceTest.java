package com.codepath.judging;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.Judge0Properties;
import com.codepath.submissions.Verdict;

class Judge0ServiceTest {

    private static final String BASE = "http://judge0.test";
    private static final String BATCH = BASE + "/submissions/batch";

    private MockRestServiceServer server;
    private Judge0Service service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        Judge0Properties properties = new Judge0Properties(BASE, 62, 1000, 1, 60, 1000, 1000);
        service = new Judge0Service(builder.build(), properties);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String result(String token, int statusId, String description, String extraFields) {
        return "{\"token\":\"" + token + "\",\"status\":{\"id\":" + statusId + ",\"description\":\"" + description
                + "\"}" + extraFields + "}";
    }

    @Test
    void sendsBase64PayloadsPollsUntilFinishedAndKeepsCaseOrder() {
        server.expect(requestTo(BATCH + "?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.submissions.length()").value(2))
                .andExpect(jsonPath("$.submissions[0].language_id").value(62))
                .andExpect(jsonPath("$.submissions[0].source_code").value(b64("class Main {}")))
                .andExpect(jsonPath("$.submissions[0].stdin").value(b64("1 2\n")))
                .andExpect(jsonPath("$.submissions[1].expected_output").value(b64("9")))
                .andExpect(jsonPath("$.submissions[0].memory_limit").value(262144))
                .andExpect(jsonPath("$.submissions[0].cpu_time_limit").value(3.0))
                .andRespond(withSuccess("[{\"token\":\"t1\"},{\"token\":\"t2\"}]", MediaType.APPLICATION_JSON));
        // First poll: still processing.
        server.expect(requestTo(startsWith(BATCH + "?tokens=t1,t2")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"submissions\":[" + result("t1", 3, "Accepted", "") + ","
                        + result("t2", 2, "Processing", "") + "]}", MediaType.APPLICATION_JSON));
        // Second poll: both done, returned in reverse order to prove ordering follows the tokens.
        server.expect(requestTo(startsWith(BATCH + "?tokens=t1,t2")))
                .andRespond(withSuccess("{\"submissions\":["
                        + result("t2", 4, "Wrong Answer", ",\"time\":\"0.120\",\"memory\":2048,\"stdout\":\""
                                + b64("8\n") + "\"") + ","
                        + result("t1", 3, "Accepted", ",\"time\":\"0.045\",\"memory\":1024,\"stdout\":\""
                                + b64("3") + "\"") + "]}", MediaType.APPLICATION_JSON));

        List<JudgeCaseResult> results = service.judge("class Main {}",
                List.of(new JudgeTestCase("1 2\n", "3"), new JudgeTestCase("4 5\n", "9")), 2000, 262144);

        assertEquals(2, results.size());
        assertEquals(Verdict.ACCEPTED, results.get(0).verdict());
        assertEquals(45, results.get(0).timeMs());
        assertEquals(1024, results.get(0).memoryKb());
        assertEquals("3", results.get(0).stdout());
        assertEquals(Verdict.WRONG_ANSWER, results.get(1).verdict());
        assertEquals(120, results.get(1).timeMs());
        assertEquals("8\n", results.get(1).stdout());
        server.verify();
    }

    @Test
    void compilationErrorCarriesDecodedCompilerOutput() {
        server.expect(requestTo(BATCH + "?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BATCH + "?tokens=t1")))
                .andRespond(withSuccess("{\"submissions\":[" + result("t1", 6, "Compilation Error",
                        ",\"compile_output\":\"" + b64("Main.java:3: error: ';' expected") + "\"") + "]}",
                        MediaType.APPLICATION_JSON));

        JudgeCaseResult only = service.judge("bad", List.of(new JudgeTestCase("", "x")), 2000, 262144).get(0);

        assertEquals(Verdict.COMPILATION_ERROR, only.verdict());
        assertEquals("Main.java:3: error: ';' expected", only.compileOutput());
        assertNull(only.stdout());
        assertNull(only.timeMs());
    }

    @Test
    void infiniteLoopIsTimeLimitExceeded() {
        server.expect(requestTo(BATCH + "?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BATCH + "?tokens=t1")))
                .andRespond(withSuccess("{\"submissions\":[" + result("t1", 5, "Time Limit Exceeded",
                        ",\"time\":\"3.001\"") + "]}", MediaType.APPLICATION_JSON));

        JudgeCaseResult only = service.judge("loop", List.of(new JudgeTestCase("", "x")), 2000, 262144).get(0);

        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, only.verdict());
        assertEquals(3001, only.timeMs());
    }

    @Test
    void judgeDownIsServiceUnavailable() {
        server.expect(requestTo(BATCH + "?base64_encoded=true")).andRespond(withServerError());

        assertThrows(ServiceUnavailableException.class,
                () -> service.judge("code", List.of(new JudgeTestCase("", "x")), 2000, 262144));
    }

    @Test
    void judgeInternalErrorIsNotTurnedIntoAVerdict() {
        server.expect(requestTo(BATCH + "?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BATCH + "?tokens=t1")))
                .andRespond(withSuccess("{\"submissions\":[" + result("t1", 13, "Internal Error", "") + "]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(ServiceUnavailableException.class,
                () -> service.judge("code", List.of(new JudgeTestCase("", "x")), 2000, 262144));
    }

    @Test
    void givesUpWhenJudgeNeverFinishes() {
        server.expect(requestTo(BATCH + "?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.min(1), requestTo(startsWith(BATCH + "?tokens=t1")))
                .andRespond(withSuccess("{\"submissions\":[" + result("t1", 1, "In Queue", "") + "]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(ServiceUnavailableException.class,
                () -> service.judge("code", List.of(new JudgeTestCase("", "x")), 2000, 262144));
    }

    @Test
    void noTestCasesMeansNoJudge0Calls() {
        assertTrue(service.judge("code", List.of(), 2000, 262144).isEmpty());
        server.verify();
    }

    @Test
    void limitsAreAdjustedForJavaAndClampedToJudge0Maximums() {
        assertEquals(3.0, Judge0Service.cpuTimeLimitSeconds(2000, 1000), 1e-9);
        assertEquals(15.0, Judge0Service.cpuTimeLimitSeconds(60_000, 1000), 1e-9);
        assertEquals(6.0, Judge0Service.wallTimeLimitSeconds(3.0), 1e-9);
        assertEquals(20.0, Judge0Service.wallTimeLimitSeconds(15.0), 1e-9);
        assertEquals(131_072, Judge0Service.memoryLimitKb(16_000));
        assertEquals(262_144, Judge0Service.memoryLimitKb(262_144));
        assertEquals(512_000, Judge0Service.memoryLimitKb(2_000_000));
    }

    @Test
    void decodeToleratesWrappedBase64AndTruncatesHugeOutput() {
        String wrapped = b64("hello world").substring(0, 6) + "\n" + b64("hello world").substring(6);
        assertEquals("hello world", Judge0Service.decode(wrapped));
        String huge = "x".repeat(Judge0Service.MAX_CAPTURED_CHARS + 50);
        assertTrue(Judge0Service.decode(b64(huge)).endsWith("(output truncated)"));
        assertNull(Judge0Service.decode(null));
    }
}
