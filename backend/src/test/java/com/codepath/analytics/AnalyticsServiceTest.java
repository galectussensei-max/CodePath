package com.codepath.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.TestData;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.questions.Difficulty;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.TopicVerdictCount;
import com.codepath.submissions.Verdict;
import com.codepath.users.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    private static final Long USER_ID = 9L;

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private SubmissionRepository submissionRepository;

    private final TestData data = new TestData();
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(topicRepository, questionRepository, topicMasteryRepository,
                submissionRepository);
        when(topicRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(data.topics());
        when(questionRepository.findPracticeQuestions(anyString())).thenReturn(data.questions());
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(submissionRepository.findQuestionIdsByVerdict(USER_ID, Verdict.ACCEPTED)).thenReturn(List.of());
        when(submissionRepository.findAttemptedQuestionIds(USER_ID)).thenReturn(List.of());
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of());
        // Mockito defaults a Double return to 0.0; the real query returns null when there are no rows.
        doReturn(null).when(submissionRepository).averageExecutionTimeMs(USER_ID, Verdict.ACCEPTED);
    }

    @Test
    void newLearnerGetsZeroFilledSummary() {
        AnalyticsSummaryDto summary = service.summary(USER_ID);

        assertEquals(0, summary.overview().totalSubmissions());
        assertEquals(0.0, summary.overview().acceptanceRatePercent());
        assertEquals(9, summary.overview().totalProblems());
        assertNull(summary.overview().averageAcceptedRuntimeMs());
        assertEquals(3, summary.topicMastery().size());
        assertTrue(summary.topicMastery().stream().allMatch(p -> p.status() == MasteryStatus.UNASSESSED
                && p.masteryScore() == 0.0));
        assertEquals(5, summary.verdictDistribution().size(), "PENDING is never charted");
        assertTrue(summary.verdictDistribution().stream().allMatch(v -> v.count() == 0));
        assertTrue(summary.recurringErrors().isEmpty());
    }

    @Test
    void summarisesMasteryDifficultyVerdictsAndRecurringErrors() {
        User user = TestData.user(USER_ID);
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(
                TestData.mastery(user, data.arrays, 55.0, MasteryStatus.DEVELOPING, null)));
        when(submissionRepository.findQuestionIdsByVerdict(USER_ID, Verdict.ACCEPTED))
                .thenReturn(List.of(data.a1.getId(), data.a2.getId()));
        when(submissionRepository.findAttemptedQuestionIds(USER_ID))
                .thenReturn(List.of(data.a1.getId(), data.a2.getId(), data.a3.getId()));
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of(
                new TopicVerdictCount(data.arrays.getId(), Verdict.ACCEPTED, 2L),
                new TopicVerdictCount(data.arrays.getId(), Verdict.WRONG_ANSWER, 4L),
                new TopicVerdictCount(data.arrays.getId(), Verdict.TIME_LIMIT_EXCEEDED, 1L),
                new TopicVerdictCount(data.strings.getId(), Verdict.COMPILATION_ERROR, 2L)));
        when(submissionRepository.averageExecutionTimeMs(USER_ID, Verdict.ACCEPTED)).thenReturn(123.456);

        AnalyticsSummaryDto summary = service.summary(USER_ID);

        AnalyticsSummaryDto.Overview overview = summary.overview();
        assertEquals(9, overview.totalSubmissions());
        assertEquals(2, overview.acceptedSubmissions());
        assertEquals(22.2, overview.acceptanceRatePercent(), 1e-9);
        assertEquals(2, overview.problemsSolved());
        assertEquals(3, overview.problemsAttempted());
        assertEquals(123.5, overview.averageAcceptedRuntimeMs(), 1e-9);

        AnalyticsSummaryDto.TopicMasteryPoint arrays = summary.topicMastery().get(0);
        assertEquals("Arrays", arrays.topic());
        assertEquals(55.0, arrays.masteryScore());
        assertEquals(4, arrays.totalProblems());

        AnalyticsSummaryDto.DifficultyProgress easy = summary.difficultyBreakdown().get(0);
        assertEquals(Difficulty.EASY, easy.difficulty());
        assertEquals(4, easy.totalProblems());
        assertEquals(1, easy.solved());
        AnalyticsSummaryDto.DifficultyProgress hard = summary.difficultyBreakdown().get(2);
        assertEquals(1, hard.attempted());
        assertEquals(0, hard.solved());

        long wrong = summary.verdictDistribution().stream()
                .filter(v -> v.verdict() == Verdict.WRONG_ANSWER).findFirst().orElseThrow().count();
        assertEquals(4, wrong);

        assertEquals(2, summary.recurringErrors().size());
        AnalyticsSummaryDto.RecurringError top = summary.recurringErrors().get(0);
        assertEquals(Verdict.WRONG_ANSWER, top.verdict());
        assertEquals("Arrays", top.topic());
        assertEquals(57.1, top.sharePercent(), 1e-9);
        assertEquals("Wrong answer 4 times in Arrays", top.description());
        assertEquals(Verdict.COMPILATION_ERROR, summary.recurringErrors().get(1).verdict());
    }
}
