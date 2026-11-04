package com.example.mini_project;

import java.util.List;

/** Pure calculation logic for SGPA, CGPA and the target planner. */
public class CgpaMath {

    public static boolean validCredits(double c) { return c > 0 && c <= 10; }

    public static boolean validGradePoint(double g) { return g >= 0 && g <= 10; }

    /** CGPA = sum(credits x grade points of all semesters) / sum(credits). Returns -1 if no data. */
    public static double cgpa(List<Semester> list) {
        double c = 0, w = 0;
        for (Semester s : list) { c += s.credits; w += s.weighted; }
        return c == 0 ? -1 : w / c;
    }

    /** SGPA needed next semester: (target*(C+N) - cgpa*C) / N */
    public static double requiredSgpa(double cgpa, double doneCredits, double target, double nextCredits) {
        return (target * (doneCredits + nextCredits) - cgpa * doneCredits) / nextCredits;
    }

    /** Highest CGPA reachable next semester (scoring a perfect 10). */
    public static double bestPossibleCgpa(double cgpa, double doneCredits, double nextCredits) {
        return (cgpa * doneCredits + 10 * nextCredits) / (doneCredits + nextCredits);
    }
}
