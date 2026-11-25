package com.example.attendanceportal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Opens one of two databases:
 *  - DEMO:     the bundled synthetic attendance.db (copied from assets on first launch).
 *  - REAL:     my_attendance.db, created empty on this device and filled by RealAttendance with the
 *              student's real attendance, typed in from Uniclare. Kept separate so real and demo data never mix.
 * AttendanceRepo works on both because they expose the same tables and views.
 */
public final class AttendanceDb {
    public enum Source { DEMO, REAL }

    private static final String DEMO_DB = "attendance.db";
    private static final String REAL_DB = "my_attendance.db";
    private static SQLiteDatabase instance;
    private static Source source = Source.DEMO;

    private AttendanceDb() { }

    /** Called at login. Switching source closes the other database. */
    public static synchronized void select(Source s) {
        if (s == source) return;
        close();
        source = s;
    }

    public static synchronized Source current() {
        return source;
    }

    public static synchronized SQLiteDatabase get(Context context) {
        if (instance != null && instance.isOpen()) return instance;
        Context app = context.getApplicationContext();
        instance = source == Source.REAL ? openReal(app) : openDemo(app);
        return instance;
    }

    /** Removes the real attendance stored on this device. */
    public static synchronized void deleteRealData(Context context) {
        if (source == Source.REAL) close();
        context.getApplicationContext().deleteDatabase(REAL_DB);
    }

    private static void close() {
        if (instance != null) instance.close();
        instance = null;
    }

    private static SQLiteDatabase openDemo(Context app) {
        File dbFile = app.getDatabasePath(DEMO_DB);
        if (!dbFile.exists()) {
            File dir = dbFile.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(dbFile.getPath() + ".tmp");
            try (InputStream in = app.getAssets().open(DEMO_DB);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (IOException e) {
                tmp.delete();
                throw new RuntimeException("Could not install the bundled database", e);
            }
            if (!tmp.renameTo(dbFile)) throw new RuntimeException("Could not install the bundled database");
        }
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            db.setForeignKeyConstraintsEnabled(true);
            installAppSchema(app, db);
            return db;
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
    }

    private static void installAppSchema(Context app, SQLiteDatabase db) {
        db.beginTransaction();
        try {
            runSqlAsset(app, db, "teacher_schema.sql");
            runSqlAsset(app, db, "cie_and_admin.sql");
            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new IllegalStateException("Could not upgrade the attendance database", e);
        } finally {
            db.endTransaction();
        }
    }

    private static void runSqlAsset(Context app, SQLiteDatabase db, String name) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                app.getAssets().open(name), StandardCharsets.UTF_8))) {
            String statement;
            while ((statement = reader.readLine()) != null) {
                if (!statement.trim().isEmpty()) db.execSQL(statement);
            }
        }
    }

    private static SQLiteDatabase openReal(Context app) {
        File dbFile = app.getDatabasePath(REAL_DB);
        File dir = dbFile.getParentFile();
        if (dir != null) dir.mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        if (db.getVersion() == 0) {
            db.beginTransaction();
            try {
                for (String sql : REAL_SCHEMA) db.execSQL(sql);
                db.setVersion(1);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        return db;
    }

    /**
     * Same table and view names as the demo database. Real data is entered as totals per subject, not one row
     * per class, so v_attendance_summary is built from subject_totals instead of attendance_records.
     */
    private static final String[] REAL_SCHEMA = {
            "CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "INSERT INTO app_settings (key, value) VALUES ('eligibility_threshold', '75')",
            "CREATE TABLE students (id INTEGER PRIMARY KEY, usn TEXT NOT NULL, name TEXT NOT NULL, "
                    + "email TEXT NOT NULL, lab_batch TEXT, pw_salt TEXT NOT NULL DEFAULT '', "
                    + "pw_hash TEXT NOT NULL DEFAULT '', password_changed INTEGER NOT NULL DEFAULT 1, "
                    + "failed_logins INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0, "
                    + "is_active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE subjects (id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, "
                    + "subject_type TEXT NOT NULL)",
            "CREATE TABLE enrollments (id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL, "
                    + "subject_id INTEGER NOT NULL, UNIQUE (student_id, subject_id))",
            "CREATE TABLE attendance_records (id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL, "
                    + "subject_id INTEGER NOT NULL, class_date TEXT NOT NULL, time_slot TEXT NOT NULL, batch TEXT, "
                    + "status TEXT NOT NULL CHECK (status IN ('Present', 'Absent')), "
                    + "UNIQUE (student_id, subject_id, class_date, time_slot))",
            "CREATE TABLE subject_totals (student_id INTEGER NOT NULL, subject_id INTEGER NOT NULL, "
                    + "conducted INTEGER NOT NULL, attended INTEGER NOT NULL, PRIMARY KEY (student_id, subject_id))",
            "CREATE VIEW v_attendance_summary AS "
                    + "SELECT b.student_id, b.subject_id, b.conducted AS classes_conducted, b.attended AS classes_attended, "
                    + "ROUND(100.0 * b.attended / b.conducted, 2) AS attendance_pct, "
                    + "CASE WHEN 100 * b.attended >= t.v * b.conducted THEN 'Eligible' ELSE 'Shortage' END AS eligibility, "
                    + "CASE WHEN 100 * b.attended >= t.v * b.conducted THEN 0 "
                    + "ELSE (t.v * b.conducted - 100 * b.attended + (100 - t.v) - 1) / (100 - t.v) END AS classes_needed "
                    + "FROM subject_totals b "
                    + "CROSS JOIN (SELECT CAST(value AS INTEGER) AS v FROM app_settings WHERE key = 'eligibility_threshold') t "
                    + "WHERE b.conducted > 0",
            "CREATE VIEW v_overall_summary AS "
                    + "SELECT b.student_id, b.classes_conducted, b.classes_attended, "
                    + "ROUND(100.0 * b.classes_attended / b.classes_conducted, 2) AS attendance_pct, "
                    + "CASE WHEN 100 * b.classes_attended >= t.v * b.classes_conducted THEN 'Eligible' ELSE 'Shortage' END AS eligibility, "
                    + "CASE WHEN 100 * b.classes_attended >= t.v * b.classes_conducted THEN 0 "
                    + "ELSE (t.v * b.classes_conducted - 100 * b.classes_attended + (100 - t.v) - 1) / (100 - t.v) END AS classes_needed "
                    + "FROM (SELECT student_id, SUM(classes_conducted) AS classes_conducted, SUM(classes_attended) AS classes_attended "
                    + "FROM v_attendance_summary GROUP BY student_id) b "
                    + "CROSS JOIN (SELECT CAST(value AS INTEGER) AS v FROM app_settings WHERE key = 'eligibility_threshold') t",
    };
}
