package com.codepath.submissions;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.ai.AiInteractionRepository;
import com.codepath.ai.InteractionType;
import com.codepath.common.ConflictException;
import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;
import com.codepath.learning.LearningService;
import com.codepath.learning.MasteryUpdate;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TestCase;
import com.codepath.questions.TestCaseRepository;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The short database transactions around a judging run. They are kept apart from
 * {@link SubmissionService} so that no transaction (and no connection) stays open while Judge0 is
 * executing code, and so Spring's transaction proxy applies to every call.
 */
@Service
@RequiredArgsConstructor
public class SubmissionStore {

    private static final List<InteractionType> HINT_TYPES = List.of(
            InteractionType.HINT_L1, InteractionType.HINT_L2, InteractionType.HINT_L3, InteractionType.HINT_L4);

    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final LearningService learningService;

    /** Loads the question's limits and test cases; public cases come first. Diagnostic questions look missing. */
    @Transactional(readOnly = true)
    public JudgeJob loadJob(Long questionId, boolean includeHidden) {
        Question question = findPracticeQuestion(questionId);
        List<JudgeJob.JobCase> cases = testCaseRepository.findByQuestionId(question.getId()).stream()
                .filter(testCase -> includeHidden || !testCase.isHidden())
                .sorted(Comparator.comparing(TestCase::isHidden).thenComparing(TestCase::getId))
                .map(testCase -> new JudgeJob.JobCase(testCase.getId(), testCase.isHidden(),
                        testCase.getInputData(), testCase.getExpectedOutput()))
                .toList();
        if (cases.isEmpty()) {
            throw new ConflictException("This question has no test cases configured yet");
        }
        return new JudgeJob(question.getId(), question.getTimeLimitMs(), question.getMemoryLimitKb(), cases);
    }

    /** Saves the verified result as a Submission and updates topic mastery in the same transaction. */
    @Transactional
    public SubmissionResultDto record(Long userId, JudgeJob job, String sourceCode, Integer timeTakenSeconds,
            JudgeOutcome outcome) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Question question = findPracticeQuestion(job.questionId());

        Submission submission = new Submission();
        submission.setUser(user);
        submission.setQuestion(question);
        submission.setSourceCode(sourceCode);
        submission.setLanguage(Language.JAVA);
        submission.setVerdict(outcome.verdict());
        submission.setExecutionTimeMs(outcome.executionTimeMs());
        submission.setMemoryUsedKb(outcome.memoryUsedKb());
        submission.setTimeTakenSeconds(timeTakenSeconds);
        submission.setHintsUsedCount(
                (int) aiInteractionRepository.countByUserIdAndQuestionIdAndInteractionTypeIn(
                        userId, question.getId(), HINT_TYPES));
        submission.setJudgeOutputLog(outcome.log());
        Submission saved = submissionRepository.save(submission);

        MasteryUpdate masteryUpdate = learningService.updateMastery(saved);
        return SubmissionResultDto.of("SUBMIT", saved.getId(), saved.getCreatedAt(), outcome, masteryUpdate);
    }

    @Transactional(readOnly = true)
    public List<SubmissionHistoryDto> history(Long userId, Long questionId) {
        Question question = findPracticeQuestion(questionId);
        return submissionRepository.findByUserIdAndQuestionIdOrderByCreatedAtDesc(userId, question.getId()).stream()
                .map(submission -> SubmissionHistoryDto.from(submission, question.getId()))
                .toList();
    }

    private Question findPracticeQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .filter(question -> !DataSeeder.DIAGNOSTIC_TAG.equals(question.getPlacementTags()))
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
    }
}
