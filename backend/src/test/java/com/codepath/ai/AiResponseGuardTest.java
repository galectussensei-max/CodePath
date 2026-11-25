package com.codepath.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiResponseGuardTest {

    private static final String CLOZE = """
            Fill in the blanks:
            ```java
            public int solve(int[] nums, int target) {
                int left = ____;
                int right = ____;
                while (left < right) {
                    int sum = ____;
                    if (sum == target) return ____;
                }
                return -1;
            }
            ```
            Each blank needs an expression.
            """;

    @Test
    void acceptsPlainConceptualHintAtLevelOne() {
        assertNull(AiResponseGuard.violation(InteractionType.HINT_L1,
                "This looks like a two pointers problem because the array is sorted."));
    }

    @Test
    void rejectsCodeBlocksAtLevelsOneToThreeAndReview() {
        String withFence = "Try this:\n```java\nint x = 1;\n```";
        for (InteractionType type : new InteractionType[] {InteractionType.HINT_L1, InteractionType.HINT_L2,
                InteractionType.HINT_L3, InteractionType.AC_REVIEW}) {
            assertNotNull(AiResponseGuard.violation(type, withFence), type.name());
        }
    }

    @Test
    void rejectsJavaMarkersEvenWithoutFences() {
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L3,
                "public static void main(String[] args) { }"));
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L2, "Use System.out.println(x)."));
    }

    @Test
    void rejectsEmptyAndOversizedReplies() {
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L1, "  "));
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L1, null));
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L1,
                "x".repeat(AiResponseGuard.MAX_REPLY_CHARS + 1)));
    }

    @Test
    void acceptsAClozeSkeletonWithBlanks() {
        assertNull(AiResponseGuard.violation(InteractionType.HINT_L4, CLOZE));
    }

    @Test
    void rejectsLevelFourWithoutACodeBlock() {
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L4, "Loop with two pointers."));
    }

    @Test
    void rejectsLevelFourWithTwoCodeBlocks() {
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L4, CLOZE + "\n" + CLOZE));
    }

    @Test
    void rejectsACompleteSolutionAtLevelFour() {
        String full = """
                ```java
                public int solve(int[] nums, int target) {
                    int left = 0;
                    int right = nums.length - 1;
                    while (left < right) {
                        int sum = nums[left] + nums[right];
                        if (sum == target) return left;
                        if (sum < target) left++;
                        else right--;
                    }
                    return -1;
                }
                ```
                """;
        String reason = AiResponseGuard.violation(InteractionType.HINT_L4, full);
        assertNotNull(reason);
        assertTrue(reason.contains("blank"));
    }

    @Test
    void rejectsLevelFourWithTooFewBlanks() {
        String almost = "```java\nint a = 1;\nint b = ____;\nint c = 3;\nint d = 4;\n```";
        assertNotNull(AiResponseGuard.violation(InteractionType.HINT_L4, almost));
    }

    @Test
    void acceptsAPlainTextReview() {
        assertNull(AiResponseGuard.violation(InteractionType.AC_REVIEW,
                "Time complexity is O(n) and space is O(1). A HashMap approach is an alternative."));
    }
}
