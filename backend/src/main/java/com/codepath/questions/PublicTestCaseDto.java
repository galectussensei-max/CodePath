package com.codepath.questions;

import java.util.Collection;
import java.util.List;

/**
 * Client-facing view of a test case. It deliberately has no "hidden" field, and
 * {@link #from(TestCase)} refuses hidden cases, so a hidden case cannot be
 * serialized to the frontend by mistake.
 */
public record PublicTestCaseDto(Long id, String input, String expectedOutput) {

    public static PublicTestCaseDto from(TestCase testCase) {
        if (testCase.isHidden()) {
            throw new IllegalArgumentException("Hidden test cases must never be mapped to a client DTO");
        }
        return new PublicTestCaseDto(testCase.getId(), testCase.getInputData(), testCase.getExpectedOutput());
    }

    /** Maps only the public cases and silently drops hidden ones. */
    public static List<PublicTestCaseDto> publicOnly(Collection<TestCase> testCases) {
        return testCases.stream()
                .filter(testCase -> !testCase.isHidden())
                .map(PublicTestCaseDto::from)
                .toList();
    }
}
