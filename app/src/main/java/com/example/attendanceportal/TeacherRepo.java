package com.example.attendanceportal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Teacher writes and their audit entries commit together. */
final class TeacherRepo {
    enum Assessment {
        IA1("IA1"), IA2("IA2"), ASSIGNMENT("Assignment"), LAB("Lab"), SEMESTER_END("Semester End");

        final String label;

        Assessment(String label) {
            this.label = label;
        }

        @Override public String toString() {
            return label;
        }
    }

    static final class Choice {
        final long id;
        final String label;

        Choice(long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override public String toString() {
            return label;
        }
    }

    static final class AttendanceRow {
        final Choice student;
        final String status;

        AttendanceRow(Choice student, String status) {
            this.student = student;
            this.status = status;
        }
    }

    static final class Mark {
        final int score;
        final int maximum;

        Mark(int score, int maximum) {
            this.score = score;
            this.maximum = maximum;
        }

        @Override public String toString() {
            return score + "/" + maximum;
        }
    }

    private final SQLiteDatabase db;

    TeacherRepo(Context context) {
        db = AttendanceDb.get(context);
    }

    List<Choice> subjects() {
        List<Choice> choices = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT s.id, s.code, s.name FROM subjects s "
                + "JOIN teacher_subjects ts ON ts.subject_id = s.id WHERE ts.teacher_id = ? ORDER BY s.id",
                args(Session.requireTeacherId()))) {
            while (c.moveToNext()) choices.add(new Choice(c.getLong(0), c.getString(1) + " · " + c.getString(2)));
        }
        return choices;
    }

    List<Choice> students(long subjectId) {
        requireSubject(Session.requireTeacherId(), subjectId);
        List<Choice> choices = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT s.id, s.usn, s.name FROM students s "
                + "JOIN enrollments e ON e.student_id = s.id WHERE e.subject_id = ? AND s.is_active = 1 ORDER BY s.usn",
                args(subjectId))) {
            while (c.moveToNext()) choices.add(student(c));
        }
        return choices;
    }

    boolean isPractical(long subjectId) {
        requireSubject(Session.requireTeacherId(), subjectId);
        try (Cursor c = db.rawQuery("SELECT subject_type FROM subjects WHERE id = ?", args(subjectId))) {
            return c.moveToFirst() && "Practical".equals(c.getString(0));
        }
    }

    List<String> slots() {
        Session.requireTeacherId();
        List<String> slots = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT time_slot FROM attendance_records GROUP BY time_slot ORDER BY MIN(id)", null)) {
            while (cursor.moveToNext()) slots.add(cursor.getString(0));
        }
        return slots;
    }

    LocalDate termStart() {
        return termDate("term_start");
    }

    LocalDate termEnd() {
        return termDate("term_end");
    }

    private LocalDate termDate(String key) {
        Session.requireTeacherId();
        try (Cursor c = db.rawQuery("SELECT value FROM app_settings WHERE key = ?", args(key))) {
            if (!c.moveToFirst()) throw new IllegalStateException("Term dates are missing");
            return LocalDate.parse(c.getString(0));
        }
    }

    List<AttendanceRow> attendance(long subjectId, LocalDate date, String slot, String batch) {
        requireSubject(Session.requireTeacherId(), subjectId);
        validateClass(subjectId, date, slot, batch);
        return readAttendance(subjectId, date, slot, batch);
    }

    private List<AttendanceRow> readAttendance(long subjectId, LocalDate date, String slot, String batch) {
        String sql = "SELECT s.id, s.usn, s.name, r.status FROM enrollments e "
                + "JOIN students s ON s.id = e.student_id LEFT JOIN attendance_records r "
                + "ON r.student_id = s.id AND r.subject_id = e.subject_id AND r.class_date = ? AND r.time_slot = ? "
                + "WHERE e.subject_id = ? AND s.is_active = 1";
        String[] parameters = args(date, slot, subjectId);
        if (batch != null) {
            sql += " AND s.lab_batch = ?";
            parameters = args(date, slot, subjectId, batch);
        }
        List<AttendanceRow> roster = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql + " ORDER BY s.usn", parameters)) {
            while (c.moveToNext()) roster.add(new AttendanceRow(student(c), c.getString(3)));
        }
        return roster;
    }

    int saveAttendance(long subjectId, LocalDate date, String slot, String batch,
                       List<AttendanceRow> loaded, List<Boolean> present) {
        long teacherId = Session.requireTeacherId();
        db.beginTransaction();
        try {
            requireSubject(teacherId, subjectId);
            validateClass(subjectId, date, slot, batch);
            List<AttendanceRow> current = readAttendance(subjectId, date, slot, batch);
            if (current.isEmpty() || current.size() != loaded.size() || loaded.size() != present.size()) {
                throw new IllegalStateException("The roster changed. Load the class again.");
            }
            int changed = 0;
            for (int i = 0; i < current.size(); i++) {
                AttendanceRow before = current.get(i);
                if (before.student.id != loaded.get(i).student.id || !Objects.equals(before.status, loaded.get(i).status)) {
                    throw new IllegalStateException("Attendance changed. Load the class again.");
                }
                String status = present.get(i) ? "Present" : "Absent";
                if (status.equals(before.status)) continue;
                ContentValues values = new ContentValues();
                values.put("status", status);
                if (before.status == null) {
                    values.put("student_id", before.student.id);
                    values.put("subject_id", subjectId);
                    values.put("class_date", date.toString());
                    values.put("time_slot", slot);
                    values.put("batch", batch);
                    db.insertOrThrow("attendance_records", null, values);
                } else {
                    db.update("attendance_records", values,
                            "student_id = ? AND subject_id = ? AND class_date = ? AND time_slot = ?",
                            args(before.student.id, subjectId, date, slot));
                }
                audit(teacherId, before.student.id, subjectId, "Attendance", date + " " + slot, before.status, status);
                changed++;
            }
            db.setTransactionSuccessful();
            return changed;
        } finally {
            db.endTransaction();
        }
    }

    String cieResult(long subjectId, long studentId) {
        requireSubject(Session.requireTeacherId(), subjectId);
        requireEnrollment(subjectId, studentId);
        try (Cursor cursor = db.rawQuery(
                "SELECT source, mse1, mse2, la1, la2, la3, total, final FROM cie_results "
                        + "WHERE subject_id = ? AND student_id = ?", args(subjectId, studentId))) {
            return cursor.moveToFirst() ? CieResult.display(cursor, 0) : "No CIE sheet available.";
        }
    }

    Mark mark(long subjectId, long studentId, Assessment assessment) {
        requireSubject(Session.requireTeacherId(), subjectId);
        requireEnrollment(subjectId, studentId);
        return readMark(subjectId, studentId, assessment);
    }

    private Mark readMark(long subjectId, long studentId, Assessment assessment) {
        try (Cursor c = db.rawQuery("SELECT score, maximum FROM marks "
                + "WHERE subject_id = ? AND student_id = ? AND assessment = ?", args(subjectId, studentId, assessment))) {
            return c.moveToFirst() ? new Mark(c.getInt(0), c.getInt(1)) : null;
        }
    }

    boolean saveMark(long subjectId, long studentId, Assessment assessment, Mark loaded, int score, int maximum) {
        if (maximum <= 0 || score < 0 || score > maximum) {
            throw new IllegalArgumentException("Enter a score between zero and the maximum.");
        }
        long teacherId = Session.requireTeacherId();
        db.beginTransaction();
        try {
            requireSubject(teacherId, subjectId);
            requireEnrollment(subjectId, studentId);
            String before = Objects.toString(readMark(subjectId, studentId, assessment), null);
            if (!Objects.equals(before, Objects.toString(loaded, null))) {
                throw new IllegalStateException("The mark changed. Select the assessment again.");
            }
            String after = new Mark(score, maximum).toString();
            if (after.equals(before)) {
                db.setTransactionSuccessful();
                return false;
            }
            ContentValues values = new ContentValues();
            values.put("score", score);
            values.put("maximum", maximum);
            if (before == null) {
                values.put("subject_id", subjectId);
                values.put("student_id", studentId);
                values.put("assessment", assessment.label);
                db.insertOrThrow("marks", null, values);
            } else {
                db.update("marks", values, "subject_id = ? AND student_id = ? AND assessment = ?",
                        args(subjectId, studentId, assessment));
            }
            audit(teacherId, studentId, subjectId, "Marks", assessment.label, before, after);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    List<String> recentChanges() {
        List<String> changes = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT a.action, s.code, p.usn, a.record_key, a.before_value, a.after_value, a.changed_at "
                + "FROM audit_log a JOIN subjects s ON s.id = a.subject_id JOIN students p ON p.id = a.student_id "
                + "JOIN teacher_subjects ts ON ts.teacher_id = a.teacher_id AND ts.subject_id = a.subject_id "
                + "WHERE a.teacher_id = ? ORDER BY a.id DESC LIMIT 100", args(Session.requireTeacherId()))) {
            while (c.moveToNext()) {
                String time = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(c.getLong(6)));
                changes.add(time + "\n" + c.getString(0) + " · " + c.getString(1) + " · " + c.getString(2)
                        + "\n" + c.getString(3) + " · "
                        + (c.isNull(4) ? "New" : c.getString(4)) + " → " + c.getString(5));
            }
        }
        return changes;
    }

    private void validateClass(long subjectId, LocalDate date, String slot, String batch) {
        if (date.isBefore(termStart()) || date.isAfter(termEnd())) {
            throw new IllegalArgumentException("Choose a date within the term.");
        }
        if (!slots().contains(slot)) throw new IllegalArgumentException("Choose a timetable slot.");
        if (isPractical(subjectId) ? !("B1".equals(batch) || "B2".equals(batch)) : batch != null) {
            throw new IllegalArgumentException("Choose the correct batch for this subject.");
        }
    }

    private void requireSubject(long teacherId, long subjectId) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM teacher_subjects ts JOIN teachers t ON t.id = ts.teacher_id "
                + "WHERE ts.teacher_id = ? AND ts.subject_id = ? AND t.is_active = 1", args(teacherId, subjectId))) {
            if (!c.moveToFirst()) throw new SecurityException("This subject is outside your assignment.");
        }
    }

    private void requireEnrollment(long subjectId, long studentId) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM enrollments e JOIN students s ON s.id = e.student_id "
                + "WHERE e.subject_id = ? AND e.student_id = ? AND s.is_active = 1", args(subjectId, studentId))) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("This student is outside the class roster.");
        }
    }

    private void audit(long teacherId, long studentId, long subjectId, String action, String key, String before, String after) {
        ContentValues values = new ContentValues();
        values.put("teacher_id", teacherId);
        values.put("student_id", studentId);
        values.put("subject_id", subjectId);
        values.put("action", action);
        values.put("record_key", key);
        values.put("before_value", before);
        values.put("after_value", after);
        values.put("changed_at", System.currentTimeMillis());
        db.insertOrThrow("audit_log", null, values);
    }

    private static Choice student(Cursor c) {
        return new Choice(c.getLong(0), c.getString(1) + " · " + c.getString(2));
    }

    private static String[] args(Object... values) {
        String[] args = new String[values.length];
        for (int i = 0; i < values.length; i++) args[i] = values[i].toString();
        return args;
    }
}
