package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final int MAX_FAILED = 5;
    private static final long LOCK_MS = 15 * 60 * 1000L;
    private static final String DUMMY_SALT = "AAAAAAAAAAAAAAAAAAAAAA==";   // used when the email is unknown

    private TextInputLayout tilEmail, tilPassword;
    private EditText etEmail, etPassword;
    private Button btnLogin;

    private static class Result {
        static final int OK = 0, INVALID = 1, LOCKED = 2, ERROR = 3;
        int type;
        int minutes;
        Result(int type, int minutes) { this.type = type; this.minutes = minutes; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_attendance_login);

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        tilEmail.setError(null);
        tilPassword.setError(null);

        final String email = etEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        final String password = etPassword.getText().toString();

        // Input validation first
        if (email.length() > 254 || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter your institutional email");
            return;
        }
        if (password.isEmpty() || password.length() > 128) {
            tilPassword.setError("Enter your password");
            return;
        }

        btnLogin.setEnabled(false);
        new Thread(() -> {                                   // hashing is deliberately slow: keep it off the UI thread
            final Result r = checkCredentials(email, password);
            runOnUiThread(() -> {
                btnLogin.setEnabled(true);
                showResult(r);
            });
        }).start();
    }

    private Result checkCredentials(String email, String password) {
        try {
            AttendanceRepo repo = new AttendanceRepo(this);
            Models.Student s = repo.findStudentByEmail(email);
            if (s == null) {                                  // nnm... and nmm... are the same student
                String alt = UsnAlias.swapPrefix(email);
                if (alt != null) s = repo.findStudentByEmail(alt);
            }
            long now = System.currentTimeMillis();

            if (s != null && s.lockedUntil > now) {
                return new Result(Result.LOCKED, (int) ((s.lockedUntil - now) / 60000) + 1);
            }

            boolean ok = false;
            if (s != null) {
                // While the password is still the initial one (the lowercase USN) accept any letter case
                String candidate = s.passwordChanged ? password : UsnAlias.initialPasswordCandidate(password, s.usn);
                ok = CryptoUtil.slowEquals(CryptoUtil.hashPassword(candidate, s.salt), s.hash);
            } else {
                CryptoUtil.hashPassword(password, DUMMY_SALT);   // same time cost as a real check
            }

            if (ok) {
                repo.recordSuccessfulLogin(s.id);
                Session.start(s.id);
                return new Result(Result.OK, 0);
            }

            if (s != null) {
                boolean lockExpired = s.lockedUntil != 0 && s.lockedUntil <= now;
                int failed = (lockExpired ? 0 : s.failedLogins) + 1;
                long lockedUntil = 0;
                if (failed >= MAX_FAILED) {
                    lockedUntil = now + LOCK_MS;
                    failed = 0;
                }
                repo.recordFailedLogin(s.id, failed, lockedUntil);
            }
            return new Result(Result.INVALID, 0);
        } catch (Exception e) {
            return new Result(Result.ERROR, 0);
        }
    }

    private void showResult(Result r) {
        switch (r.type) {
            case Result.OK:
                startActivity(new Intent(this, com.example.mini_project.HomeActivity.class));
                finish();
                break;
            case Result.LOCKED:
                tilPassword.setError("Too many failed attempts. Try again in about " + r.minutes + " minute(s).");
                break;
            case Result.INVALID:
                // Same message for a wrong email and a wrong password
                tilPassword.setError("Invalid email or password.");
                etPassword.setText("");
                break;
            default:
                tilPassword.setError("Something went wrong. Please try again.");
        }
    }
}
