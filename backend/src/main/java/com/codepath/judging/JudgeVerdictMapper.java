package com.codepath.judging;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.submissions.Verdict;

/** Translates Judge0 status ids into CodePath {@link Verdict}s. */
public final class JudgeVerdictMapper {

    public static final int IN_QUEUE = 1;
    public static final int PROCESSING = 2;
    public static final int ACCEPTED = 3;
    public static final int WRONG_ANSWER = 4;
    public static final int TIME_LIMIT_EXCEEDED = 5;
    public static final int COMPILATION_ERROR = 6;
    /** Judge0 uses 7 to 12 for the different runtime failures (SIGSEGV, SIGFPE, NZEC, and so on). */
    public static final int RUNTIME_ERROR_FIRST = 7;
    public static final int RUNTIME_ERROR_LAST = 12;
    public static final int INTERNAL_ERROR = 13;
    public static final int EXEC_FORMAT_ERROR = 14;

    private JudgeVerdictMapper() {
    }

    /** True once Judge0 has finished with the submission (it is no longer queued or processing). */
    public static boolean isFinished(int statusId) {
        return statusId > PROCESSING;
    }

    /**
     * @throws ServiceUnavailableException for Judge0 internal errors and unknown ids: those say nothing
     *                                     about the student's code, so they must not become a verdict
     */
    public static Verdict toVerdict(int statusId) {
        return switch (statusId) {
            case IN_QUEUE, PROCESSING -> Verdict.PENDING;
            case ACCEPTED -> Verdict.ACCEPTED;
            case WRONG_ANSWER -> Verdict.WRONG_ANSWER;
            case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
            case COMPILATION_ERROR -> Verdict.COMPILATION_ERROR;
            default -> {
                if (statusId >= RUNTIME_ERROR_FIRST && statusId <= RUNTIME_ERROR_LAST) {
                    yield Verdict.RUNTIME_ERROR;
                }
                throw new ServiceUnavailableException(
                        "The code judge reported an internal error (status " + statusId + "). Please try again.");
            }
        };
    }
}
