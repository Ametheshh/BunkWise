package com.example.attendanceportal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * All database access. Except for the two login-time methods, every method works on the student in
 * Session and takes no student id parameter, so there is no way to ask for someone else's records.
 * Every query is parameterised.
 */
public class AttendanceRepo {
    private final SQLiteDatabase db;

    public AttendanceRepo(Context context) {
        db = AttendanceDb.get(context);
    }

    private static long sid() {
        return Session.requireStudentId();
    }

    private static final String STUDENT_COLUMNS =
            "id, usn, name, email, lab_batch, pw_salt, pw_hash, password_changed, failed_logins, locked_until";

    private static Models.Student readStudent(Cursor c) {
        Models.Student s = new Models.Student();
        s.id = c.getLong(0);
        s.usn = c.getString(1);
        s.name = c.getString(2);
        s.email = c.getString(3);
        s.labBatch = c.getString(4);
        s.salt = c.getString(5);
        s.hash = c.getString(6);
        s.passwordChanged = c.getInt(7) == 1;
        s.failedLogins = c.getInt(8);
        s.lockedUntil = c.getLong(9);
        return s;
    }

    // ------------------------------------------------------------------ login time (no session yet)
    public Models.Student findStudentByEmail(String email) {
        Cursor c = db.rawQuery("SELECT " + STUDENT_COLUMNS + " FROM students WHERE email = ? AND is_active = 1",
                new String[]{email});
        try {
            return c.moveToFirst() ? readStudent(c) : null;
        } finally {
            c.close();
        }
    }

    public void recordFailedLogin(long studentId, int failedLogins, long lockedUntil) {
        ContentValues v = new ContentValues();
        v.put("failed_logins", failedLogins);
        v.put("locked_until", lockedUntil);
        db.update("students", v, "id = ?", new String[]{String.valueOf(studentId)});
    }

    public void recordSuccessfulLogin(long studentId) {
        recordFailedLogin(studentId, 0, 0);
    }

    // ------------------------------------------------------------------ logged-in student only
    public Models.Student getProfile() {
        Cursor c = db.rawQuery("SELECT " + STUDENT_COLUMNS + " FROM students WHERE id = ?",
                new String[]{String.valueOf(sid())});
        try {
            return c.moveToFirst() ? readStudent(c) : null;
        } finally {
            c.close();
        }
    }

    public void updateMyPassword(String salt, String hash) {
        ContentValues v = new ContentValues();
        v.put("pw_salt", salt);
        v.put("pw_hash", hash);
        v.put("password_changed", 1);
        v.put("failed_logins", 0);
        v.put("locked_until", 0);
        db.update("students", v, "id = ?", new String[]{String.valueOf(sid())});
    }

    public int getThreshold() {
        Cursor c = db.rawQuery("SELECT value FROM app_settings WHERE key = 'eligibility_threshold'", null);
        try {
            return c.moveToFirst() ? Integer.parseInt(c.getString(0)) : 75;
        } finally {
            c.close();
        }
    }

    public Models.Overall getOverall() {
        Cursor c = db.rawQuery("SELECT classes_conducted, classes_attended, attendance_pct, eligibility, classes_needed "
                + "FROM v_overall_summary WHERE student_id = ?", new String[]{String.valueOf(sid())});
        try {
            if (!c.moveToFirst()) return null;
            Models.Overall o = new Models.Overall();
            o.conducted = c.getInt(0);
            o.attended = c.getInt(1);
            o.pct = c.getDouble(2);
            o.eligibility = c.getString(3);
            o.needed = c.getInt(4);
            return o;
        } finally {
            c.close();
        }
    }

