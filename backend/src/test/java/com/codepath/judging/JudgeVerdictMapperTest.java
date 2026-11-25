package com.codepath.judging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.submissions.Verdict;

class JudgeVerdictMapperTest {

    @Test
    void mapsJudge0StatusesToVerdicts() {
        assertEquals(Verdict.PENDING, JudgeVerdictMapper.toVerdict(1));
        assertEquals(Verdict.PENDING, JudgeVerdictMapper.toVerdict(2));
        assertEquals(Verdict.ACCEPTED, JudgeVerdictMapper.toVerdict(3));
        assertEquals(Verdict.WRONG_ANSWER, JudgeVerdictMapper.toVerdict(4));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, JudgeVerdictMapper.toVerdict(5));
        assertEquals(Verdict.COMPILATION_ERROR, JudgeVerdictMapper.toVerdict(6));
    }

    @Test
    void allRuntimeFailureStatusesAreRuntimeErrors() {
        for (int id = 7; id <= 12; id++) {
            assertEquals(Verdict.RUNTIME_ERROR, JudgeVerdictMapper.toVerdict(id), "status " + id);
        }
    }

    @Test
    void judgeInternalFailuresAreNotVerdicts() {
        assertThrows(ServiceUnavailableException.class, () -> JudgeVerdictMapper.toVerdict(13));
        assertThrows(ServiceUnavailableException.class, () -> JudgeVerdictMapper.toVerdict(14));
        assertThrows(ServiceUnavailableException.class, () -> JudgeVerdictMapper.toVerdict(99));
    }

    @Test
    void finishedMeansPastProcessing() {
        assertFalse(JudgeVerdictMapper.isFinished(1));
        assertFalse(JudgeVerdictMapper.isFinished(2));
        assertTrue(JudgeVerdictMapper.isFinished(3));
        assertTrue(JudgeVerdictMapper.isFinished(13));
    }
}
