package com.example.attendanceportal;

import java.util.List;

/** Pure helpers for the Marks screen, kept free of Android types so they can be unit tested. */
public final class MarksMath {
    private MarksMath() { }

    public static final int CIE_MAX = 50;

    /** Status band for a final CIE score out of 50. */
    public enum Band {
        STRONG("Strong"), OKAY("Okay"), NEEDS_WORK("Needs work");
        public final String label;
        Band(String label) { this.label = label; }
        public boolean isGood() { return this != NEEDS_WORK; }
    }

    public static Band band(int finalCie) {
        if (finalCie >= 40) return Band.STRONG;
        if (finalCie >= 25) return Band.OKAY;
        return Band.NEEDS_WORK;
    }

    /** Average, best and weakest of a list of final CIE scores. Null when the list is empty. */
    public static final class Summary {
        public final double average;
        public final int bestIndex, weakestIndex;
        Summary(double average, int bestIndex, int weakestIndex) {
            this.average = average;
            this.bestIndex = bestIndex;
            this.weakestIndex = weakestIndex;
        }
    }

    public static Summary summarize(List<Integer> finals) {
        if (finals == null || finals.isEmpty()) return null;
        int best = 0, weakest = 0;
        long sum = 0;
        for (int i = 0; i < finals.size(); i++) {
            int v = finals.get(i);
            sum += v;
            if (v > finals.get(best)) best = i;
            if (v < finals.get(weakest)) weakest = i;
        }
        return new Summary((double) sum / finals.size(), best, weakest);
    }
}
