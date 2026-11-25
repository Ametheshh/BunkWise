package com.example.attendanceportal;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.List;

final class SchoolScreen {
    private SchoolScreen() { }

    static LinearLayout open(BaseActivity activity, String title) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        activity.setContentView(scroll);
        heading(body, title).setTextSize(27);
        return body;
    }

    static TextView text(LinearLayout body, String label) {
        TextView text = new TextView(body.getContext());
        text.setText(label);
        text.setTextSize(16);
        body.addView(text);
        return text;
    }

    static TextView heading(LinearLayout body, String label) {
        TextView heading = text(body, label);
        heading.setTextSize(20);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        ViewCompat.setAccessibilityHeading(heading, true);
        heading.setPadding(0, Math.round(12 * body.getResources().getDisplayMetrics().density), 0, 0);
        return heading;
    }

    static MaterialButton button(LinearLayout body, String label, Runnable action) {
        MaterialButton button = new MaterialButton(body.getContext());
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        body.addView(button);
        return button;
    }

    static Spinner spinner(LinearLayout body, String label, List<?> choices) {
        TextView caption = text(body, label);
        Spinner spinner = new Spinner(body.getContext());
        spinner.setId(View.generateViewId());
        caption.setLabelFor(spinner.getId());
        ArrayAdapter<?> adapter = new ArrayAdapter<>(body.getContext(),
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(Math.round(48 * body.getResources().getDisplayMetrics().density));
        body.addView(spinner);
        return spinner;
    }
}
