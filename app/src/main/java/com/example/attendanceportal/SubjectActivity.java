package com.example.attendanceportal;

import com.example.mini_project.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

public class SubjectActivity extends BaseActivity {

    public static final String EXTRA_CODE = "subject_code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;
        setContentView(R.layout.activity_subject);

        AttendanceRepo repo = new AttendanceRepo(this);
        String code = getIntent().getStringExtra(EXTRA_CODE);
        Models.SubjectSummary s = repo.getSubject(code);     // null unless the logged-in student takes this subject
        if (s == null) {
            Toast.makeText(this, "Subject not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        int threshold = repo.getThreshold();

        ((TextView) findViewById(R.id.tvSubjTitle)).setText(s.name);
        ((TextView) findViewById(R.id.tvSubjMeta)).setText(s.code + "  |  " + s.type);

        MaterialCardView card = findViewById(R.id.cardSubj);
        TextView tvPct = findViewById(R.id.tvSubjPct);
        TextView tvBadge = findViewById(R.id.tvSubjBadge);
        LinearProgressIndicator pb = findViewById(R.id.pbSubj);
        TextView tvCounts = findViewById(R.id.tvSubjCounts);
        TextView tvAdvice = findViewById(R.id.tvSubjAdvice);

        if (s.hasClasses()) {
            boolean ok = !s.isShortage();
            int color = Ui.statusColor(this, ok);
            tvPct.setText(Ui.pct(s.pct));
            Ui.badge(tvBadge, ok, s.eligibility);
            pb.setIndicatorColor(color);
            pb.setProgress((int) Math.round(s.pct));
            card.setStrokeColor(color);
            tvAdvice.setTextColor(color);
        } else {
            tvPct.setText("--");
            tvBadge.setVisibility(View.GONE);
            pb.setVisibility(View.GONE);
        }
        tvCounts.setText(String.format(Locale.US,
                "Classes conducted: %d   |   Classes attended: %d   |   Missed: %d",
                s.conducted, s.attended, s.conducted - s.attended));
        tvAdvice.setText(Ui.advice(s, threshold));

        LayoutInflater inflater = LayoutInflater.from(this);

        // ---- month by month
        LinearLayout months = findViewById(R.id.monthsContainer);
        for (Models.MonthStat m : repo.getMonthly(s.code)) {
            View row = inflater.inflate(R.layout.item_month_row, months, false);
            ((TextView) row.findViewById(R.id.tvMMonth)).setText(Ui.niceMonth(m.month));
            ((TextView) row.findViewById(R.id.tvMConducted)).setText(String.valueOf(m.conducted));
            ((TextView) row.findViewById(R.id.tvMAttended)).setText(String.valueOf(m.attended));
            ((TextView) row.findViewById(R.id.tvMPct)).setText(Ui.pct(m.pct()));
            months.addView(row);
        }

        // ---- daily history for this subject
        LinearLayout history = findViewById(R.id.historyContainer);
        List<Models.HistoryItem> items = repo.getHistory(s.code, null, null, 500);
        for (Models.HistoryItem h : items) {
            View row = inflater.inflate(R.layout.item_history_row, history, false);
            Ui.bindHistoryRow(row, h);
            history.addView(row);
        }
    }
}
