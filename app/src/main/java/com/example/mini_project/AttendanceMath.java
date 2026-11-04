package com.example.mini_project;

/** Pure calculation logic, easy to test and explain in the viva. */
public class AttendanceMath {

    public static double percentage(int attended, int conducted) {
        if (conducted <= 0) return 0;
        return attended * 100.0 / conducted;
    }

    /** Classes to attend IN A ROW to reach target t (e.g. 0.85). n = ceil((t*H - A) / (1 - t)) */
    public static int classesNeeded(int attended, int conducted, double t) {
        if (percentage(attended, conducted) >= t * 100) return 0;
        return (int) Math.ceil((t * conducted - attended) / (1 - t));
    }

    /** Max classes you can still miss and stay at or above t. x = floor(A/t - H) */
    public static int canMiss(int attended, int conducted, double t) {
        int x = (int) Math.floor(attended / t - conducted);
        return Math.max(x, 0);
    }

    /** True only if the input makes sense. */
    public static boolean isValid(int attended, int conducted) {
        return attended >= 0 && conducted > 0 && attended <= conducted;
    }
}
