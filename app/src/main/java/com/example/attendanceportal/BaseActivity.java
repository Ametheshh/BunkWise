package com.example.attendanceportal;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Every screen after login extends this: blocks screenshots and requires an active session. */
public abstract class BaseActivity extends AppCompatActivity {

    /** False when the session is gone; subclasses must stop in onCreate if so. */
    protected boolean sessionOk = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (!Session.isActive()) {
            sessionOk = false;
            goToLogin("Please log in");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Session.isActive()) {
            goToLogin("Session expired. Please log in again.");
        } else {
            Session.touch();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        Session.touch();
    }

    protected void goToLogin(String message) {
        Session.end();
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    protected void logout() {
        goToLogin("Logged out");
    }
}
