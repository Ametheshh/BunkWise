package com.example.attendanceportal;

import android.os.SystemClock;

/**
 * The only place that says "who is logged in". Every data query in AttendanceRepo takes the student
 * from here, never from an Intent extra or user input, so one student cannot ask for another's data.
 * Lives in memory only: killing the app or 15 idle minutes ends it.
 */
public final class Session {
    private static final long IDLE_MS = 15 * 60 * 1000L;
    private static long studentId = -1;
    private static long lastTouch = 0;

    private Session() { }

    public static synchronized void start(long id) {
        studentId = id;
        touch();
    }

    public static synchronized void touch() {
        lastTouch = SystemClock.elapsedRealtime();
    }

    public static synchronized boolean isActive() {
        if (studentId < 0) return false;
        if (SystemClock.elapsedRealtime() - lastTouch > IDLE_MS) {
            studentId = -1;
            return false;
        }
        return true;
    }

    public static synchronized long requireStudentId() {
        if (!isActive()) throw new IllegalStateException("No active session");
        return studentId;
    }

    public static synchronized void end() {
        studentId = -1;
    }
}
