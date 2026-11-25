package com.codepath.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.codepath.learning.MasteryHeuristicService.Evaluation;
import com.codepath.learning.MasteryHeuristicService.Input;
import com.codepath.questions.Difficulty;
import com.codepath.submissions.Verdict;

class MasteryHeuristicServiceTest {

    private static final double EPS = 1e-9;
    private final MasteryHeuristicService service = new MasteryHeuristicService();

    private static Input input(Verdict verdict, Difficulty difficulty, int attempt, boolean solvedBefore,
            Integer seconds, int hints, boolean revisionDue) {
        return new Input(verdict, difficulty, attempt, solvedBefore, seconds, hints, revisionDue);
    }

    @Test
    void firstTryEasySolveGivesFullBaseGain() {
        Evaluation result = service.evaluate(input(Verdict.ACCEPTED, Difficulty.EASY, 1, false, 300, 0, false));
        assertEquals(8.0, result.delta(), EPS);
        assertFalse(result.factors().isEmpty());
    }

    @Test
    void harderProblemsAreWorthMore() {
        assertEquals(12.0, service.evaluate(input(Verdict.ACCEPTED, Difficulty.MEDIUM, 1, false, null, 0, false)).delta(), EPS);
        assertEquals(16.0, service.evaluate(input(Verdict.ACCEPTED, Difficulty.HARD, 1, false, null, 0, false)).delta(), EPS);
    }

    @Test
    void attemptsSlowTimeAndHintsAllReduceTheGain() {
        // 12 base x 0.70 (attempt 3) x 0.8 (2.5x expected time) x 0.70 (2 hints) = 4.704
        Evaluation result = service.evaluate(
                input(Verdict.ACCEPTED, Difficulty.MEDIUM, 3, false, 4500, 2, false));
        assertEquals(4.7, result.delta(), EPS);
        assertTrue(result.factors().stream().anyMatch(f -> f.contains("Attempt 3")));
        assertTrue(result.factors().stream().anyMatch(f -> f.contains("hint")));
    }

    @Test
    void reductionFactorsHaveFloors() {
        assertEquals(0.5, MasteryHeuristicService.attemptFactor(20), EPS);
        assertEquals(0.4, MasteryHeuristicService.hintFactor(10), EPS);
        assertEquals(1.0, MasteryHeuristicService.attemptFactor(1), EPS);
        assertEquals(1.0, MasteryHeuristicService.hintFactor(0), EPS);
    }

    @Test
    void timeFactorBands() {
        assertEquals(1.0, MasteryHeuristicService.timeFactor(Difficulty.EASY, null), EPS);
        assertEquals(1.0, MasteryHeuristicService.timeFactor(Difficulty.EASY, 900), EPS);
        assertEquals(0.9, MasteryHeuristicService.timeFactor(Difficulty.EASY, 1800), EPS);
        assertEquals(0.8, MasteryHeuristicService.timeFactor(Difficulty.EASY, 1801), EPS);
    }

    @Test
    void solvingDuringRevisionAddsABonus() {
        Evaluation result = service.evaluate(input(Verdict.ACCEPTED, Difficulty.EASY, 1, false, null, 0, true));
        assertEquals(10.0, result.delta(), EPS);
    }

    @Test
    void repeatingAnAlreadySolvedProblemDoesNotFarmMastery() {
        assertEquals(0.0, service.evaluate(input(Verdict.ACCEPTED, Difficulty.HARD, 5, true, null, 0, true)).delta(), EPS);
        assertEquals(0.0, service.evaluate(input(Verdict.WRONG_ANSWER, Difficulty.HARD, 6, true, null, 0, false)).delta(), EPS);
    }

    @Test
    void failuresOnUnsolvedProblemsCostALittle() {
        assertEquals(-1.0, service.evaluate(input(Verdict.WRONG_ANSWER, Difficulty.EASY, 1, false, null, 0, false)).delta(), EPS);
        assertEquals(-1.5, service.evaluate(input(Verdict.RUNTIME_ERROR, Difficulty.MEDIUM, 1, false, null, 0, false)).delta(), EPS);
        assertEquals(-2.0, service.evaluate(input(Verdict.TIME_LIMIT_EXCEEDED, Difficulty.HARD, 1, false, null, 0, false)).delta(), EPS);
    }

    @Test
    void compilationErrorsAndPendingNeverChangeMastery() {
        assertEquals(0.0, service.evaluate(input(Verdict.COMPILATION_ERROR, Difficulty.HARD, 1, false, null, 0, false)).delta(), EPS);
        assertEquals(0.0, service.evaluate(input(Verdict.PENDING, Difficulty.HARD, 1, false, null, 0, false)).delta(), EPS);
    }
}
