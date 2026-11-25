package com.codepath.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.Topic;
import com.codepath.submissions.Submission;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;
import com.codepath.users.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long QUESTION_ID = 21L;
    private static final Long TOPIC_ID = 3L;

    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private SubmissionRepository submissionRepository;

    private LearningService service;
    private User user;
    private Topic topic;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new LearningService(topicMasteryRepository, submissionRepository, new MasteryHeuristicService());
        user = new User();
        user.setId(USER_ID);
        topic = new Topic();
        topic.setId(TOPIC_ID);
        topic.setName("Arrays");
        question = new Question();
        question.setId(QUESTION_ID);
        question.setTopic(topic);
        question.setDifficulty(Difficulty.MEDIUM);
        when(submissionRepository.countByUserIdAndQuestionIdAndIdNot(anyLong(), anyLong(), anyLong())).thenReturn(0L);
        when(submissionRepository.existsByUserIdAndQuestionIdAndVerdictAndIdNot(
                anyLong(), anyLong(), any(Verdict.class), anyLong())).thenReturn(false);
    }

    private Submission submission(Verdict verdict) {
        Submission submission = new Submission();
        submission.setId(100L);
        submission.setUser(user);
        submission.setQuestion(question);
        submission.setVerdict(verdict);
        return submission;
    }

    private TopicMastery row(double score, MasteryStatus status) {
        TopicMastery mastery = new TopicMastery();
        mastery.setUser(user);
        mastery.setTopic(topic);
        mastery.setMasteryScore(score);
        mastery.setStatus(status);
        when(topicMasteryRepository.findByUserIdAndTopicId(USER_ID, TOPIC_ID)).thenReturn(Optional.of(mastery));
        return mastery;
    }

    @Test
    void firstAcceptedSolveRaisesScoreAndCounters() {
        TopicMastery mastery = row(50.0, MasteryStatus.DEVELOPING);

        MasteryUpdate update = service.updateMastery(submission(Verdict.ACCEPTED));

        assertEquals(62.0, mastery.getMasteryScore(), 1e-9); // MEDIUM first try: +12
        assertEquals(MasteryStatus.DEVELOPING, mastery.getStatus());
        assertEquals(1, mastery.getAttemptsCount());
        assertEquals(1, mastery.getSolvedCount());
        assertNotNull(mastery.getLastPracticedAt());
        assertTrue(mastery.getNextRevisionDue().isAfter(Instant.now().plus(Duration.ofDays(2))));
        verify(topicMasteryRepository).save(mastery);
        assertEquals(50.0, update.previousScore(), 1e-9);
        assertEquals(62.0, update.newScore(), 1e-9);
        assertEquals(12.0, update.change(), 1e-9);
        assertEquals("Arrays", update.topicName());
        assertTrue(!update.factors().isEmpty());
    }

    @Test
    void crossingTheThresholdChangesStatus() {
        TopicMastery mastery = row(60.0, MasteryStatus.DEVELOPING);

        MasteryUpdate update = service.updateMastery(submission(Verdict.ACCEPTED));

        assertEquals(72.0, mastery.getMasteryScore(), 1e-9);
        assertEquals(MasteryStatus.STRONG, update.newStatus());
        assertEquals(MasteryStatus.DEVELOPING, update.previousStatus());
    }

    @Test
    void scoreIsClampedAtOneHundred() {
        TopicMastery mastery = row(95.0, MasteryStatus.STRONG);
        service.updateMastery(submission(Verdict.ACCEPTED));
        assertEquals(100.0, mastery.getMasteryScore(), 1e-9);
    }

    @Test
    void failureLowersScoreButNeverBelowZero() {
        TopicMastery mastery = row(0.5, MasteryStatus.WEAK);
        service.updateMastery(submission(Verdict.WRONG_ANSWER));
        assertEquals(0.0, mastery.getMasteryScore(), 1e-9);
        assertEquals(1, mastery.getAttemptsCount());
        assertEquals(0, mastery.getSolvedCount());
    }

    @Test
    void missingRowIsCreatedForTheTopic() {
        when(topicMasteryRepository.findByUserIdAndTopicId(USER_ID, TOPIC_ID)).thenReturn(Optional.empty());

        MasteryUpdate update = service.updateMastery(submission(Verdict.ACCEPTED));

        ArgumentCaptor<TopicMastery> saved = ArgumentCaptor.forClass(TopicMastery.class);
        verify(topicMasteryRepository).save(saved.capture());
        assertEquals(TOPIC_ID, saved.getValue().getTopic().getId());
        assertEquals(12.0, saved.getValue().getMasteryScore(), 1e-9);
        assertEquals(MasteryStatus.UNASSESSED, update.previousStatus());
        assertEquals(MasteryStatus.WEAK, update.newStatus());
    }

    @Test
    void repeatSolveOfSolvedQuestionDoesNotIncreaseSolvedCountOrScore() {
        when(submissionRepository.existsByUserIdAndQuestionIdAndVerdictAndIdNot(
                eq(USER_ID), eq(QUESTION_ID), eq(Verdict.ACCEPTED), anyLong())).thenReturn(true);
        when(submissionRepository.countByUserIdAndQuestionIdAndIdNot(USER_ID, QUESTION_ID, 100L)).thenReturn(1L);
        TopicMastery mastery = row(40.0, MasteryStatus.DEVELOPING);
        mastery.setSolvedCount(1);
        mastery.setAttemptsCount(1);

        service.updateMastery(submission(Verdict.ACCEPTED));

        assertEquals(40.0, mastery.getMasteryScore(), 1e-9);
        assertEquals(1, mastery.getSolvedCount());
        assertEquals(2, mastery.getAttemptsCount());
    }

    @Test
    void solvingWhileRevisionIsDueEarnsTheRevisionBonus() {
        TopicMastery mastery = row(30.0, MasteryStatus.WEAK);
        mastery.setNextRevisionDue(Instant.now().minus(Duration.ofDays(1)));

        service.updateMastery(submission(Verdict.ACCEPTED));

        assertEquals(45.0, mastery.getMasteryScore(), 1e-9); // 12 x 1.25 = 15
    }

    @Test
    void revisionIntervalsGrowWithStatus() {
        assertEquals(Duration.ofDays(1), LearningService.revisionInterval(MasteryStatus.WEAK));
        assertEquals(Duration.ofDays(3), LearningService.revisionInterval(MasteryStatus.DEVELOPING));
        assertEquals(Duration.ofDays(7), LearningService.revisionInterval(MasteryStatus.STRONG));
    }
}
