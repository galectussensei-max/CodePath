package com.codepath.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.TestData;
import com.codepath.questions.Difficulty;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;
import com.codepath.users.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

    private static final Long BEGINNER = 1L;
    private static final Long ADVANCED = 2L;
    private static final Long STRUGGLING = 3L;

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicPrerequisiteRepository topicPrerequisiteRepository;
    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private SubmissionRepository submissionRepository;

    private final TestData data = new TestData();
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(questionRepository, topicRepository, topicPrerequisiteRepository,
                topicMasteryRepository, submissionRepository);
        when(topicRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(data.topics());
        when(topicPrerequisiteRepository.findAll()).thenReturn(data.links());
        when(questionRepository.findPracticeQuestions(anyString())).thenReturn(data.questions());
        when(topicMasteryRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(submissionRepository.findQuestionIdsByVerdict(anyLong(), eq(Verdict.ACCEPTED))).thenReturn(List.of());
        when(submissionRepository.findAttemptedQuestionIds(anyLong())).thenReturn(List.of());
    }

    private void givenMastery(Long userId, double arrays, double strings) {
        User user = TestData.user(userId);
        when(topicMasteryRepository.findByUserId(userId)).thenReturn(List.of(
                TestData.mastery(user, data.arrays, arrays, MasteryStatus.fromScore(arrays), null),
                TestData.mastery(user, data.strings, strings, MasteryStatus.fromScore(strings), null)));
    }

    private static List<String> slugs(RecommendationsDto result) {
        return result.recommendations().stream().map(RecommendationDto::slug).toList();
    }

    @Test
    void learnerWithNoMasteryOnlyGetsRootTopicProblemsAndSeesWhyOthersAreLocked() {
        RecommendationsDto result = service.recommend(BEGINNER, 10);

        assertEquals(List.of("arrays-easy-tp", "arrays-easy-sw", "arrays-medium-tp", "arrays-hard-sw"),
                slugs(result));
        assertTrue(result.recommendations().stream().allMatch(r -> r.topic().equals("Arrays")));
        assertEquals(List.of("Strings", "Dynamic Programming"),
                result.lockedTopics().stream().map(LockedTopicDto::topic).toList());
        assertTrue(result.lockedTopics().get(0).reason().contains("Arrays reaches 40% (not assessed yet)"));
    }

    @Test
    void differentLearnersGetDifferentButPrerequisiteValidRecommendations() {
        givenMastery(ADVANCED, 75.0, 55.0);
        givenMastery(STRUGGLING, 20.0, 0.0);

        RecommendationsDto beginner = service.recommend(BEGINNER, 5);
        RecommendationsDto advanced = service.recommend(ADVANCED, 5);
        RecommendationsDto struggling = service.recommend(STRUGGLING, 5);

        assertNotEquals(slugs(beginner), slugs(advanced));
        assertNotEquals(slugs(struggling), slugs(advanced));

        // Advanced: Arrays 75 and Strings 55 meet every prerequisite, so Dynamic Programming is open and led by
        // an Easy DP problem (DP is unassessed, so it has the highest need).
        assertEquals("dp-easy", advanced.recommendations().get(0).slug());
        assertTrue(advanced.recommendations().stream().anyMatch(r -> r.topic().equals("Dynamic Programming")));
        assertTrue(advanced.lockedTopics().isEmpty());

        // Beginner and struggling learners must never receive Strings or DP problems.
        for (RecommendationsDto restricted : List.of(beginner, struggling)) {
            assertTrue(restricted.recommendations().stream().allMatch(r -> r.topic().equals("Arrays")),
                    "unlocked topics only: " + slugs(restricted));
            assertEquals(2, restricted.lockedTopics().size());
        }
        assertTrue(struggling.lockedTopics().get(0).reason().contains("currently 20%"));
    }

    @Test
    void everyRecommendationHasAHumanReadableReason() {
        givenMastery(ADVANCED, 75.0, 55.0);

        RecommendationsDto result = service.recommend(ADVANCED, 5);

        assertFalse(result.recommendations().isEmpty());
        for (RecommendationDto item : result.recommendations()) {
            assertTrue(item.reason().startsWith("Recommended because "), item.reason());
            assertTrue(item.reason().contains("mastery is") || item.reason().contains("has not been assessed"),
                    item.reason());
        }
        RecommendationDto strings = result.recommendations().stream()
                .filter(r -> r.topic().equals("Strings")).findFirst().orElseThrow();
        assertTrue(strings.reason().contains("Strings mastery is 55% (prerequisite met: Arrays 75%)"),
                strings.reason());
        assertTrue(strings.reason().contains("you have not solved any Palindromes problems yet"), strings.reason());
    }

    @Test
    void solvedProblemsAreNeverRecommendedAgain() {
        when(submissionRepository.findQuestionIdsByVerdict(anyLong(), eq(Verdict.ACCEPTED)))
                .thenReturn(List.of(data.a1.getId()));

        RecommendationsDto result = service.recommend(BEGINNER, 10);

        assertFalse(slugs(result).contains("arrays-easy-tp"));
        assertEquals(3, result.recommendations().size());
    }

    @Test
    void conceptGapUsesSolvedCountInReason() {
        when(submissionRepository.findQuestionIdsByVerdict(anyLong(), eq(Verdict.ACCEPTED)))
                .thenReturn(List.of(data.a1.getId()));

        RecommendationsDto result = service.recommend(BEGINNER, 10);

        RecommendationDto next = result.recommendations().stream()
                .filter(r -> r.slug().equals("arrays-medium-tp")).findFirst().orElseThrow();
        assertTrue(next.reason().contains("Two Pointers needs practice (1 of 2 solved)"), next.reason());
    }

    @Test
    void revisionDueAndRetryRaiseTheScoreAndAreExplained() {
        User user = TestData.user(BEGINNER);
        when(topicMasteryRepository.findByUserId(BEGINNER)).thenReturn(List.of(
                TestData.mastery(user, data.arrays, 20.0, MasteryStatus.WEAK, Instant.now().minus(Duration.ofDays(1)))));
        when(submissionRepository.findAttemptedQuestionIds(BEGINNER)).thenReturn(List.of(data.a4.getId()));

        RecommendationsDto result = service.recommend(BEGINNER, 10);

        RecommendationDto retried = result.recommendations().stream()
                .filter(r -> r.slug().equals("arrays-easy-sw")).findFirst().orElseThrow();
        RecommendationDto fresh = result.recommendations().stream()
                .filter(r -> r.slug().equals("arrays-easy-tp")).findFirst().orElseThrow();
        assertEquals("arrays-easy-sw", result.recommendations().get(0).slug());
        assertEquals(RecommendationService.RETRY_BONUS, retried.priorityScore() - fresh.priorityScore(), 0.11);
        assertTrue(retried.reason().contains("due for revision"));
        assertTrue(retried.reason().contains("tried this problem before"));
        assertFalse(fresh.reason().contains("tried this problem before"));
    }

    @Test
    void limitIsRespectedAndConceptsAreMixedWhenPossible() {
        givenMastery(ADVANCED, 75.0, 55.0);

        List<RecommendationDto> items = service.recommend(ADVANCED, 3).recommendations();

        assertEquals(3, items.size());
        assertEquals(List.of(1, 2, 3), items.stream().map(RecommendationDto::rank).toList());
        Map<String, Long> perConcept = items.stream()
                .collect(Collectors.groupingBy(RecommendationDto::concept, Collectors.counting()));
        assertTrue(perConcept.values().stream().allMatch(count -> count <= RecommendationService.MAX_PER_CONCEPT));
    }

    @Test
    void defaultAndMaximumLimitsApply() {
        assertEquals(4, service.recommend(BEGINNER, 0).recommendations().size());
        assertEquals(4, service.recommend(BEGINNER, 500).recommendations().size());
    }

    @Test
    void targetDifficultyFollowsMasteryThresholds() {
        assertEquals(Difficulty.EASY, RecommendationService.targetDifficulty(39.9));
        assertEquals(Difficulty.MEDIUM, RecommendationService.targetDifficulty(40.0));
        assertEquals(Difficulty.MEDIUM, RecommendationService.targetDifficulty(69.9));
        assertEquals(Difficulty.HARD, RecommendationService.targetDifficulty(70.0));
    }
}