    public List<Models.SubjectSummary> getSubjects() {
        int threshold = getThreshold();
        List<Models.SubjectSummary> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT s.code, s.name, s.subject_type, "
                        + "IFNULL(v.classes_conducted, 0), IFNULL(v.classes_attended, 0), "
                        + "IFNULL(v.attendance_pct, 0), IFNULL(v.eligibility, ''), IFNULL(v.classes_needed, 0) "
                        + "FROM enrollments e JOIN subjects s ON s.id = e.subject_id "
                        + "LEFT JOIN v_attendance_summary v ON v.student_id = e.student_id AND v.subject_id = e.subject_id "
                        + "WHERE e.student_id = ? ORDER BY s.name",
                new String[]{String.valueOf(sid())});
        try {
            while (c.moveToNext()) {
                Models.SubjectSummary s = new Models.SubjectSummary();
                s.code = c.getString(0);
                s.name = c.getString(1);
                s.type = c.getString(2);
                s.conducted = c.getInt(3);
                s.attended = c.getInt(4);
                s.pct = c.getDouble(5);
                s.eligibility = c.getString(6);
                s.needed = c.getInt(7);
                s.canMiss = AttendanceMath.canMiss(s.attended, s.conducted, threshold);
                list.add(s);
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Returns null unless the logged-in student is enrolled in that subject. */
    public Models.SubjectSummary getSubject(String code) {
        if (code == null) return null;
        for (Models.SubjectSummary s : getSubjects()) {
            if (s.code.equalsIgnoreCase(code.trim())) return s;
        }
        return null;
    }

    public List<Models.MonthStat> getMonthly(String subjectCode) {
        List<Models.MonthStat> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT substr(r.class_date, 1, 7), COUNT(*), "
                        + "SUM(CASE WHEN r.status = 'Present' THEN 1 ELSE 0 END) "
                        + "FROM attendance_records r JOIN subjects s ON s.id = r.subject_id "
                        + "WHERE r.student_id = ? AND s.code = ? GROUP BY 1 ORDER BY 1",
                new String[]{String.valueOf(sid()), subjectCode});
        try {
            while (c.moveToNext()) {
                Models.MonthStat m = new Models.MonthStat();
                m.month = c.getString(0);
                m.conducted = c.getInt(1);
                m.attended = c.getInt(2);
                list.add(m);
            }
        } finally {
            c.close();
        }
        return list;
    }

    public List<String> getMonths() {
        List<String> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT DISTINCT substr(class_date, 1, 7) FROM attendance_records "
                + "WHERE student_id = ? ORDER BY 1 DESC", new String[]{String.valueOf(sid())});
        try {
            while (c.moveToNext()) list.add(c.getString(0));
        } finally {
            c.close();
        }
        return list;
    }

    /** subjectCode, status and month are optional filters (null or empty = no filter). */
    public List<Models.HistoryItem> getHistory(String subjectCode, String status, String month, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.class_date, r.time_slot, s.name, s.code, r.status "
                        + "FROM attendance_records r JOIN subjects s ON s.id = r.subject_id "
                        + "JOIN enrollments e ON e.subject_id = s.id AND e.student_id = r.student_id "
                        + "WHERE r.student_id = ?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(sid()));
        if (subjectCode != null && !subjectCode.isEmpty()) {
            sql.append(" AND s.code = ?");
            args.add(subjectCode);
        }
        if ("Present".equals(status) || "Absent".equals(status)) {
            sql.append(" AND r.status = ?");
            args.add(status);
        }
        if (month != null && month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            sql.append(" AND substr(r.class_date, 1, 7) = ?");
            args.add(month);
        }
        sql.append(" ORDER BY r.class_date DESC, r.time_slot DESC, s.name LIMIT ").append(Math.max(1, limit));

        List<Models.HistoryItem> list = new ArrayList<>();
        Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                Models.HistoryItem h = new Models.HistoryItem();
                h.date = c.getString(0);
                h.slot = c.getString(1);
                h.subjectName = c.getString(2);
                h.subjectCode = c.getString(3);
                h.status = c.getString(4);
                list.add(h);
            }
        } finally {
            c.close();
        }
        return list;
    }
}
