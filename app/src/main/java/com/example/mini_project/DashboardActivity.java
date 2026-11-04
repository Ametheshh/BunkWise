package com.example.mini_project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvAttendance, tvCgpa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_dashboard);

        tvAttendance = findViewById(R.id.tvAttendanceSummary);
        tvCgpa = findViewById(R.id.tvCgpaSummary);
        View cardAttendance = findViewById(R.id.cardAttendance);
        View cardCgpa = findViewById(R.id.cardCgpa);
        View cardVault = findViewById(R.id.cardVault);

        cardAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceActivity.class)));
        cardCgpa.setOnClickListener(v ->
                startActivity(new Intent(this, CgpaActivity.class)));
        cardVault.setOnClickListener(v ->
                startActivity(new Intent(this, VaultActivity.class)));
    }

    // Refresh both summaries every time we come back to this screen
    @Override
    protected void onResume() {
        super.onResume();
        DBHelper db = new DBHelper(this);

        try {
            List<Subject> list = db.getAll();
            int a = 0, c = 0;
            for (Subject s : list) { a += s.attended; c += s.conducted; }
            tvAttendance.setText(c == 0 ? "--%"
                    : String.format(Locale.US, "%.1f%%", AttendanceMath.percentage(a, c)));
        } catch (Exception e) {
            tvAttendance.setText("--%");
        }

        try {
            double cgpa = CgpaMath.cgpa(db.getSemesters());
            tvCgpa.setText(cgpa < 0 ? "--" : String.format(Locale.US, "%.2f", cgpa));
        } catch (Exception e) {
            tvCgpa.setText("--");
        }
    }
}
