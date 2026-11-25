package com.codepath.learning;

public enum MasteryStatus {
    UNASSESSED,
    WEAK,
    DEVELOPING,
    STRONG;

    /** Scores below this are WEAK. */
    public static final double WEAK_BELOW = 40.0;
    /** Scores at or above this are STRONG; anything in between is DEVELOPING. */
    public static final double STRONG_FROM = 70.0;

    /** Maps an assessed score in [0, 100] to a status. UNASSESSED is never returned for a real score. */
    public static MasteryStatus fromScore(double score) {
        if (score < WEAK_BELOW) {
            return WEAK;
        }
        if (score < STRONG_FROM) {
            return DEVELOPING;
        }
        return STRONG;
    }
}
