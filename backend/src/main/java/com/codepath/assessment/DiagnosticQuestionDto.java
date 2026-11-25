package com.codepath.assessment;

import java.util.List;

/**
 * Client-facing diagnostic question. It carries only the prompt and the A-D options: the answer key
 * (the HIDDEN test case) is never mapped into this DTO.
 */
public record DiagnosticQuestionDto(
        Long id,
        String title,
        String topic,
        String difficulty,
        String prompt,
        List<Option> options) {

    public record Option(String label, String text) {
    }
}
