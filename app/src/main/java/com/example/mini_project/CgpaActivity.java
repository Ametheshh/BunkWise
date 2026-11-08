package com.example.mini_project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.attendanceportal.BaseActivity;

import java.util.List;
import java.util.Locale;

public class CgpaActivity extends BaseActivity {

    private DBHelper db;
    private LinearLayout rows, semContainer;
    private TextView tvCgpa, tvCredits, tvSgpa, tvPlan;
    private EditText etSemName, etTargetCgpa, etNextCredits;

    private double lastCredits, lastWeighted;      // result of the latest SGPA calculation
    private double savedCgpa = -1, savedCredits;   // from saved semesters

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;       // no active login: BaseActivity already sent the user to the login screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_cgpa);

        db = new DBHelper(this);
        rows = findViewById(R.id.rows);
        semContainer = findViewById(R.id.semContainer);
        tvCgpa = findViewById(R.id.tvCgpa);
        tvCredits = findViewById(R.id.tvCredits);
        tvSgpa = findViewById(R.id.tvSgpa);
        tvPlan = findViewById(R.id.tvPlan);
        etSemName = findViewById(R.id.etSemName);
        etTargetCgpa = findViewById(R.id.etTargetCgpa);
        etNextCredits = findViewById(R.id.etNextCredits);

        findViewById(R.id.btnAddRow).setOnClickListener(v -> addRow());
        findViewById(R.id.btnCalc).setOnClickListener(v -> calculate());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveSemester());
        findViewById(R.id.btnPlan).setOnClickListener(v -> plan());

        for (int i = 0; i < 3; i++) addRow();
        refresh();
    }

    // ------------------------------------------------------------ SGPA rows
    private void addRow() {
        final View row = LayoutInflater.from(this).inflate(R.layout.item_grade_row, rows, false);
        row.findViewById(R.id.btnRemove).setOnClickListener(v -> rows.removeView(row));
        rows.addView(row);
    }

    /** Reads all rows, validates them and shows the SGPA. Returns true on success. */
    private boolean calculate() {
        double credits = 0, weighted = 0;
        int used = 0;

        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            EditText etC = row.findViewById(R.id.etCredits);
            EditText etG = row.findViewById(R.id.etGrade);
            String cs = etC.getText().toString().trim();
            String gs = etG.getText().toString().trim();
            if (cs.isEmpty() && gs.isEmpty()) continue;   // ignore blank rows

            double c, g;
            try { c = Double.parseDouble(cs); }
            catch (NumberFormatException e) { etC.setError("Enter credits"); return false; }
            try { g = Double.parseDouble(gs); }
            catch (NumberFormatException e) { etG.setError("Enter grade point"); return false; }

            if (!CgpaMath.validCredits(c)) { etC.setError("Above 0, up to 10"); return false; }
            if (!CgpaMath.validGradePoint(g)) { etG.setError("0 to 10"); return false; }

            credits += c;
            weighted += c * g;
            used++;
        }

        if (used == 0) {
            Toast.makeText(this, "Enter at least one subject", Toast.LENGTH_SHORT).show();
            return false;
        }

        lastCredits = credits;
        lastWeighted = weighted;
        tvSgpa.setText(String.format(Locale.US, "SGPA: %.2f   (%.1f credits)",
                weighted / credits, credits));
        return true;
    }

    private void saveSemester() {
        String name = etSemName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 30) {
            etSemName.setError("Name required (max 30 characters)");
            return;
        }
        if (!calculate()) return;   // always save fresh numbers, never stale ones

        try {
            db.addSemester(name, lastCredits, lastWeighted);
        } catch (Exception e) {
            Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        etSemName.setText("");
        rows.removeAllViews();
        for (int i = 0; i < 3; i++) addRow();
        tvSgpa.setText("SGPA: --");
        refresh();
        Toast.makeText(this, "Semester saved", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ saved semesters + CGPA
    private void refresh() {
        semContainer.removeAllViews();
        double totalC = 0, totalW = 0;

        try {
            List<Semester> list = db.getSemesters();
            LayoutInflater inflater = LayoutInflater.from(this);

            for (Semester s : list) {
                totalC += s.credits;
                totalW += s.weighted;

                View item = inflater.inflate(R.layout.item_semester, semContainer, false);
                ((TextView) item.findViewById(R.id.tvSemName)).setText(s.name);
                ((TextView) item.findViewById(R.id.tvSemStats)).setText(
                        String.format(Locale.US, "SGPA %.2f   |   %.1f credits", s.sgpa(), s.credits));

                final Semester sem = s;
                item.findViewById(R.id.btnSemDelete).setOnClickListener(v -> {
                    db.deleteSemester(sem.id);
                    refresh();
                });
                semContainer.addView(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not load data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        savedCredits = totalC;
        savedCgpa = totalC > 0 ? totalW / totalC : -1;
        tvCgpa.setText(savedCgpa < 0 ? "--" : String.format(Locale.US, "%.2f", savedCgpa));
        tvCredits.setText(String.format(Locale.US, "%.1f credits completed", totalC));
    }

    // ------------------------------------------------------------ target planner
    private void plan() {
        if (savedCgpa < 0) {
            tvPlan.setText("Save at least one semester first.");
            return;
        }
        double target, next;
        try {
            target = Double.parseDouble(etTargetCgpa.getText().toString().trim());
            if (target <= 0 || target > 10) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etTargetCgpa.setError("Enter 0.1 to 10");
            return;
        }
        try {
            next = Double.parseDouble(etNextCredits.getText().toString().trim());
            if (next <= 0 || next > 40) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etNextCredits.setError("Enter credits (1 to 40)");
            return;
        }

        double req = CgpaMath.requiredSgpa(savedCgpa, savedCredits, target, next);
        double best = CgpaMath.bestPossibleCgpa(savedCgpa, savedCredits, next);

        if (req <= 0) {
            tvPlan.setText(String.format(Locale.US,
                    "Target %.2f is already secured. Your CGPA stays at or above it next semester.", target));
        } else if (req > 10) {
            tvPlan.setText(String.format(Locale.US,
                    "Not reachable in one semester: it needs an SGPA of %.2f. "
                  + "A perfect 10 would give a CGPA of %.2f, so plan across more semesters.", req, best));
        } else {
            tvPlan.setText(String.format(Locale.US,
                    "You need an SGPA of at least %.2f across %.1f credits to reach a CGPA of %.2f.",
                    req, next, target));
        }
    }
}
