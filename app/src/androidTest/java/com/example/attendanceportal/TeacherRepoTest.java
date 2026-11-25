package com.example.attendanceportal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TeacherRepoTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void teacherChangesAttendanceAndMarksOnlyForAssignedSubjects() {
        SQLiteDatabase db = AttendanceDb.get(context);
        db.beginTransaction();
        try {
            Session.start(1, Session.Role.TEACHER);
            TeacherRepo repo = new TeacherRepo(context);
            assertEquals(2, repo.subjects().size());
            assertThrows(SecurityException.class, () -> repo.students(3));
            assertThrows(IllegalStateException.class, Session::requireStudentId);

            long subjectId = repo.subjects().get(0).id;
            long studentId = repo.students(subjectId).get(0).id;
            TeacherRepo.Assessment assessment = TeacherRepo.Assessment.IA1;
            TeacherRepo.Mark original = repo.mark(subjectId, studentId, assessment);
            int score = original != null && original.score == 16 ? 17 : 16;
            assertTrue(repo.saveMark(subjectId, studentId, assessment, original, score, 20));
            TeacherRepo.Mark saved = repo.mark(subjectId, studentId, assessment);
            assertEquals(score, saved.score);
            assertEquals(20, saved.maximum);
            assertFalse(repo.saveMark(subjectId, studentId, assessment, saved, score, 20));
            assertThrows(IllegalArgumentException.class,
                    () -> repo.saveMark(subjectId, studentId, assessment, saved, 21, 20));
            assertThrows(IllegalStateException.class,
                    () -> repo.saveMark(subjectId, studentId, assessment, original, 20, 20));

            LocalDate date = repo.termStart();
            String slot = repo.slots().get(0);
            List<TeacherRepo.AttendanceRow> roster = repo.attendance(subjectId, date, slot, null);
            List<Boolean> present = new ArrayList<>();
            for (TeacherRepo.AttendanceRow row : roster) present.add("Present".equals(row.status));
            present.set(0, !present.get(0));
            assertTrue(repo.saveAttendance(subjectId, date, slot, null, roster, present) > 0);
            List<TeacherRepo.AttendanceRow> updated = repo.attendance(subjectId, date, slot, null);
            assertEquals(present.get(0) ? "Present" : "Absent", updated.get(0).status);
            assertEquals(0, repo.saveAttendance(subjectId, date, slot, null, updated, present));
            assertFalse(repo.recentChanges().isEmpty());

            Session.start(studentId, Session.Role.STUDENT);
            assertThrows(IllegalStateException.class, repo::subjects);
        } finally {
            Session.end();
            db.endTransaction();
        }
    }
}
