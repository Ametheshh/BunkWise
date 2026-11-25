package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
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
    private TextView tvError, tvImportStatus;
    private Button btnImport;

    /** Several screenshots, because the Uniclare list usually needs scrolling. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickScreenshots = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS), this::importScreenshots);
    private static final int MAX_SCREENSHOTS = 5;

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
        tvImportStatus = findViewById(R.id.tvImportStatus);
        btnImport = findViewById(R.id.btnImportScreenshots);
        btnImport.setOnClickListener(v -> pickScreenshots.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
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
        clearErrorOnEdit(row.findViewById(R.id.tilConducted));
        clearErrorOnEdit(row.findViewById(R.id.tilAttended));
        rows.addView(row);
        return row;
    }

    private void importScreenshots(List<Uri> images) {
        if (images == null || images.isEmpty()) return;
        btnImport.setEnabled(false);
        tvError.setVisibility(View.GONE);
        showImportStatus("Reading " + images.size() + (images.size() == 1 ? " screenshot…" : " screenshots…"), false);
        ScreenshotImporter.read(this, images, new ScreenshotImporter.Callback() {
            @Override public void onDone(UniclareScreenshotParser.Result result) {
                if (isFinishing() || isDestroyed()) return;
                btnImport.setEnabled(true);
                applyImport(result, images.size());
            }

            @Override public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                btnImport.setEnabled(true);
                showImportStatus("Could not read the screenshot. Try again, or type the numbers in.", true);
            }
        });
    }

    /** Fills the form from the screenshots. Nothing is saved until the student taps Save. */
    private void applyImport(UniclareScreenshotParser.Result result, int screenshots) {
        if (result.rows.isEmpty()) {
            showImportStatus("No subjects found. Use a screenshot of Uniclare's Attendance Summary list.", true);
            return;
        }
        if (etUsn.getText().toString().trim().isEmpty() && result.usn != null) etUsn.setText(result.usn);
        if (etName.getText().toString().trim().isEmpty() && result.name != null) etName.setText(result.name);

        int filled = 0, unsure = 0, skipped = 0;
        for (UniclareScreenshotParser.Row r : result.rows) {
            View row = findRow(r.name, r.practical);
            if (row == null) {
                if (rows.getChildCount() >= RealAttendance.MAX_SUBJECTS) {
                    skipped++;
                    continue;
                }
                row = addRow(null);
            }
            ((EditText) row.findViewById(R.id.etSubName)).setText(r.name);
            ((CheckBox) row.findViewById(R.id.cbPractical)).setChecked(r.practical);
            ((EditText) row.findViewById(R.id.etConducted)).setText(String.valueOf(r.conducted));
            ((EditText) row.findViewById(R.id.etAttended)).setText(String.valueOf(r.attended));
            String check = r.confident ? null : "Check this";
            ((TextInputLayout) row.findViewById(R.id.tilConducted)).setError(check);
            ((TextInputLayout) row.findViewById(R.id.tilAttended)).setError(check);
            filled++;
            if (!r.confident) unsure++;
        }

        StringBuilder msg = new StringBuilder("Filled ").append(filled)
                .append(filled == 1 ? " subject" : " subjects").append(" from ").append(screenshots)
                .append(screenshots == 1 ? " screenshot." : " screenshots.");
        if (unsure > 0) msg.append(' ').append(unsure).append(unsure == 1 ? " needs" : " need")
                .append(" checking: the numbers did not match the percentage.");
        if (skipped > 0) msg.append(' ').append(skipped).append(" skipped: at most ")
                .append(RealAttendance.MAX_SUBJECTS).append(" subjects.");
        msg.append(" Compare with Uniclare, then tap Save.");
        showImportStatus(msg.toString(), unsure > 0 || skipped > 0);
    }

    /** An existing row for this subject, else an empty row to reuse, else null. */
    private View findRow(String name, boolean practical) {
        View blank = null;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            String n = ((EditText) row.findViewById(R.id.etSubName)).getText().toString().trim();
            boolean p = ((CheckBox) row.findViewById(R.id.cbPractical)).isChecked();
            if (n.equalsIgnoreCase(name) && p == practical) return row;
            if (blank == null && n.isEmpty()
                    && ((EditText) row.findViewById(R.id.etConducted)).getText().toString().trim().isEmpty()
                    && ((EditText) row.findViewById(R.id.etAttended)).getText().toString().trim().isEmpty()) {
                blank = row;
            }
        }
        return blank;
    }

    private void showImportStatus(String message, boolean warn) {
        tvImportStatus.setText(message);
        tvImportStatus.setTextColor(MaterialColors.getColor(tvImportStatus,
                warn ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurfaceVariant));
        tvImportStatus.setVisibility(View.VISIBLE);
    }

    private static void clearErrorOnEdit(TextInputLayout til) {
        if (til.getEditText() == null) return;
        til.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { if (til.getError() != null && s.length() > 0 && til.hasFocus()) til.setError(null); }
        });
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
