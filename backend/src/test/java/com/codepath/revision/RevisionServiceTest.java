package com.codepath.revision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.TestData;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.TopicVerdictCount;
import com.codepath.submissions.Verdict;
import com.codepath.users.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevisionServiceTest {

    private static final Long USER_ID = 5L;

    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private SubmissionRepository submissionRepository;

    private final TestData data = new TestData();
    private final User user = TestData.user(USER_ID);
    private RevisionService service;

    @BeforeEach
    void setUp() {
        service = new RevisionService(topicMasteryRepository, submissionRepository);
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of());
    }

    private TopicMastery row(com.codepath.questions.Topic topic, double score, Instant nextDue) {
        return TestData.mastery(user, topic, score, MasteryStatus.fromScore(score), nextDue);
    }

    @Test
    void overdueTopicIsQueuedAndFutureTopicIsNot() {
        Instant now = Instant.now();
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(
                row(data.arrays, 30, now.minus(Duration.ofDays(2)).minusSeconds(3600)),
                row(data.strings, 60, now.plus(Duration.ofDays(2)))));

        RevisionQueueDto queue = service.queue(USER_ID);

        assertEquals(1, queue.dueCount());
        RevisionItemDto item = queue.items().get(0);
        assertEquals("Arrays", item.topic());
        assertEquals(RevisionTrigger.SCHEDULED, item.trigger());
        assertEquals(2, item.daysOverdue());
        assertTrue(item.reason().contains("revision was due 2 days ago"), item.reason());
    }

    @Test
    void highErrorRateQueuesATopicThatIsNotDue() {
        Instant future = Instant.now().plus(Duration.ofDays(3));
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(row(data.strings, 30, future)));
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of(
                new TopicVerdictCount(data.strings.getId(), Verdict.WRONG_ANSWER, 3L),
                new TopicVerdictCount(data.strings.getId(), Verdict.ACCEPTED, 1L),
                new TopicVerdictCount(data.strings.getId(), Verdict.COMPILATION_ERROR, 9L)));

        RevisionQueueDto queue = service.queue(USER_ID);

        assertEquals(1, queue.dueCount());
        RevisionItemDto item = queue.items().get(0);
        assertEquals(RevisionTrigger.HIGH_ERROR_RATE, item.trigger());
        assertEquals(0, item.daysOverdue());
        assertEquals(4, item.gradedSubmissions(), "compile errors must not count as graded");
        assertEquals(0.75, item.errorRate(), 1e-9);
        assertTrue(item.reason().contains("75% of your 4 graded submissions in Strings failed"), item.reason());
    }

    @Test
    void fewSubmissionsOrLowErrorRateDoNotTrigger() {
        Instant future = Instant.now().plus(Duration.ofDays(3));
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(
                row(data.arrays, 50, future), row(data.strings, 50, future), row(data.dp, 50, future)));
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of(
                new TopicVerdictCount(data.arrays.getId(), Verdict.WRONG_ANSWER, 2L),
                new TopicVerdictCount(data.strings.getId(), Verdict.WRONG_ANSWER, 1L),
                new TopicVerdictCount(data.strings.getId(), Verdict.ACCEPTED, 4L),
                new TopicVerdictCount(data.dp.getId(), Verdict.WRONG_ANSWER, 2L),
                new TopicVerdictCount(data.dp.getId(), Verdict.ACCEPTED, 2L)));

        RevisionQueueDto queue = service.queue(USER_ID);

        // Arrays: only 2 graded (below the minimum). Strings: 1 of 5 failed (20%).
        // Dynamic Programming: 2 of 4 failed = exactly 50%, and the threshold is inclusive, so it is queued.
        assertEquals(List.of("Dynamic Programming"), queue.items().stream().map(RevisionItemDto::topic).toList());
    }

    @Test
    void overdueComesFirstThenHigherErrorRateAndBothIsReported() {
        Instant now = Instant.now();
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(
                row(data.arrays, 30, now.minus(Duration.ofDays(1))),
                row(data.strings, 30, now.minus(Duration.ofDays(5))),
                row(data.dp, 30, now.plus(Duration.ofDays(1)))));
        when(submissionRepository.countVerdictsByTopic(USER_ID)).thenReturn(List.of(
                new TopicVerdictCount(data.arrays.getId(), Verdict.RUNTIME_ERROR, 3L),
                new TopicVerdictCount(data.dp.getId(), Verdict.TIME_LIMIT_EXCEEDED, 4L)));

        RevisionQueueDto queue = service.queue(USER_ID);

        assertEquals(List.of("Strings", "Arrays", "Dynamic Programming"),
                queue.items().stream().map(RevisionItemDto::topic).toList());
        assertEquals(RevisionTrigger.SCHEDULED, queue.items().get(0).trigger());
        assertEquals(RevisionTrigger.BOTH, queue.items().get(1).trigger());
        assertEquals(RevisionTrigger.HIGH_ERROR_RATE, queue.items().get(2).trigger());
    }

    @Test
    void topicWithoutADueDateAndNoErrorsIsNeverQueued() {
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(row(data.arrays, 20, null)));

        assertEquals(0, service.queue(USER_ID).dueCount());
    }

    @Test
    void gradedCountsIgnoreCompileErrorsAndPending() {
        Map<Long, int[]> counts = RevisionService.gradedCounts(List.of(
                new TopicVerdictCount(1L, Verdict.ACCEPTED, 2L),
                new TopicVerdictCount(1L, Verdict.WRONG_ANSWER, 1L),
                new TopicVerdictCount(1L, Verdict.RUNTIME_ERROR, 1L),
                new TopicVerdictCount(1L, Verdict.TIME_LIMIT_EXCEEDED, 1L),
                new TopicVerdictCount(1L, Verdict.COMPILATION_ERROR, 7L),
                new TopicVerdictCount(1L, Verdict.PENDING, 3L)));

        assertEquals(5, counts.get(1L)[0]);
        assertEquals(3, counts.get(1L)[1]);
        assertEquals(0.0, RevisionService.errorRate(0, 0));
    }
}
