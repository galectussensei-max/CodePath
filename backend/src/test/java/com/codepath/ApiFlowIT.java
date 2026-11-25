package com.codepath;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end API flow through the real Spring context, security filter chain and PostgreSQL.
 *
 * <p>It needs the app database from docker-compose (host port 5433) and the seed data, and it never calls
 * Judge0 or the LLM, so it works with the Judge0 VM off and no AI key. It is named {@code *IT} on purpose:
 * Maven's default {@code mvn test} skips it, so the normal unit-test run stays database-free. Run it with
 * {@code mvn -Dtest=ApiFlowIT test}. Each run registers one new throw-away learner (unique email).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiFlowIT {

    private static final String PASSWORD = "Passw0rd!123";
    private static final String EMAIL = "it-" + UUID.randomUUID() + "@example.com";
    private static final List<String> PROTECTED_GETS = List.of(
            "/api/questions",
            "/api/assessment/diagnostic",
            "/api/users/profile",
            "/api/learning/recommendations",
            "/api/revision/queue",
            "/api/analytics/summary",
            "/api/roadmap");

    private static String token;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.path("success").asBoolean(), "response must be a success envelope");
        return root.path("data");
    }

    private MvcResult authedGet(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @Order(1)
    void protectedEndpointsRejectAnonymousRequests() throws Exception {
        for (String path : PROTECTED_GETS) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    @Order(2)
    void registerRejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "A", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    @Order(3)
    void registerReturnsTokenAndANewLearnerWithoutDiagnostic() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Integration Learner", "email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = data(result);
        token = data.path("accessToken").asText();
        assertFalse(token.isBlank(), "a JWT must be issued");
        assertFalse(data.path("user").path("diagnosticCompleted").asBoolean(), "diagnostic starts incomplete");
        assertFalse(result.getResponse().getContentAsString().toLowerCase().contains("password"),
                "the response must not echo any password field");
    }

    @Test
    @Order(4)
    void duplicateEmailAndWrongPasswordAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Someone Else", "email", EMAIL.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", "wrong-password-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @Order(5)
    void questionBankHidesDiagnosticQuestionsAndNeverLeaksHiddenTestCases() throws Exception {
        MvcResult list = authedGet("/api/questions");
        JsonNode questions = data(list);
        assertTrue(questions.size() >= 15, "the seed data has 15 practice problems");
        assertFalse(list.getResponse().getContentAsString().contains("DIAGNOSTIC"),
                "diagnostic questions must not appear in the problem bank");

        for (JsonNode summary : questions) {
            assertFalse(summary.has("testCases") || summary.has("sampleTestCases"),
                    "the list view carries no test cases at all");
        }

        String slug = questions.get(0).path("slug").asText();
        MvcResult detail = authedGet("/api/questions/" + slug);
        JsonNode samples = data(detail).path("sampleTestCases");
        assertTrue(samples.isArray() && samples.size() > 0, "a problem shows its public sample cases");
        for (JsonNode sample : samples) {
            List<String> fields = new ArrayList<>();
            sample.fieldNames().forEachRemaining(fields::add);
            assertTrue(fields.containsAll(List.of("id", "input", "expectedOutput")));
            assertFalse(fields.contains("hidden") || fields.contains("isHidden"),
                    "the hidden flag is not part of the client contract");
        }
        String body = detail.getResponse().getContentAsString().toLowerCase();
        assertFalse(body.contains("\"hidden\"") || body.contains("\"ishidden\""));
    }

    @Test
    @Order(6)
    void diagnosticNeverRevealsTheAnswerKey() throws Exception {
        MvcResult result = authedGet("/api/assessment/diagnostic");
        JsonNode questions = data(result);
        assertTrue(questions.size() >= 10, "the seed data has 10 diagnostic questions");
        for (JsonNode question : questions) {
            assertFourOptions(question);
        }
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("expectedOutput") || body.contains("expected_output"),
                "the answer key is the hidden test case and must never be returned");
    }

    private static void assertFourOptions(JsonNode question) {
        assertTrue(question.path("options").size() == 4, "each diagnostic question has options A to D");
    }

    @Test
    @Order(7)
    void completingTheDiagnosticMarksTheLearnerAsAssessed() throws Exception {
        JsonNode questions = data(authedGet("/api/assessment/diagnostic"));
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode question : questions) {
            answers.add(Map.of("questionId", question.path("id").asLong(), "answer", "A"));
        }

        mvc.perform(post("/api/assessment/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("answers", answers))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(questions.size()))
                .andExpect(jsonPath("$.data.topics").isArray());

        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosticCompleted").value(true));
    }

    @Test
    @Order(8)
    void everyRecommendationExplainsItself() throws Exception {
        JsonNode body = data(authedGet("/api/learning/recommendations?limit=5"));
        assertTrue(body.path("recommendations").isArray());
        assertTrue(body.path("recommendations").size() <= 5, "limit is respected");
        for (JsonNode item : body.path("recommendations")) {
            assertTrue(item.path("reason").isTextual() && !item.path("reason").asText().isBlank(),
                    "every recommendation must carry a human-readable reason");
            assertFalse(item.path("slug").asText().isBlank());
        }
        for (JsonNode locked : body.path("lockedTopics")) {
            assertFalse(locked.path("reason").asText().isBlank(), "a locked topic explains why it is locked");
        }
    }

    @Test
    @Order(9)
    void roadmapRevisionAndAnalyticsRespondForANewLearner() throws Exception {
        JsonNode roadmap = data(authedGet("/api/roadmap"));
        assertTrue(roadmap.path("steps").size() >= 5, "the seed curriculum has 5 topics");
        int previousOrder = 0;
        for (JsonNode step : roadmap.path("steps")) {
            assertTrue(step.path("order").asInt() > previousOrder, "steps are returned in order");
            previousOrder = step.path("order").asInt();
            for (JsonNode problem : step.path("problems")) {
                assertFalse(problem.path("solved").asBoolean(), "a new learner has solved nothing");
            }
        }

        JsonNode revision = data(authedGet("/api/revision/queue"));
        assertTrue(revision.path("items").isArray());
        assertTrue(revision.path("dueCount").asInt() == revision.path("items").size());

        JsonNode analytics = data(authedGet("/api/analytics/summary"));
        assertTrue(analytics.path("overview").path("totalSubmissions").asLong() == 0, "no submissions yet");
        assertTrue(analytics.path("topicMastery").size() >= 5, "every topic is charted, assessed or not");
        assertTrue(analytics.path("verdictDistribution").isArray());
        assertTrue(analytics.path("recurringErrors").isEmpty());
    }

    @Test
    @Order(10)
    void unknownQuestionSlugIsANotFoundEnvelopeNotAStackTrace() throws Exception {
        mvc.perform(get("/api/questions/this-slug-does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("Exception"))));
    }
}
