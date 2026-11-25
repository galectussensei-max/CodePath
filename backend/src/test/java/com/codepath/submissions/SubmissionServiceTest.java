package com.codepath.submissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.judging.Judge0Service;
import com.codepath.judging.JudgeCaseResult;
import com.codepath.judging.JudgeTestCase;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceTest {

    @Mock
    private SubmissionStore store;
    @Mock
    private Judge0Service judge0Service;

    private SubmissionService service;

    private final JudgeJob publicOnly = new JudgeJob(1L, 2000, 262144, List.of(
            new JudgeJob.JobCase(1L, false, "in-public", "out-public")));
    private final JudgeJob withHidden = new JudgeJob(1L, 2000, 262144, List.of(
            new JudgeJob.JobCase(1L, false, "in-public", "out-public"),
            new JudgeJob.JobCase(2L, true, "in-hidden", "out-hidden")));

    @BeforeEach
    void setUp() {
        service = new SubmissionService(store, judge0Service);
    }

    private static JudgeCaseResult accepted() {
        return new JudgeCaseResult(Verdict.ACCEPTED, 3, "Accepted", 30, 1000, "out", null, null, null);
    }

    @Test
    void runUsesPublicCasesOnlyAndSavesNothing() {
        when(store.loadJob(1L, false)).thenReturn(publicOnly);
        when(judge0Service.judge(anyString(), anyList(), anyInt(), anyInt())).thenReturn(List.of(accepted()));

        SubmissionResultDto result = service.run(new RunRequest(1L, "code"));

        assertEquals("RUN", result.mode());
        assertEquals(Verdict.ACCEPTED, result.verdict());
        assertNull(result.submissionId());
        assertNull(result.masteryUpdate());
        verify(store, never()).loadJob(1L, true);
        verify(store, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void submitRunsPublicAndHiddenCasesThenSavesTheResult() {
        when(store.loadJob(1L, true)).thenReturn(withHidden);
        when(judge0Service.judge(anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(accepted(), accepted()));
        SubmissionResultDto saved = new SubmissionResultDto("SUBMIT", 9L, Verdict.ACCEPTED, 30, 1000, 2, 2, null,
                List.of(), null, null);
        when(store.record(eq(5L), eq(withHidden), eq("code"), eq(120), any(JudgeOutcome.class))).thenReturn(saved);

        SubmissionResultDto result = service.submit(5L, new SubmitRequest(1L, "code", 120));

        assertSame(saved, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JudgeTestCase>> cases = ArgumentCaptor.forClass(List.class);
        verify(judge0Service).judge(eq("code"), cases.capture(), eq(2000), eq(262144));
        assertEquals(List.of(new JudgeTestCase("in-public", "out-public"), new JudgeTestCase("in-hidden", "out-hidden")),
                cases.getValue());
        ArgumentCaptor<JudgeOutcome> outcome = ArgumentCaptor.forClass(JudgeOutcome.class);
        verify(store).record(eq(5L), eq(withHidden), eq("code"), eq(120), outcome.capture());
        assertEquals(2, outcome.getValue().passedCount());
    }

    @Test
    void aFailedSubmissionIsStillRecordedWithItsVerdict() {
        when(store.loadJob(1L, true)).thenReturn(withHidden);
        when(judge0Service.judge(anyString(), anyList(), anyInt(), anyInt())).thenReturn(List.of(accepted(),
                new JudgeCaseResult(Verdict.WRONG_ANSWER, 4, "Wrong Answer", 20, 900, "bad", null, null, null)));

        service.submit(5L, new SubmitRequest(1L, "code", null));

        ArgumentCaptor<JudgeOutcome> outcome = ArgumentCaptor.forClass(JudgeOutcome.class);
        verify(store).record(eq(5L), eq(withHidden), eq("code"), eq(null), outcome.capture());
        assertEquals(Verdict.WRONG_ANSWER, outcome.getValue().verdict());
        assertEquals(1, outcome.getValue().passedCount());
    }

    @Test
    void historyIsDelegatedToTheStore() {
        List<SubmissionHistoryDto> history = List.of();
        when(store.history(5L, 1L)).thenReturn(history);
        assertSame(history, service.history(5L, 1L));
    }
}
