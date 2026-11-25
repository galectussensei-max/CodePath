package com.codepath.judging;

/** One stdin / expected stdout pair to run a program against. */
public record JudgeTestCase(String input, String expectedOutput) {
}
