package com.example.mini_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.attendanceportal.AttendanceRepo;
import com.example.attendanceportal.BaseActivity;
import com.example.attendanceportal.Models;
import com.example.attendanceportal.Session;
import com.example.attendanceportal.Ui;

import java.util.Locale;

/** What you see right after logging in: Attendance, CGPA and Document Vault. */
public class HomeActivity extends BaseActivity {

    private TextView tvWho, tvAttendance, tvAttendanceHint, tvCgpa, tvVault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;
        setContentView(R.layout.activity_home);

        tvWho = findViewById(R.id.tvHomeWho);
        tvAttendance = findViewById(R.id.tvAttendanceSummary);
        tvAttendanceHint = findViewById(R.id.tvAttendanceHint);
        tvCgpa = findViewById(R.id.tvCgpaSummary);
        tvVault = findViewById(R.id.tvVaultSummary);

        findViewById(R.id.cardAttendance).setOnClickListener(v ->
                startActivity(new Intent(this, com.example.attendanceportal.DashboardActivity.class)));
        findViewById(R.id.cardCgpa).setOnClickListener(v ->
                startActivity(new Intent(this, CgpaActivity.class)));
        findViewById(R.id.cardVault).setOnClickListener(v ->
                startActivity(new Intent(this, VaultActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
    }

    // Refresh the three summaries every time we come back to this screen
    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionOk || !Session.isActive()) return;

        AttendanceRepo repo = new AttendanceRepo(this);
        Models.Student me = repo.getProfile();
        tvWho.setText("Hello, " + titleCase(me.name));

        // ---- attendance (from the institutional database)
        Models.Overall overall = repo.getOverall();
        if (overall == null) {
            tvAttendance.setText("--");
            tvAttendanceHint.setText("No attendance recorded yet");
        } else {
            tvAttendance.setText(Ui.pct(overall.pct));
            tvAttendanceHint.setText(overall.isShortage()
                    ? "Below " + repo.getThreshold() + "%. Tap to see what to attend."
                    : "Eligible. Tap for subject-wise details.");
        }

        // ---- CGPA and vault (this student's own encrypted data)
        DBHelper db = new DBHelper(this);
        try {
            double cgpa = CgpaMath.cgpa(db.getSemesters());
            tvCgpa.setText(cgpa < 0 ? "--" : String.format(Locale.US, "%.2f", cgpa));
        } catch (Exception e) {
            tvCgpa.setText("--");
        }
        try {
            int n = db.getDocuments().size();
            tvVault.setText(n == 0 ? "Empty" : n + (n == 1 ? " document" : " documents"));
        } catch (Exception e) {
            tvVault.setText("--");
        }
    }

    private static String titleCase(String name) {
        StringBuilder out = new StringBuilder();
        for (String part : name.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
