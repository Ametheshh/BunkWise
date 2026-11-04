package com.example.mini_project;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class AttendanceActivity extends AppCompatActivity {

    private DBHelper db;
    private SharedPreferences prefs;
    private LinearLayout container;
    private TextView tvOverall;
    private EditText etName, etAttended, etConducted, etTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_attendance);

        db = new DBHelper(this);
        prefs = getSharedPreferences("secure_prefs", MODE_PRIVATE);

        container = findViewById(R.id.container);
        tvOverall = findViewById(R.id.tvOverall);
        etName = findViewById(R.id.etName);
        etAttended = findViewById(R.id.etAttended);
        etConducted = findViewById(R.id.etConducted);
        etTarget = findViewById(R.id.etTarget);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnTarget = findViewById(R.id.btnTarget);

        etTarget.setText(String.valueOf(prefs.getInt("target", 85)));

        btnAdd.setOnClickListener(v -> addSubject());
        btnTarget.setOnClickListener(v -> {
            int t = readTarget();
            if (t != -1) {
                prefs.edit().putInt("target", t).apply();
                refresh();
            }
        });

        refresh();
    }

    /** Returns target 1..99, or -1 if invalid. 100 is blocked because the formula divides by (1 - t). */
    private int readTarget() {
        try {
            int t = Integer.parseInt(etTarget.getText().toString().trim());
            if (t < 1 || t > 99) throw new NumberFormatException();
            return t;
        } catch (NumberFormatException e) {
            etTarget.setError("Enter 1 to 99");
            return -1;
        }
    }

    private void addSubject() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 40) {
            etName.setError("Name required (max 40 characters)");
            return;
        }
        int a, c;
        try {
            a = Integer.parseInt(etAttended.getText().toString().trim());
            c = Integer.parseInt(etConducted.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter valid numbers", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AttendanceMath.isValid(a, c)) {
            Toast.makeText(this, "Attended cannot exceed conducted, and conducted must be above 0",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            db.addSubject(name, a, c);
            etName.setText("");
            etAttended.setText("");
            etConducted.setText("");
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        container.removeAllViews();
        int target = prefs.getInt("target", 85);
        double t = target / 100.0;
        int totalA = 0, totalC = 0;

        try {
            List<Subject> subjects = db.getAll();
            LayoutInflater inflater = LayoutInflater.from(this);

            for (Subject s : subjects) {
                totalA += s.attended;
                totalC += s.conducted;

                View item = inflater.inflate(R.layout.item_subject, container, false);
                TextView tvName = item.findViewById(R.id.tvName);
                TextView tvStats = item.findViewById(R.id.tvStats);
                TextView tvAdvice = item.findViewById(R.id.tvAdvice);
                ProgressBar pb = item.findViewById(R.id.pb);

                double pct = AttendanceMath.percentage(s.attended, s.conducted);
                tvName.setText(s.name);
                tvStats.setText(String.format(Locale.US, "%.1f%%   (%d / %d classes)",
                        pct, s.attended, s.conducted));
                pb.setProgress((int) Math.round(pct));

                if (pct >= target) {
                    int miss = AttendanceMath.canMiss(s.attended, s.conducted, t);
                    tvStats.setTextColor(Color.parseColor("#2E7D32"));
                    tvAdvice.setText("On track. You can miss up to " + miss + " more class(es).");
                } else {
                    int need = AttendanceMath.classesNeeded(s.attended, s.conducted, t);
                    tvStats.setTextColor(Color.parseColor("#C62828"));
                    tvAdvice.setText("Attend the next " + need + " class(es) in a row to reach " + target + "%.");
                }

                final Subject sub = s;
                item.findViewById(R.id.btnPresent).setOnClickListener(v ->
                        update(sub.id, sub.attended + 1, sub.conducted + 1));
                item.findViewById(R.id.btnAbsent).setOnClickListener(v ->
                        update(sub.id, sub.attended, sub.conducted + 1));
                item.findViewById(R.id.btnDelete).setOnClickListener(v -> {
                    db.deleteSubject(sub.id);
                    refresh();
                });

                container.addView(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not load data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (totalC == 0) {
            tvOverall.setText("Overall: add a subject to begin");
        } else {
            tvOverall.setText(String.format(Locale.US, "Overall: %.1f%%  |  Target: %d%%",
                    AttendanceMath.percentage(totalA, totalC), target));
        }
    }

    private void update(int id, int attended, int conducted) {
        try {
            db.updateCounts(id, attended, conducted);
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
