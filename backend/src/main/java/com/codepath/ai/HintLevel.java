package com.codepath.ai;

import com.codepath.common.ConflictException;

/** The four scaffolding levels of blueprint section 5B, mapped to their {@link InteractionType}. */
public enum HintLevel {
    CONCEPTUAL_NUDGE(1, InteractionType.HINT_L1),
    LOGICAL_GUIDANCE(2, InteractionType.HINT_L2),
    ALGORITHMIC_STEPS(3, InteractionType.HINT_L3),
    PARTIAL_CLOZE(4, InteractionType.HINT_L4);

    private final int number;
    private final InteractionType interactionType;

    HintLevel(int number, InteractionType interactionType) {
        this.number = number;
        this.interactionType = interactionType;
    }

    public int number() {
        return number;
    }

    public InteractionType interactionType() {
        return interactionType;
    }

    /** The level below this one, or null for Level 1. */
    public HintLevel previous() {
        return number == 1 ? null : of(number - 1);
    }

    public static HintLevel of(int number) {
        for (HintLevel level : values()) {
            if (level.number == number) {
                return level;
            }
        }
        throw new IllegalArgumentException("hintLevel must be between 1 and 4");
    }

    /** Same as {@link #of(int)} but reports a missing level as a conflict-free bad request. */
    public static HintLevel require(Integer number) {
        if (number == null) {
            throw new IllegalArgumentException("hintLevel is required");
        }
        return of(number);
    }

    /** Used when a learner skips ahead of the progressive order. */
    public ConflictException skippedException() {
        return new ConflictException("Request the Level " + (number - 1) + " hint for this question first");
    }
}
