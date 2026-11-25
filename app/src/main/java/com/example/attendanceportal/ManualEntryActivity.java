package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the student types in their real attendance from the Uniclare website. Saving it also logs
 * them in, so this is reached both from the login screen and from the Dashboard's Update button.
 */
public class ManualEntryActivity extends AppCompatActivity {

    private EditText etUsn, etName;
    private LinearLayout rows;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_manual_entry);

        etUsn = findViewById(R.id.etUsn);
        etName = findViewById(R.id.etName);
        rows = findViewById(R.id.rowsContainer);
        tvError = findViewById(R.id.tvError);

        findViewById(R.id.btnAddSubject).setOnClickListener(v -> {
            if (rows.getChildCount() >= RealAttendance.MAX_SUBJECTS) {
                showError("At most " + RealAttendance.MAX_SUBJECTS + " subjects");
            } else {
                addRow(null).findViewById(R.id.etSubName).requestFocus();
            }
        });
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnDeleteAll).setOnClickListener(v -> confirmDelete());

        if (savedInstanceState == null) loadSaved();
        if (rows.getChildCount() == 0) addRow(null);
    }

    private void loadSaved() {
        RealAttendance.Profile p = RealAttendance.loadProfile(this);
        if (p == null) return;
        etUsn.setText(p.usn);
        etName.setText(p.name);
        for (RealAttendance.Entry e : RealAttendance.loadEntries(this)) addRow(e);
        findViewById(R.id.btnDeleteAll).setVisibility(View.VISIBLE);
    }

    private View addRow(RealAttendance.Entry e) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_manual_subject, rows, false);
        if (e != null) {
            ((EditText) row.findViewById(R.id.etSubName)).setText(e.name);
            ((EditText) row.findViewById(R.id.etConducted)).setText(String.valueOf(e.conducted));
            ((EditText) row.findViewById(R.id.etAttended)).setText(String.valueOf(e.attended));
            ((CheckBox) row.findViewById(R.id.cbPractical)).setChecked(e.practical);
        }
        row.findViewById(R.id.btnRemove).setOnClickListener(v -> rows.removeView(row));
        rows.addView(row);
        return row;
    }

    private void save() {
        RealAttendance.Profile p = new RealAttendance.Profile();
        p.usn = etUsn.getText().toString().trim();
        p.name = etName.getText().toString().trim();

        List<RealAttendance.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            String name = ((EditText) row.findViewById(R.id.etSubName)).getText().toString().trim();
            String held = ((EditText) row.findViewById(R.id.etConducted)).getText().toString().trim();
            String att = ((EditText) row.findViewById(R.id.etAttended)).getText().toString().trim();
            if (name.isEmpty() && held.isEmpty() && att.isEmpty()) continue;    // ignore blank rows
            RealAttendance.Entry e = new RealAttendance.Entry();
            e.name = name;
            e.conducted = toInt(held);
            e.attended = toInt(att);
            e.practical = ((CheckBox) row.findViewById(R.id.cbPractical)).isChecked();
            if (e.conducted < 0 || e.attended < 0) {
                showError((name.isEmpty() ? "A subject" : name) + ": enter classes held and attended");
                return;
            }
            entries.add(e);
        }

        String err = RealAttendance.validate(p, entries);
        if (err != null) {
            showError(err);
            return;
        }
        long sid;
        try {
            sid = RealAttendance.save(this, p, entries);
        } catch (Exception ex) {
            showError("Could not save. Please try again.");
            return;
        }
        Session.start(sid, Session.Role.STUDENT);
        Toast.makeText(this, "Attendance saved", Toast.LENGTH_SHORT).show();
        // Home underneath, Dashboard on top: Back from the Dashboard lands on Home as after a normal login
        startActivity(new Intent(this, com.example.mini_project.HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete saved attendance?")
                .setMessage("This removes the attendance you entered from this phone.")
                .setPositiveButton("Delete", (d, w) -> {
                    RealAttendance.deleteAll(this);
                    Session.end();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
