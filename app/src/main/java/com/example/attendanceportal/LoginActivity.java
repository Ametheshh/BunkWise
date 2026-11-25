package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

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
    private Spinner rolePicker;

    private static class Result {
        static final int OK = 0, INVALID = 1, LOCKED = 2, ERROR = 3;
        int type;
        int minutes;
        long accountId;
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

        rolePicker = findViewById(R.id.loginRole);
        ArrayAdapter<Session.Role> roles = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Session.Role.values());
        roles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rolePicker.setAdapter(roles);
        TextView hint = findViewById(R.id.loginHint);
        rolePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Session.Role role = roles.getItem(position);
                if (role == Session.Role.STUDENT) {
                    hint.setText("Use your USN followed by @nmamit.in. Your first password is your USN.");
                } else if (role == Session.Role.TEACHER) {
                    hint.setText("Use your staff email and password.");
                } else {
                    hint.setText("Use your system admin email and password.");
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
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

        final Session.Role role = (Session.Role) rolePicker.getSelectedItem();
        btnLogin.setEnabled(false);
        rolePicker.setEnabled(false);
        new Thread(() -> {                                   // hashing is deliberately slow: keep it off the UI thread
            final Result r = checkCredentials(email, password, role);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnLogin.setEnabled(true);
                rolePicker.setEnabled(true);
                showResult(r, role);
            });
        }).start();
    }

    private Result checkCredentials(String email, String password, Session.Role role) {
        try {
            AttendanceRepo repo = new AttendanceRepo(this);
            Models.Student s = repo.findAccountByEmail(role, email);
            if (role == Session.Role.STUDENT && s == null) {                                  // nnm... and nmm... are the same student
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
                String candidate = (role != Session.Role.STUDENT || s.passwordChanged) ? password : UsnAlias.initialPasswordCandidate(password, s.usn);
                ok = CryptoUtil.slowEquals(CryptoUtil.hashPassword(candidate, s.salt), s.hash);
            } else {
                CryptoUtil.hashPassword(password, DUMMY_SALT);   // same time cost as a real check
            }

            if (ok) {
                repo.recordLoginState(role, s.id, 0, 0);
                Result result = new Result(Result.OK, 0);
                result.accountId = s.id;
                return result;
            }

            if (s != null) {
                boolean lockExpired = s.lockedUntil != 0 && s.lockedUntil <= now;
                int failed = (lockExpired ? 0 : s.failedLogins) + 1;
                long lockedUntil = 0;
                if (failed >= MAX_FAILED) {
                    lockedUntil = now + LOCK_MS;
                    failed = 0;
                }
                repo.recordLoginState(role, s.id, failed, lockedUntil);
            }
            return new Result(Result.INVALID, 0);
        } catch (Exception e) {
            return new Result(Result.ERROR, 0);
        }
    }

    private void showResult(Result r, Session.Role role) {
        switch (r.type) {
            case Result.OK:
                Session.start(r.accountId, role);
                Class<?> destination;
                switch (role) {
                    case ADMIN: destination = AdminActivity.class; break;
                    case TEACHER: destination = TeacherActivity.class; break;
                    default: destination = com.example.mini_project.HomeActivity.class;
                }
                startActivity(new Intent(this, destination));
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
