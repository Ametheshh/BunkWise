package com.example.attendanceportal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The student's real attendance, typed in from the Uniclare website (Attendance > Summary) and kept
 * in its own database on this phone, separate from the demo data.
 */
public final class RealAttendance {
    private RealAttendance() { }

    public static final int MAX_SUBJECTS = 20;

    public static class Profile {
        public String usn, name;
    }

    public static class Entry {
        public String name;
        public boolean practical;
        public int conducted, attended;
    }

    /** Returns an error message, or null if the entries can be saved. */
    public static String validate(Profile p, List<Entry> entries) {
        if (p.usn == null || !p.usn.trim().matches("[A-Za-z0-9]{6,15}")) return "Enter your USN, for example NNM23CB012";
        if (p.name == null || p.name.trim().isEmpty() || p.name.trim().length() > 80) return "Enter your name";
        if (entries.isEmpty()) return "Add at least one subject";
        if (entries.size() > MAX_SUBJECTS) return "At most " + MAX_SUBJECTS + " subjects";
        List<String> seen = new ArrayList<>();
        for (Entry e : entries) {
            String n = e.name == null ? "" : e.name.trim();
            if (n.isEmpty() || n.length() > 80) return "Every subject needs a name";
            if (e.conducted < 0 || e.conducted > 1000) return n + ": classes held must be between 0 and 1000";
            if (e.attended < 0 || e.attended > e.conducted) return n + ": attended cannot be more than classes held";
            String key = n.toLowerCase(Locale.ROOT) + (e.practical ? "|p" : "|t");
            if (seen.contains(key)) return n + (e.practical ? " (Practical)" : "") + " is listed twice";
            seen.add(key);
        }
        return null;
    }

    /** Replaces whatever was saved before. Returns the local student id to put in the Session. */
    public static long save(Context context, Profile p, List<Entry> entries) {
        String err = validate(p, entries);
        if (err != null) throw new IllegalArgumentException(err);

        AttendanceDb.select(AttendanceDb.Source.REAL);
        SQLiteDatabase db = AttendanceDb.get(context);
        String usn = p.usn.trim().toUpperCase(Locale.ROOT);
        long sid = studentIdFor(usn);
        db.beginTransaction();
        try {
            db.delete("subject_totals", null, null);
            db.delete("enrollments", null, null);
            db.delete("attendance_records", null, null);
            db.delete("subjects", null, null);
            db.delete("students", null, null);

            ContentValues st = new ContentValues();
            st.put("id", sid);
            st.put("usn", usn);
            st.put("name", p.name.trim());
            st.put("email", "");
            db.insertOrThrow("students", null, st);

            int i = 0;
            for (Entry e : entries) {
                i++;
                ContentValues sub = new ContentValues();
                sub.put("code", String.format(Locale.US, "S%02d", i));
                sub.put("name", e.name.trim());
                sub.put("subject_type", e.practical ? "Practical" : "Theory");
                long subjectId = db.insertOrThrow("subjects", null, sub);

                ContentValues en = new ContentValues();
                en.put("student_id", sid);
                en.put("subject_id", subjectId);
                db.insertOrThrow("enrollments", null, en);

                ContentValues tot = new ContentValues();
                tot.put("student_id", sid);
                tot.put("subject_id", subjectId);
                tot.put("conducted", e.conducted);
                tot.put("attended", e.attended);
                db.insertOrThrow("subject_totals", null, tot);
            }

            ContentValues set = new ContentValues();
            set.put("key", "last_updated");
            set.put("value", String.valueOf(System.currentTimeMillis()));
            db.insertWithOnConflict("app_settings", null, set, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return sid;
    }

    /** The saved student, or null if nothing has been entered yet. */
    public static Profile loadProfile(Context context) {
        AttendanceDb.select(AttendanceDb.Source.REAL);
        Cursor c = AttendanceDb.get(context).rawQuery("SELECT usn, name FROM students LIMIT 1", null);
        try {
            if (!c.moveToFirst()) return null;
            Profile p = new Profile();
            p.usn = c.getString(0);
            p.name = c.getString(1);
            return p;
        } finally {
            c.close();
        }
    }

    public static List<Entry> loadEntries(Context context) {
        AttendanceDb.select(AttendanceDb.Source.REAL);
        List<Entry> list = new ArrayList<>();
        Cursor c = AttendanceDb.get(context).rawQuery(
                "SELECT s.name, s.subject_type, t.conducted, t.attended FROM subject_totals t "
                        + "JOIN subjects s ON s.id = t.subject_id ORDER BY s.code", null);
        try {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.name = c.getString(0);
                e.practical = "Practical".equals(c.getString(1));
                e.conducted = c.getInt(2);
                e.attended = c.getInt(3);
                list.add(e);
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Milliseconds since epoch of the last save, or 0. */
    public static long lastUpdated(Context context) {
        if (AttendanceDb.current() != AttendanceDb.Source.REAL) return 0;
        Cursor c = AttendanceDb.get(context).rawQuery(
                "SELECT value FROM app_settings WHERE key = 'last_updated'", null);
        try {
            return c.moveToFirst() ? Long.parseLong(c.getString(0)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        } finally {
            c.close();
        }
    }

    public static void deleteAll(Context context) {
        AttendanceDb.deleteRealData(context);
        AttendanceDb.select(AttendanceDb.Source.DEMO);
    }

    /**
     * A local id that cannot clash with the demo students (small ids). The CGPA planner and the vault
     * key their data on the session's student id, so this keeps each USN's data separate.
     */
    static long studentIdFor(String usn) {
        long h = usn.trim().toUpperCase(Locale.ROOT).hashCode() & 0x7fffffffL;
        return 1_000_000_000_000L + h;
    }
}
