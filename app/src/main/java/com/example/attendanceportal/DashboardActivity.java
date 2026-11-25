package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

public class DashboardActivity extends BaseActivity {

    private TextView tvWho, tvMeta, tvOverallPct, tvOverallBadge, tvOverallDetail, tvOverallNeed, tvShortage;
    private View cardDefaultPw, cardShortage;
    private MaterialCardView cardOverall;
    private LinearProgressIndicator pbOverall;
    private LinearLayout subjectsContainer, recentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;
        setContentView(R.layout.activity_attendance_dashboard);

        tvWho = findViewById(R.id.tvWho);
        tvMeta = findViewById(R.id.tvMeta);
        cardDefaultPw = findViewById(R.id.cardDefaultPw);
        cardOverall = findViewById(R.id.cardOverall);
        tvOverallPct = findViewById(R.id.tvOverallPct);
        tvOverallBadge = findViewById(R.id.tvOverallBadge);
        pbOverall = findViewById(R.id.pbOverall);
        tvOverallDetail = findViewById(R.id.tvOverallDetail);
        tvOverallNeed = findViewById(R.id.tvOverallNeed);
        tvShortage = findViewById(R.id.tvShortage);
        cardShortage = tvShortage;            // the red notice is the TextView itself
        subjectsContainer = findViewById(R.id.subjectsContainer);
        recentContainer = findViewById(R.id.recentContainer);

        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        findViewById(R.id.btnChangeFromBanner).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.btnPassword).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
    }

    // Reload every time we come back (for example after changing the password)
    @Override
    protected void onResume() {
        super.onResume();
        if (sessionOk && Session.isActive()) render();
    }

    private void render() {
        AttendanceRepo repo = new AttendanceRepo(this);
        Models.Student me = repo.getProfile();
        int threshold = repo.getThreshold();
        Models.Overall overall = repo.getOverall();
        List<Models.SubjectSummary> subjects = repo.getSubjects();

        // ---- who
        tvWho.setText("Hello, " + titleCase(me.name));
        tvMeta.setText(me.usn + "  |  " + me.email + "  |  Lab batch " + me.labBatch);
        cardDefaultPw.setVisibility(me.passwordChanged ? View.GONE : View.VISIBLE);

        // ---- overall
        if (overall == null) {
            tvOverallPct.setText("--");
            tvOverallBadge.setVisibility(View.GONE);
            tvOverallDetail.setText("No attendance has been recorded for you yet.");
            tvOverallNeed.setVisibility(View.GONE);
            pbOverall.setProgress(0);
        } else {
            boolean ok = !overall.isShortage();
            int color = Ui.statusColor(this, ok);
            tvOverallPct.setText(Ui.pct(overall.pct));
            Ui.badge(tvOverallBadge, ok, overall.eligibility);
            pbOverall.setIndicatorColor(color);
            pbOverall.setProgress((int) Math.round(overall.pct));
            cardOverall.setStrokeColor(color);
            tvOverallDetail.setText(String.format(Locale.US,
                    "Attended %d of %d classes across all subjects.", overall.attended, overall.conducted));
            if (overall.isShortage()) {
                tvOverallNeed.setText("You need " + Ui.classes(overall.needed)
                        + " in a row to reach " + threshold + "% overall.");
                tvOverallNeed.setVisibility(View.VISIBLE);
            } else {
                tvOverallNeed.setVisibility(View.GONE);
            }
        }

        // ---- subjects below the threshold
        StringBuilder sb = new StringBuilder();
        int shortageCount = 0;
        for (Models.SubjectSummary s : subjects) {
            if (s.isShortage()) {
                shortageCount++;
                sb.append("\n\u2022 ").append(s.name).append(" is at ").append(Ui.pct(s.pct))
                  .append(". Attend the next ").append(Ui.classes(s.needed)).append(" in a row to reach ")
                  .append(threshold).append("%.");
            }
        }
        if (shortageCount > 0) {
            tvShortage.setText("Attendance below " + threshold + "% in " + shortageCount
                    + (shortageCount == 1 ? " subject:" : " subjects:") + sb);
            cardShortage.setVisibility(View.VISIBLE);
        } else {
            cardShortage.setVisibility(View.GONE);
        }

        // ---- subject cards
        subjectsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Models.SubjectSummary s : subjects) {
            MaterialCardView card = (MaterialCardView) inflater.inflate(R.layout.item_subject_card, subjectsContainer, false);
            ((TextView) card.findViewById(R.id.tvSubName)).setText(s.name);
            ((TextView) card.findViewById(R.id.tvSubMeta)).setText(s.code + "  |  " + s.type);
            TextView tvPct = card.findViewById(R.id.tvSubPct);
            LinearProgressIndicator pb = card.findViewById(R.id.pbSub);
            TextView tvCounts = card.findViewById(R.id.tvSubCounts);
            TextView tvBadge = card.findViewById(R.id.tvSubBadge);
            TextView tvAdvice = card.findViewById(R.id.tvSubAdvice);

            if (s.hasClasses()) {
                boolean ok = !s.isShortage();
                int color = Ui.statusColor(this, ok);
                tvPct.setText(Ui.pct(s.pct));
                pb.setIndicatorColor(color);
                pb.setProgress((int) Math.round(s.pct));
                tvCounts.setText("Attended " + s.attended + " of " + s.conducted + " classes");
                Ui.badge(tvBadge, ok, s.eligibility);
                if (!ok) {
                    card.setStrokeColor(color);
                    card.setStrokeWidth(4);
                    tvAdvice.setTextColor(color);
                } 
            } else {
                tvPct.setText("--");
                pb.setVisibility(View.GONE);
                tvCounts.setText("No classes yet");
                tvBadge.setVisibility(View.GONE);
            }
            tvAdvice.setText(Ui.advice(s, threshold));

            final String code = s.code;
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, SubjectActivity.class);
                i.putExtra(SubjectActivity.EXTRA_CODE, code);     // a subject code only, never a student id
                startActivity(i);
            });
            subjectsContainer.addView(card);
        }

        // ---- recent attendance
        recentContainer.removeAllViews();
        List<Models.HistoryItem> recent = repo.getHistory(null, null, null, 8);
        if (recent.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Nothing recorded yet.");
            recentContainer.addView(none);
        }
        for (Models.HistoryItem h : recent) {
            View row = inflater.inflate(R.layout.item_history_row, recentContainer, false);
            Ui.bindHistoryRow(row, h);
            recentContainer.addView(row);
        }
    }

    private static String titleCase(String name) {
        StringBuilder out = new StringBuilder();
        for (String part : name.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
