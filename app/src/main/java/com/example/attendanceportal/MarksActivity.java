package com.example.attendanceportal;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.LinearLayout;

public final class MarksActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!sessionOk) return;
        LinearLayout body = SchoolScreen.open(this, "Marks");
        SchoolScreen.button(body, "Back", this::finish);
        String lastSubject = null;
        try (Cursor c = AttendanceDb.get(this).rawQuery(
                "SELECT s.code, s.name, m.assessment, m.score, m.maximum, "
                        + "c.source, c.mse1, c.mse2, c.la1, c.la2, c.la3, c.total, c.final FROM enrollments e "
                        + "JOIN subjects s ON s.id = e.subject_id LEFT JOIN marks m "
                        + "ON m.student_id = e.student_id AND m.subject_id = e.subject_id "
                        + "LEFT JOIN cie_results c ON c.student_id = e.student_id AND c.subject_id = e.subject_id "
                        + "WHERE e.student_id = ? ORDER BY s.name, CASE m.assessment "
                        + "WHEN 'IA1' THEN 1 WHEN 'IA2' THEN 2 WHEN 'Assignment' THEN 3 WHEN 'Lab' THEN 4 ELSE 5 END",
                new String[]{String.valueOf(Session.requireStudentId())})) {
            while (c.moveToNext()) {
                String code = c.getString(0);
                if (!code.equals(lastSubject)) {
                    SchoolScreen.heading(body, c.getString(1) + " · " + code);
                    SchoolScreen.text(body, CieResult.display(c, 5));
                    lastSubject = code;
                }
                if (!c.isNull(2)) {
                    SchoolScreen.text(body, "Teacher entry · " + c.getString(2) + "   " + c.getInt(3) + " / " + c.getInt(4));
                }
            }
        }
        if (lastSubject == null) SchoolScreen.text(body, "No enrolled subjects.");
    }
}
