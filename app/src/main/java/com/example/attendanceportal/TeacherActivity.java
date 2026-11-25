package com.example.attendanceportal;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TeacherActivity extends BaseActivity {
    private TeacherRepo repo;

    @Override protected boolean allowsSession() {
        return Session.isTeacher();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!sessionOk) return;
        repo = new TeacherRepo(this);
        showHome();
    }

    private void showHome() {
        LinearLayout body = SchoolScreen.open(this, "Teacher dashboard");
        Models.Student profile = new AttendanceRepo(this).getTeacherProfile();
        if (profile == null) {
            goToLogin("This account is unavailable.");
            return;
        }
        SchoolScreen.text(body, profile.name);
        List<TeacherRepo.Choice> subjects = repo.subjects();
        if (subjects.isEmpty()) {
            SchoolScreen.text(body, "No subjects are assigned to your account.");
        } else {
            Spinner subject = SchoolScreen.spinner(body, "Subject", subjects);
            SchoolScreen.button(body, "Take or correct attendance", () -> showAttendance(choice(subject)));
            SchoolScreen.button(body, "Enter or correct marks", () -> showMarks(choice(subject)));
        }
        SchoolScreen.button(body, "Recent changes", this::showAudit);
        SchoolScreen.button(body, "Change password", () -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        SchoolScreen.button(body, "Log out", this::logout);
    }

    private void showAttendance(TeacherRepo.Choice subject) {
        LinearLayout body = SchoolScreen.open(this, "Attendance");
        SchoolScreen.text(body, subject.label);
        SchoolScreen.button(body, "Back to dashboard", this::showHome);
        LocalDate start = repo.termStart();
        LocalDate end = repo.termEnd();
        LocalDate today = LocalDate.now();
        LocalDate[] selectedDate = {today.isBefore(start) ? start : today.isAfter(end) ? end : today};
        Spinner slot = SchoolScreen.spinner(body, "Timetable slot", repo.slots());
        Spinner batch = repo.isPractical(subject.id)
                ? SchoolScreen.spinner(body, "Lab batch", Arrays.asList("B1", "B2")) : null;

        LinearLayout roster = new LinearLayout(this);
        roster.setOrientation(LinearLayout.VERTICAL);
        MaterialButton dateButton = SchoolScreen.button(body, "Date · " + selectedDate[0], () -> {});
        dateButton.setOnClickListener(view -> {
            LocalDate date = selectedDate[0];
            DatePickerDialog picker = new DatePickerDialog(this, (dateView, year, month, day) -> {
                selectedDate[0] = LocalDate.of(year, month + 1, day);
                dateButton.setText("Date · " + selectedDate[0]);
                roster.removeAllViews();
            }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
            picker.getDatePicker().setMinDate(epochMillis(start));
            picker.getDatePicker().setMaxDate(epochMillis(end));
            picker.show();
        });

        onSelected(slot, roster::removeAllViews);
        if (batch != null) onSelected(batch, roster::removeAllViews);
        SchoolScreen.button(body, "Load class roster", () -> {
            String selectedSlot = (String) slot.getSelectedItem();
            if (selectedSlot == null) {
                toast("No timetable slots are available.");
                return;
            }
            String selectedBatch = batch == null ? null : (String) batch.getSelectedItem();
            LocalDate date = selectedDate[0];
            try {
                showRoster(roster, subject.id, date, selectedSlot, selectedBatch);
            } catch (RuntimeException e) {
                showError(e);
            }
        });
        body.addView(roster);
    }

    private void showRoster(LinearLayout roster, long subjectId, LocalDate date, String slot, String batch) {
        List<TeacherRepo.AttendanceRow> entries = repo.attendance(subjectId, date, slot, batch);
        roster.removeAllViews();
        if (entries.isEmpty()) {
            SchoolScreen.text(roster, "No enrolled students in this roster.");
            return;
        }
        SchoolScreen.heading(roster, "Class roster");
        SchoolScreen.text(roster, "Checked means present. Unchecked means absent. New entries start unchecked.");
        List<CheckBox> checks = new ArrayList<>();
        SchoolScreen.button(roster, "Mark everyone present", () -> {
            for (CheckBox check : checks) check.setChecked(true);
        });
        for (TeacherRepo.AttendanceRow row : entries) {
            CheckBox flag = new CheckBox(this);
            flag.setText(row.student.label);
            flag.setChecked("Present".equals(row.status));
            roster.addView(flag);
            checks.add(flag);
        }
        SchoolScreen.button(roster, "Save attendance", () -> {
            List<Boolean> present = new ArrayList<>();
            for (CheckBox check : checks) present.add(check.isChecked());
            try {
                int changed = repo.saveAttendance(subjectId, date, slot, batch, entries, present);
                toast(changed == 0 ? "No attendance changes" : "Attendance saved for " + changed + " students");
                showRoster(roster, subjectId, date, slot, batch);
            } catch (RuntimeException e) {
                showError(e);
            }
        });
    }

    private void showMarks(TeacherRepo.Choice subject) {
        LinearLayout body = SchoolScreen.open(this, "Enter marks");
        SchoolScreen.text(body, subject.label);
        SchoolScreen.button(body, "Back to dashboard", this::showHome);
        List<TeacherRepo.Choice> students = repo.students(subject.id);
        if (students.isEmpty()) {
            SchoolScreen.text(body, "No enrolled students.");
            return;
        }
        Spinner student = SchoolScreen.spinner(body, "Student", students);
        TextView cie = SchoolScreen.text(body, "");
        SchoolScreen.heading(body, "Teacher assessment entry");
        Spinner assessment = SchoolScreen.spinner(body, "Assessment", Arrays.asList(TeacherRepo.Assessment.values()));
        EditText score = numberField(body, "Score");
        EditText maximum = numberField(body, "Maximum score");
        TeacherRepo.Mark[] loaded = new TeacherRepo.Mark[1];
        long[] loadedStudent = {-1};
        TeacherRepo.Assessment[] loadedAssessment = new TeacherRepo.Assessment[1];
        Runnable refresh = () -> {
            loadedStudent[0] = -1;
            long studentId = choice(student).id;
            TeacherRepo.Assessment type = (TeacherRepo.Assessment) assessment.getSelectedItem();
            try {
                cie.setText(repo.cieResult(subject.id, studentId));
                loaded[0] = repo.mark(subject.id, studentId, type);
                loadedAssessment[0] = type;
                loadedStudent[0] = studentId;
                score.setText(loaded[0] == null ? "" : String.valueOf(loaded[0].score));
                maximum.setText(loaded[0] == null ? "" : String.valueOf(loaded[0].maximum));
                score.setError(null);
            } catch (RuntimeException e) {
                cie.setText("");
                showError(e);
            }
        };
        onSelected(student, refresh);
        onSelected(assessment, refresh);
        SchoolScreen.button(body, "Reload mark", refresh);
        SchoolScreen.button(body, "Save mark", () -> {
            int points;
            int total;
            try {
                points = Integer.parseInt(score.getText().toString().trim());
                total = Integer.parseInt(maximum.getText().toString().trim());
            } catch (NumberFormatException e) {
                score.setError("Enter a whole number in both fields.");
                return;
            }
            long studentId = choice(student).id;
            TeacherRepo.Assessment type = (TeacherRepo.Assessment) assessment.getSelectedItem();
            if (studentId != loadedStudent[0] || type != loadedAssessment[0]) {
                refresh.run();
                toast("Mark reloaded. Check the values and save again.");
                return;
            }
            try {
                boolean changed = repo.saveMark(subject.id, studentId, type, loaded[0], points, total);
                toast(changed ? "Mark saved" : "No mark changes");
                refresh.run();
            } catch (RuntimeException e) {
                showError(e);
            }
        });
        refresh.run();
    }

    private void showAudit() {
        LinearLayout body = SchoolScreen.open(this, "Recent changes");
        SchoolScreen.button(body, "Back to dashboard", this::showHome);
        List<String> changes = repo.recentChanges();
        if (changes.isEmpty()) SchoolScreen.text(body, "No changes recorded yet.");
        for (String change : changes) SchoolScreen.text(body, change + "\n");
    }

    private static TeacherRepo.Choice choice(Spinner spinner) {
        return (TeacherRepo.Choice) spinner.getSelectedItem();
    }

    private EditText numberField(LinearLayout body, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(field);
        return field;
    }

    private static long epochMillis(LocalDate day) {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static void onSelected(Spinner spinner, Runnable action) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                action.run();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void showError(RuntimeException exception) {
        if (!Session.isTeacher()) {
            goToLogin("Session expired. Please log in again.");
        } else if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException
                || exception instanceof SecurityException) {
            toast(exception.getMessage());
        } else {
            toast("Could not access the class record. Please try again.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
