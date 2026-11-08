package com.example.attendanceportal;

import com.example.mini_project.R;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class ChangePasswordActivity extends BaseActivity {

    private TextInputLayout tilCurrent, tilNew, tilConfirm;
    private EditText etCurrent, etNew, etConfirm;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;
        setContentView(R.layout.activity_change_password);

        tilCurrent = findViewById(R.id.tilCurrent);
        tilNew = findViewById(R.id.tilNew);
        tilConfirm = findViewById(R.id.tilConfirm);
        etCurrent = findViewById(R.id.etCurrent);
        etNew = findViewById(R.id.etNew);
        etConfirm = findViewById(R.id.etConfirm);
        btnSave = findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        tilCurrent.setError(null);
        tilNew.setError(null);
        tilConfirm.setError(null);

        final String current = etCurrent.getText().toString();
        final String newPw = etNew.getText().toString();
        final String confirm = etConfirm.getText().toString();

        if (current.isEmpty() || current.length() > 128) {
            tilCurrent.setError("Enter your current password");
            return;
        }
        String problem = validateNewPassword(newPw, confirm, current);
        if (problem != null) {
            tilNew.setError(problem);
            return;
        }

        btnSave.setEnabled(false);
        new Thread(() -> {
            String error = null;
            try {
                AttendanceRepo repo = new AttendanceRepo(this);
                Models.Student me = repo.getProfile();
                String candidate = me.passwordChanged ? current : UsnAlias.initialPasswordCandidate(current, me.usn);
                boolean ok = CryptoUtil.slowEquals(CryptoUtil.hashPassword(candidate, me.salt), me.hash);
                if (!ok) {
                    error = "CURRENT";
                } else if (isIdentity(newPw, me)) {
                    error = "New password cannot be your USN or email.";
                } else {
                    String salt = CryptoUtil.newSalt();
                    repo.updateMyPassword(salt, CryptoUtil.hashPassword(newPw, salt));
                }
            } catch (Exception e) {
                error = "Could not save the password. Please try again.";
            }
            final String result = error;
            runOnUiThread(() -> {
                btnSave.setEnabled(true);
                if (result == null) {
                    Toast.makeText(this, "Password changed", Toast.LENGTH_LONG).show();
                    finish();
                } else if ("CURRENT".equals(result)) {
                    tilCurrent.setError("Current password is incorrect");
                } else {
                    tilNew.setError(result);
                }
            });
        }).start();
    }

    private static String validateNewPassword(String pw, String confirm, String current) {
        if (pw.length() < 8) return "Use at least 8 characters.";
        if (pw.length() > 128) return "That password is too long.";
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) return "Include at least one letter and one number.";
        if (!pw.equals(confirm)) return "New password and confirmation do not match.";
        if (pw.equals(current)) return "New password must be different from the current one.";
        return null;
    }

    private static boolean isIdentity(String pw, Models.Student me) {
        String p = pw.toLowerCase(Locale.ROOT);
        String usn = me.usn.toLowerCase(Locale.ROOT);
        String usnOtherSpelling = UsnAlias.swapPrefix(usn);
        return p.equals(usn) || p.equals(usnOtherSpelling) || p.equals(me.email.toLowerCase(Locale.ROOT));
    }
}
