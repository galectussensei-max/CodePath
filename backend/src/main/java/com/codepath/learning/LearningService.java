package com.codepath.learning;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.questions.Question;
import com.codepath.questions.Topic;
import com.codepath.submissions.Submission;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;

import lombok.RequiredArgsConstructor;

/**
 * Applies the mastery heuristic to a graded submission and saves the learner's {@link TopicMastery} row.
 * Called by the submissions module after a SUBMIT (never after a RUN).
 */
@Service
@RequiredArgsConstructor
public class LearningService {

    /** Days until a topic is due for revision after practice, by resulting status. */
    static final Duration WEAK_REVISION_INTERVAL = Duration.ofDays(1);
    static final Duration DEVELOPING_REVISION_INTERVAL = Duration.ofDays(3);
    static final Duration STRONG_REVISION_INTERVAL = Duration.ofDays(7);

    private final TopicMasteryRepository topicMasteryRepository;
    private final SubmissionRepository submissionRepository;
    private final MasteryHeuristicService heuristicService;

    /**
     * Updates the mastery row for the submission's topic. The submission must already be saved (it needs an
     * id) so that its own row is excluded when counting earlier attempts. Joins the caller's transaction.
     */
    @Transactional
    public MasteryUpdate updateMastery(Submission submission) {
        Long userId = submission.getUser().getId();
        Question question = submission.getQuestion();
        Topic topic = question.getTopic();

        long earlierAttempts = submissionRepository.countByUserIdAndQuestionIdAndIdNot(
                userId, question.getId(), submission.getId());
        boolean solvedBefore = submissionRepository.existsByUserIdAndQuestionIdAndVerdictAndIdNot(
                userId, question.getId(), Verdict.ACCEPTED, submission.getId());

        TopicMastery mastery = topicMasteryRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> {
                    TopicMastery created = new TopicMastery();
                    created.setUser(submission.getUser());
                    created.setTopic(topic);
                    return created;
                });

        Instant now = Instant.now();
        boolean revisionDue = mastery.getNextRevisionDue() != null && mastery.getNextRevisionDue().isBefore(now);
        double previousScore = mastery.getMasteryScore();
        MasteryStatus previousStatus = mastery.getStatus();

        MasteryHeuristicService.Evaluation evaluation = heuristicService.evaluate(new MasteryHeuristicService.Input(
                submission.getVerdict(), question.getDifficulty(), (int) earlierAttempts + 1, solvedBefore,
                submission.getTimeTakenSeconds(), submission.getHintsUsedCount(), revisionDue));

        double newScore = Math.round(clamp(previousScore + evaluation.delta()) * 10.0) / 10.0;
        MasteryStatus newStatus = MasteryStatus.fromScore(newScore);

        mastery.setMasteryScore(newScore);
        mastery.setStatus(newStatus);
        mastery.setAttemptsCount(mastery.getAttemptsCount() + 1);
        if (submission.getVerdict() == Verdict.ACCEPTED && !solvedBefore) {
            mastery.setSolvedCount(mastery.getSolvedCount() + 1);
        }
        mastery.setLastPracticedAt(now);
        mastery.setNextRevisionDue(now.plus(revisionInterval(newStatus)));
        topicMasteryRepository.save(mastery);

        return new MasteryUpdate(topic.getId(), topic.getName(), previousScore, newScore,
                Math.round((newScore - previousScore) * 10.0) / 10.0, previousStatus, newStatus,
                evaluation.factors(), MasteryUpdate.ESTIMATE_NOTE);
    }

    static Duration revisionInterval(MasteryStatus status) {
        return switch (status) {
            case WEAK, UNASSESSED -> WEAK_REVISION_INTERVAL;
            case DEVELOPING -> DEVELOPING_REVISION_INTERVAL;
            case STRONG -> STRONG_REVISION_INTERVAL;
        };
    }

    private static double clamp(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }
}
