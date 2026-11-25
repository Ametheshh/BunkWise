package com.example.attendanceportal;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Every screen after login extends this: blocks screenshots and requires an active session. */
public abstract class BaseActivity extends AppCompatActivity {

    /** False when the session is gone; subclasses must stop in onCreate if so. */
    protected boolean sessionOk = true;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleLogout = () -> {
        if (sessionOk && !allowsSession()) expireSession();
    };

    protected boolean allowsSession() {
        return Session.hasRole(Session.Role.STUDENT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (!allowsSession()) {
            sessionOk = false;
            goToLogin("Please log in");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionOk) return;
        if (!allowsSession()) {
            expireSession();
        } else {
            resetIdleTimeout();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!sessionOk) return;
        if (allowsSession()) {
            resetIdleTimeout();
        } else {
            expireSession();
        }
    }

    @Override
    protected void onPause() {
        idleHandler.removeCallbacks(idleLogout);
        super.onPause();
    }

    private void resetIdleTimeout() {
        Session.touch();
        idleHandler.removeCallbacks(idleLogout);
        idleHandler.postDelayed(idleLogout, Session.IDLE_MS);
    }

    private void expireSession() {
        goToLogin("Session expired. Please log in again.");
    }

    protected void goToLogin(String message) {
        sessionOk = false;
        idleHandler.removeCallbacks(idleLogout);
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
