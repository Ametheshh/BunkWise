package com.example.mini_project;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ANSWER_ATTEMPTS = 3;

    private EditText etPin, etQuestion, etAnswer;
    private TextView tvTitle, tvForgot;
    private Button btnAction;
    private View tilQuestion, tilAnswer;
    private CheckBox cbShow;
    private SharedPreferences prefs;
    private int attempts = 0;
    private int answerAttempts = 0;
    private boolean firstTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Blocks screenshots and hides content in the recent-apps screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_login);

        etPin = findViewById(R.id.etPin);
        etQuestion = findViewById(R.id.etQuestion);
        etAnswer = findViewById(R.id.etAnswer);
        tilQuestion = findViewById(R.id.tilQuestion);
        tilAnswer = findViewById(R.id.tilAnswer);
        tvTitle = findViewById(R.id.tvTitle);
        tvForgot = findViewById(R.id.tvForgot);
        btnAction = findViewById(R.id.btnAction);
        cbShow = findViewById(R.id.cbShow);

        prefs = getSharedPreferences("secure_prefs", MODE_PRIVATE);
        firstTime = !prefs.contains("pin_hash");

        if (firstTime) {
            tvTitle.setText("Create a 4-6 digit PIN");
            btnAction.setText("Set PIN");
            tilQuestion.setVisibility(View.VISIBLE);
            tilAnswer.setVisibility(View.VISIBLE);
            tvForgot.setVisibility(View.GONE);
        } else {
            tvTitle.setText("Enter PIN to unlock");
            btnAction.setText("Unlock");
            tilQuestion.setVisibility(View.GONE);
            tilAnswer.setVisibility(View.GONE);
            tvForgot.setVisibility(View.VISIBLE);
        }

        cbShow.setOnCheckedChangeListener((b, checked) -> {
            etPin.setInputType(InputType.TYPE_CLASS_NUMBER |
                    (checked ? InputType.TYPE_NUMBER_VARIATION_NORMAL
                             : InputType.TYPE_NUMBER_VARIATION_PASSWORD));
            etPin.setSelection(etPin.getText().length());
        });

        btnAction.setOnClickListener(v -> handleClick());
        tvForgot.setOnClickListener(v -> forgotPin());
    }

    // ---------------------------------------------------------------- login / setup
    private void handleClick() {
        String pin = etPin.getText().toString().trim();
        if (!pin.matches("\\d{4,6}")) {
            etPin.setError("PIN must be 4 to 6 digits");
            return;
        }
        try {
            if (firstTime) {
                String question = etQuestion.getText().toString().trim();
                String answer = normalise(etAnswer.getText().toString());
                if (question.length() < 5) {
                    etQuestion.setError("Write a recovery question (min 5 characters)");
                    return;
                }
                if (answer.length() < 3) {
                    etAnswer.setError("Answer must be at least 3 characters");
                    return;
                }
                String pinSalt = CryptoUtil.newSalt();
                String ansSalt = CryptoUtil.newSalt();
                prefs.edit()
                     .putString("pin_salt", pinSalt)
                     .putString("pin_hash", CryptoUtil.hashPin(pin, pinSalt))
                     .putString("sec_question", question)
                     .putString("sec_salt", ansSalt)
                     .putString("sec_hash", CryptoUtil.hashPin(answer, ansSalt)) // answer is hashed, never stored as text
                     .apply();
                Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show();
                openDashboard();
            } else {
                String salt = prefs.getString("pin_salt", "");
                String saved = prefs.getString("pin_hash", "");
                if (CryptoUtil.slowEquals(saved, CryptoUtil.hashPin(pin, salt))) {
                    openDashboard();
                } else {
                    attempts++;
                    etPin.setText("");
                    if (attempts >= MAX_ATTEMPTS) {
                        Toast.makeText(this, "Too many attempts. App closing.",
                                Toast.LENGTH_LONG).show();
                        finishAffinity();
                    } else {
                        Toast.makeText(this, "Wrong PIN. Attempts left: "
                                + (MAX_ATTEMPTS - attempts), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Security error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- forgot PIN
    private void forgotPin() {
        // PIN was created before the recovery question existed: only erase is possible
        if (!prefs.contains("sec_hash")) {
            new AlertDialog.Builder(this)
                    .setTitle("Forgot PIN")
                    .setMessage("No recovery question was set for this PIN.\n\n"
                            + "You can erase all app data and start fresh.")
                    .setPositiveButton("Erase all data", (d, w) -> confirmErase())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        final EditText input = new EditText(this);
        input.setHint("Your answer");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        input.setPadding(56, 24, 56, 24);

        new AlertDialog.Builder(this)
                .setTitle("Forgot PIN")
                .setMessage(prefs.getString("sec_question", ""))
                .setView(input)
                .setPositiveButton("Verify", (d, w) -> verifyAnswer(input.getText().toString()))
                .setNeutralButton("Erase all data", (d, w) -> confirmErase())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyAnswer(String answer) {
        try {
            String h = CryptoUtil.hashPin(normalise(answer), prefs.getString("sec_salt", ""));
            if (CryptoUtil.slowEquals(h, prefs.getString("sec_hash", ""))) {
                showNewPinDialog();
            } else {
                answerAttempts++;
                if (answerAttempts >= MAX_ANSWER_ATTEMPTS) {
                    Toast.makeText(this, "Too many wrong answers. App closing.",
                            Toast.LENGTH_LONG).show();
                    finishAffinity();
                } else {
                    Toast.makeText(this, "Wrong answer. Attempts left: "
                            + (MAX_ANSWER_ATTEMPTS - answerAttempts), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Security error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNewPinDialog() {
        final EditText input = new EditText(this);
        input.setHint("New 4-6 digit PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        input.setPadding(56, 24, 56, 24);

        new AlertDialog.Builder(this)
                .setTitle("Answer correct. Set a new PIN")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Save PIN", (d, w) -> {
                    String pin = input.getText().toString().trim();
                    if (!pin.matches("\\d{4,6}")) {
                        Toast.makeText(this, "PIN must be 4 to 6 digits", Toast.LENGTH_SHORT).show();
                        showNewPinDialog();
                        return;
                    }
                    try {
                        String salt = CryptoUtil.newSalt();
                        prefs.edit()
                             .putString("pin_salt", salt)
                             .putString("pin_hash", CryptoUtil.hashPin(pin, salt))
                             .apply();
                        attempts = 0;
                        answerAttempts = 0;
                        Toast.makeText(this, "PIN reset. Your data is safe.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Security error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- erase everything
    private void confirmErase() {
        new AlertDialog.Builder(this)
                .setTitle("Erase all data?")
                .setMessage("This permanently deletes all subjects, records and your PIN. "
                        + "It cannot be undone.")
                .setPositiveButton("Erase", (d, w) -> eraseAll())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void eraseAll() {
        try {
            deleteRecursive(new java.io.File(getFilesDir(), "vault")); // encrypted documents
            deleteDatabase("securecampus.db");   // encrypted records
            CryptoUtil.deleteKey();              // destroys the AES key, so any leftovers are unreadable
            prefs.edit().clear().apply();        // PIN hash, salt, recovery question, settings
            Toast.makeText(this, "All data erased. Set up a new PIN.", Toast.LENGTH_LONG).show();
            recreate();                          // shows the first-time setup again
        } catch (Exception e) {
            Toast.makeText(this, "Could not erase: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- helpers
    /** Makes the answer case- and space-insensitive so "Hubli" and " hubli " match. */
    private String normalise(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private void deleteRecursive(java.io.File f) {
        if (f == null || !f.exists()) return;
        java.io.File[] kids = f.listFiles();
        if (kids != null) for (java.io.File k : kids) deleteRecursive(k);
        f.delete();
    }

    private void openDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
