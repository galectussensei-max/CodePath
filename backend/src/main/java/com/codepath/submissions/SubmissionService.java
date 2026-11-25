package com.codepath.submissions;

import java.util.List;

import org.springframework.stereotype.Service;

import com.codepath.judging.JudgeCaseResult;
import com.codepath.judging.JudgeTestCase;
import com.codepath.judging.Judge0Service;

import lombok.RequiredArgsConstructor;

/**
 * Submission lifecycle: load the question's cases, run the code in Judge0, then (for SUBMIT only) save the
 * result and update mastery. This class is deliberately not transactional; see {@link SubmissionStore}.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionStore store;
    private final Judge0Service judge0Service;

    /** Runs against PUBLIC cases only. Nothing is saved and mastery is not touched. */
    public SubmissionResultDto run(RunRequest request) {
        JudgeJob job = store.loadJob(request.questionId(), false);
        JudgeOutcome outcome = execute(job, request.sourceCode());
        return SubmissionResultDto.of("RUN", null, null, outcome, null);
    }

    /** Runs against public and hidden cases, saves a Submission and updates topic mastery. */
    public SubmissionResultDto submit(Long userId, SubmitRequest request) {
        JudgeJob job = store.loadJob(request.questionId(), true);
        JudgeOutcome outcome = execute(job, request.sourceCode());
        return store.record(userId, job, request.sourceCode(), request.timeTakenSeconds(), outcome);
    }

    /** The learner's own past submissions for a question, newest first. */
    public List<SubmissionHistoryDto> history(Long userId, Long questionId) {
        return store.history(userId, questionId);
    }

    private JudgeOutcome execute(JudgeJob job, String sourceCode) {
        List<JudgeTestCase> cases = job.cases().stream()
                .map(testCase -> new JudgeTestCase(testCase.input(), testCase.expectedOutput()))
                .toList();
        List<JudgeCaseResult> results = judge0Service.judge(sourceCode, cases, job.timeLimitMs(),
                job.memoryLimitKb());
        return JudgeOutcome.of(job, results);
    }
}
