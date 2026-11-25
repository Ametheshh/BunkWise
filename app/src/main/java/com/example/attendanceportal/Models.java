package com.example.attendanceportal;

/** Plain data holders returned by AttendanceRepo. */
public final class Models {
    private Models() { }

    public static class Student {
        public long id;
        public String usn, name, email, labBatch, salt, hash;
        public boolean passwordChanged;
        public int failedLogins;
        public long lockedUntil;
    }

    public static class SubjectSummary {
        public String code, name, type, eligibility;
        public int conducted, attended, needed, canMiss;
        public double pct;
        public boolean hasClasses() { return conducted > 0; }
        public boolean isShortage() { return conducted > 0 && "Shortage".equals(eligibility); }
    }

    public static class Overall {
        public int conducted, attended, needed;
        public double pct;
        public String eligibility;
        public boolean isShortage() { return "Shortage".equals(eligibility); }
    }

    public static class HistoryItem {
        public String date, slot, subjectName, subjectCode, status;
        public boolean isPresent() { return "Present".equals(status); }
    }

    public static class MonthStat {
        public String month;
        public int conducted, attended;
        public double pct() { return conducted == 0 ? 0 : 100.0 * attended / conducted; }
    }
}
