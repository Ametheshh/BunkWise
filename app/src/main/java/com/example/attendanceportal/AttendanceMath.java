package com.example.attendanceportal;

/** Integer arithmetic only, so exactly 75.0% is never lost to rounding. t is the threshold in percent. */
public final class AttendanceMath {
    private AttendanceMath() { }

    public static boolean isEligible(int attended, int conducted, int t) {
        return 100 * attended >= t * conducted;
    }

    /** Classes to attend IN A ROW to reach t%:  n = ceil((t*H - 100*A) / (100 - t)). */
    public static int classesNeeded(int attended, int conducted, int t) {
        if (isEligible(attended, conducted, t)) return 0;
        int num = t * conducted - 100 * attended;
        int den = 100 - t;
        return (num + den - 1) / den;
    }

    /** Most classes that can still be missed while staying at or above t%. */
    public static int canMiss(int attended, int conducted, int t) {
        if (conducted == 0 || !isEligible(attended, conducted, t)) return 0;
        return (100 * attended - t * conducted) / t;
    }
}
