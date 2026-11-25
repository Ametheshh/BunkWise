package com.example.attendanceportal;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.text.DateFormat;
import java.util.Date;

public final class AdminActivity extends BaseActivity {
    @Override protected boolean allowsSession() {
        return Session.hasRole(Session.Role.ADMIN);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!sessionOk) return;
        showDashboard();
    }

    private void showDashboard() {
        LinearLayout body = SchoolScreen.open(this, "System administration");
        SchoolScreen.button(body, "View audit log", this::showAudit);
        SchoolScreen.button(body, "Change password",
                () -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        SchoolScreen.button(body, "Log out", this::logout);

        SQLiteDatabase db = adminDatabase();
        SchoolScreen.heading(body, "Overview");
        try (Cursor cursor = db.rawQuery("SELECT (SELECT COUNT(*) FROM students WHERE is_active = 1), "
                + "(SELECT COUNT(*) FROM teachers WHERE is_active = 1), (SELECT COUNT(*) FROM subjects), "
                + "(SELECT COUNT(*) FROM cie_results WHERE source = 'PDF'), "
                + "(SELECT COUNT(*) FROM cie_results WHERE source = 'DEMO')", null)) {
            if (cursor.moveToFirst()) {
                SchoolScreen.text(body, cursor.getInt(0) + " active students · "
                        + cursor.getInt(1) + " active teachers · " + cursor.getInt(2) + " subjects");
                SchoolScreen.text(body, cursor.getInt(3) + " imported CIE results · "
                        + cursor.getInt(4) + " demo CIE results");
            }
        }
        SchoolScreen.heading(body, "Teacher assignments");
        try (Cursor cursor = db.rawQuery("SELECT t.name, t.email, s.code FROM teachers t "
                + "LEFT JOIN teacher_subjects a ON a.teacher_id = t.id "
                + "LEFT JOIN subjects s ON s.id = a.subject_id WHERE t.is_active = 1 ORDER BY t.id, s.code", null)) {
            while (cursor.moveToNext()) {
                SchoolScreen.text(body, cursor.getString(0) + " · " + cursor.getString(1) + " · "
                        + (cursor.isNull(2) ? "Unassigned" : cursor.getString(2)));
            }
        }
    }

    private void showAudit() {
        LinearLayout body = SchoolScreen.open(this, "Recent teacher changes");
        SchoolScreen.button(body, "Back to dashboard", this::showDashboard);
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        try (Cursor cursor = adminDatabase().rawQuery(
                "SELECT a.changed_at, t.name, s.code, p.usn, a.action, a.record_key, a.before_value, a.after_value "
                        + "FROM audit_log a JOIN teachers t ON t.id = a.teacher_id "
                        + "JOIN subjects s ON s.id = a.subject_id JOIN students p ON p.id = a.student_id "
                        + "ORDER BY a.id DESC LIMIT 100", null)) {
            if (cursor.getCount() == 0) SchoolScreen.text(body, "No teacher changes recorded yet.");
            while (cursor.moveToNext()) {
                SchoolScreen.heading(body, cursor.getString(4) + " · " + cursor.getString(2));
                SchoolScreen.text(body, dateFormat.format(new Date(cursor.getLong(0))) + " · " + cursor.getString(1)
                        + "\n" + cursor.getString(3) + " · " + cursor.getString(5) + "\n"
                        + (cursor.isNull(6) ? "New" : cursor.getString(6)) + " → " + cursor.getString(7));
            }
        }
    }

    private SQLiteDatabase adminDatabase() {
        Session.requireAccountId(Session.Role.ADMIN);
        return AttendanceDb.get(this);
    }
}
