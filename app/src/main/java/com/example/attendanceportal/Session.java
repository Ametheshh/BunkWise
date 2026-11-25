package com.example.attendanceportal;

import android.os.SystemClock;

/**
 * The only place that says "who is logged in". Every data query in AttendanceRepo takes the student
 * from here, never from an Intent extra or user input, so one student cannot ask for another's data.
 * Lives in memory only: killing the app or 15 idle minutes ends it.
 */
public final class Session {
    static final long IDLE_MS = 15 * 60 * 1000L;
    private static long accountId = -1;
    enum Role {
        STUDENT("Student", "students"),
        TEACHER("Teacher", "teachers"),
        ADMIN("System admin", "admins");

        private final String label;
        final String table;

        Role(String label, String table) {
            this.label = label;
            this.table = table;
        }

        @Override public String toString() {
            return label;
        }
    }

    private static Role role;
    private static long generation;
    private static long lastTouch = 0;

    private Session() { }

    static synchronized void start(long id, Role accountRole) {
        accountId = id;
        role = accountRole;
        generation++;
        touch();
    }

    public static synchronized boolean isTeacher() {
        return hasRole(Role.TEACHER);
    }

    static synchronized boolean hasRole(Role expected) {
        return isActive() && role == expected;
    }

    static synchronized Role requireRole() {
        if (!isActive()) throw new IllegalStateException("No active session");
        return role;
    }

    static synchronized long requireAccountId(Role expected) {
        if (!hasRole(expected)) throw new IllegalStateException("Wrong account role");
        return accountId;
    }

    static synchronized long generation() {
        if (!isActive()) throw new IllegalStateException("No active session");
        return generation;
    }

    static synchronized long requireTeacherId() {
        if (!isTeacher()) throw new IllegalStateException("Teacher session required");
        return accountId;
    }

    public static synchronized void touch() {
        lastTouch = SystemClock.elapsedRealtime();
    }

    public static synchronized boolean isActive() {
        if (accountId < 0) return false;
        if (SystemClock.elapsedRealtime() - lastTouch >= IDLE_MS) {
            end();
            return false;
        }
        return true;
    }

    public static synchronized long requireStudentId() {
        return requireAccountId(Role.STUDENT);
    }

    public static synchronized void end() {
        accountId = -1;
        generation++;
    }
}
