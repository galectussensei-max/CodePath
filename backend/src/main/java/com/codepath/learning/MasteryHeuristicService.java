package com.codepath.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.codepath.questions.Difficulty;
import com.codepath.submissions.Verdict;

/**
 * The transparent, deterministic mastery rules from the blueprint (section 5A). It has no state and no
 * database access, so every rule can be unit tested and explained to a learner.
 *
 * <ul>
 *   <li>First accepted solve of a question: gain = {@value #BASE_GAIN} x difficulty weight (Easy 1.0,
 *       Medium 1.5, Hard 2.0), scaled by attempt, time, hint and revision factors.</li>
 *   <li>Repeat accepted solves of an already solved question change nothing, so a score cannot be farmed.</li>
 *   <li>Wrong answer, runtime error or time limit on an unsolved question: a small penalty of
 *       {@value #FAILURE_PENALTY} x difficulty weight. Compilation errors are never penalised.</li>
 * </ul>
 */
@Service
public class MasteryHeuristicService {

    static final double BASE_GAIN = 8.0;
    static final double FAILURE_PENALTY = 1.0;
    static final double ATTEMPT_STEP = 0.15;
    static final double ATTEMPT_FLOOR = 0.5;
    static final double HINT_STEP = 0.15;
    static final double HINT_FLOOR = 0.4;
    static final double REVISION_BONUS = 1.25;

    /**
     * @param verdict           verdict of the submission being scored
     * @param difficulty        difficulty of the question
     * @param attemptNumber     1 for the first submission to this question, 2 for the second, and so on
     * @param solvedBefore      the learner already has an accepted submission for this question
     * @param timeTakenSeconds  client-reported solving time, or null when unknown
     * @param hintsUsed         AI hints the learner requested for this question
     * @param revisionDue       the topic was past its revision date (so this solve counts as revision)
     */
    public record Input(Verdict verdict, Difficulty difficulty, int attemptNumber, boolean solvedBefore,
            Integer timeTakenSeconds, int hintsUsed, boolean revisionDue) {
    }

    /** The score change plus a human-readable line for every rule that applied. */
    public record Evaluation(double delta, List<String> factors) {
    }

    public Evaluation evaluate(Input in) {
        List<String> factors = new ArrayList<>();
        double weight = difficultyWeight(in.difficulty());

        if (in.verdict() == Verdict.PENDING) {
            factors.add("Submission is still pending, so mastery is unchanged");
            return new Evaluation(0.0, factors);
        }

        if (in.verdict() == Verdict.ACCEPTED) {
            if (in.solvedBefore()) {
                factors.add("You already solved this problem earlier, so repeating it does not raise mastery again");
                return new Evaluation(0.0, factors);
            }
            double gain = BASE_GAIN * weight;
            factors.add(format("First accepted solve: %.1f base points (%s weight %.1f x %.0f)", gain,
                    in.difficulty(), weight, BASE_GAIN));

            double attemptFactor = attemptFactor(in.attemptNumber());
            if (attemptFactor < 1.0) {
                factors.add(format("Attempt %d: x%.2f", in.attemptNumber(), attemptFactor));
            }
            double timeFactor = timeFactor(in.difficulty(), in.timeTakenSeconds());
            if (timeFactor < 1.0) {
                factors.add(format("Took more than the expected time: x%.2f", timeFactor));
            }
            double hintFactor = hintFactor(in.hintsUsed());
            if (hintFactor < 1.0) {
                factors.add(format("%d hint(s) used: x%.2f", in.hintsUsed(), hintFactor));
            }
            double revisionFactor = in.revisionDue() ? REVISION_BONUS : 1.0;
            if (in.revisionDue()) {
                factors.add(format("Solved while this topic was due for revision: x%.2f", revisionFactor));
            }
            return new Evaluation(round1(gain * attemptFactor * timeFactor * hintFactor * revisionFactor), factors);
        }

        if (in.verdict() == Verdict.COMPILATION_ERROR) {
            factors.add("Compilation errors are not counted against mastery");
            return new Evaluation(0.0, factors);
        }
        if (in.solvedBefore()) {
            factors.add("You already solved this problem, so a failed re-run is not penalised");
            return new Evaluation(0.0, factors);
        }
        double penalty = FAILURE_PENALTY * weight;
        factors.add(format("%s on an unsolved problem: -%.1f (%s weight %.1f x %.0f)",
                label(in.verdict()), penalty, in.difficulty(), weight, FAILURE_PENALTY));
        return new Evaluation(round1(-penalty), factors);
    }

    public static double difficultyWeight(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1.0;
            case MEDIUM -> 1.5;
            case HARD -> 2.0;
        };
    }

    /** Expected solving time used for the time factor. */
    public static int expectedSeconds(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 900;
            case MEDIUM -> 1800;
            case HARD -> 2700;
        };
    }

    static double attemptFactor(int attemptNumber) {
        return Math.max(ATTEMPT_FLOOR, 1.0 - ATTEMPT_STEP * (Math.max(attemptNumber, 1) - 1));
    }

    /** Ratio of actual to expected time: up to 1x is full credit, up to 2x is 0.9, beyond that 0.8. */
    static double timeFactor(Difficulty difficulty, Integer timeTakenSeconds) {
        if (timeTakenSeconds == null) {
            return 1.0;
        }
        double ratio = timeTakenSeconds / (double) expectedSeconds(difficulty);
        if (ratio <= 1.0) {
            return 1.0;
        }
        return ratio <= 2.0 ? 0.9 : 0.8;
    }

    static double hintFactor(int hintsUsed) {
        return Math.max(HINT_FLOOR, 1.0 - HINT_STEP * Math.max(hintsUsed, 0));
    }

    private static String label(Verdict verdict) {
        return switch (verdict) {
            case WRONG_ANSWER -> "Wrong answer";
            case RUNTIME_ERROR -> "Runtime error";
            case TIME_LIMIT_EXCEEDED -> "Time limit exceeded";
            default -> verdict.name();
        };
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
