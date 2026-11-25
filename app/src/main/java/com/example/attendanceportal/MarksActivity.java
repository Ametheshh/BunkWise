package com.example.attendanceportal;

import com.example.mini_project.R;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Student's CIE marks: a summary, a warning for low attendance, then one compact card per subject. */
public final class MarksActivity extends BaseActivity {

    private static final class SubjectMarks {
        long subjectId;
        String code, name, source;
        boolean hasCie;
        double mse1, mse2, la1, la2, la3, total;
        int finalCie;
        final List<String> teacherEntries = new ArrayList<>();
    }

    private final NumberFormat num = NumberFormat.getNumberInstance();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!sessionOk) return;
        setContentView(R.layout.activity_marks);
        num.setMaximumFractionDigits(2);
        findViewById(R.id.btnMarksBack).setOnClickListener(v -> finish());

        if (AttendanceDb.current() == AttendanceDb.Source.REAL) {
            showEmpty("Marks are not part of the attendance you entered from Uniclare. "
                    + "Log in to the demo to see how the marks screen works.");
            return;
        }

        List<SubjectMarks> subjects = load(Session.requireStudentId());
        List<SubjectMarks> withCie = new ArrayList<>();
        for (SubjectMarks s : subjects) if (s.hasCie) withCie.add(s);

        if (subjects.isEmpty()) {
            showEmpty("You are not enrolled in any subjects yet.");
            return;
        }
        bindSummary(withCie);
        bindAttendanceWarning();

        LinearLayout container = findViewById(R.id.marksContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SubjectMarks s : subjects) container.addView(card(inflater, container, s));
    }

    private List<SubjectMarks> load(long studentId) {
        Map<Long, SubjectMarks> bySubject = new LinkedHashMap<>();
        String sid = String.valueOf(studentId);
        try (Cursor c = AttendanceDb.get(this).rawQuery(
                "SELECT s.id, s.code, s.name, c.source, c.mse1, c.mse2, c.la1, c.la2, c.la3, c.total, c.final "
                        + "FROM enrollments e JOIN subjects s ON s.id = e.subject_id "
                        + "LEFT JOIN cie_results c ON c.student_id = e.student_id AND c.subject_id = e.subject_id "
                        + "WHERE e.student_id = ? ORDER BY s.name", new String[]{sid})) {
            while (c.moveToNext()) {
                SubjectMarks m = new SubjectMarks();
                m.subjectId = c.getLong(0);
                m.code = c.getString(1);
                m.name = c.getString(2);
                m.hasCie = !c.isNull(3);
                if (m.hasCie) {
                    m.source = c.getString(3);
                    m.mse1 = c.getDouble(4);
                    m.mse2 = c.getDouble(5);
                    m.la1 = c.getDouble(6);
                    m.la2 = c.getDouble(7);
                    m.la3 = c.getDouble(8);
                    m.total = c.getDouble(9);
                    m.finalCie = c.getInt(10);
                }
                bySubject.put(m.subjectId, m);
            }
        }
        try (Cursor c = AttendanceDb.get(this).rawQuery(
                "SELECT subject_id, assessment, score, maximum FROM marks WHERE student_id = ? "
                        + "ORDER BY CASE assessment WHEN 'IA1' THEN 1 WHEN 'IA2' THEN 2 WHEN 'Assignment' THEN 3 "
                        + "WHEN 'Lab' THEN 4 ELSE 5 END", new String[]{sid})) {
            while (c.moveToNext()) {
                SubjectMarks m = bySubject.get(c.getLong(0));
                if (m != null) m.teacherEntries.add(c.getString(1) + "  " + c.getInt(2) + " / " + c.getInt(3));
            }
        }
        return new ArrayList<>(bySubject.values());
    }

    private void bindSummary(List<SubjectMarks> withCie) {
        List<Integer> finals = new ArrayList<>();
        for (SubjectMarks s : withCie) finals.add(s.finalCie);
        MarksMath.Summary sum = MarksMath.summarize(finals);
        TextView avg = findViewById(R.id.tvMarksAverage);
        TextView best = findViewById(R.id.tvMarksBest);
        TextView weakest = findViewById(R.id.tvMarksWeakest);
        TextView source = findViewById(R.id.tvMarksSource);
        if (sum == null) {
            avg.setText("--");
            best.setText("No CIE results published yet.");
            weakest.setVisibility(View.GONE);
            source.setText("Continuous internal evaluation, out of " + MarksMath.CIE_MAX);
            return;
        }
        boolean anyDemo = false;
        for (SubjectMarks s : withCie) if (!"PDF".equals(s.source)) anyDemo = true;
        source.setText(anyDemo
                ? "CIE out of " + MarksMath.CIE_MAX + ". Some subjects use simulated demo marks."
                : "CIE out of " + MarksMath.CIE_MAX + ", from the imported CIE sheets.");
        avg.setText(String.format(java.util.Locale.US, "%.1f", sum.average) + " / " + MarksMath.CIE_MAX);
        SubjectMarks b = withCie.get(sum.bestIndex), w = withCie.get(sum.weakestIndex);
        best.setText("Best: " + b.name + " (" + b.finalCie + ")");
        if (withCie.size() > 1) {
            weakest.setText("Focus on: " + w.name + " (" + w.finalCie + ")");
        } else {
            weakest.setVisibility(View.GONE);
        }
    }

    /** Low attendance can block exam eligibility regardless of marks, so say so here too. */
    private void bindAttendanceWarning() {
        AttendanceRepo repo = new AttendanceRepo(this);
        List<String> short_ = new ArrayList<>();
        for (Models.SubjectSummary s : repo.getSubjects()) if (s.isShortage()) short_.add(s.name);
        if (short_.isEmpty()) return;
        TextView warn = findViewById(R.id.tvMarksAttendanceWarning);
        warn.setText("Attendance is below " + repo.getThreshold() + "% in " + String.join(", ", short_)
                + ". This can affect your exam eligibility even with good marks.");
        warn.setVisibility(View.VISIBLE);
    }

    private View card(LayoutInflater inflater, LinearLayout parent, SubjectMarks s) {
        View card = inflater.inflate(R.layout.item_marks_card, parent, false);
        ((TextView) card.findViewById(R.id.tvMkName)).setText(s.name);
        ((TextView) card.findViewById(R.id.tvMkCode)).setText(s.code
                + (s.hasCie ? ("PDF".equals(s.source) ? " · imported sheet" : " · demo marks") : ""));
        TextView fin = card.findViewById(R.id.tvMkFinal);
        TextView line = card.findViewById(R.id.tvMkLine);
        TextView badge = card.findViewById(R.id.tvMkBadge);
        LinearProgressIndicator bar = card.findViewById(R.id.pbMk);
        TextView details = card.findViewById(R.id.tvMkDetails);

        if (!s.hasCie) {
            fin.setText("--");
            bar.setVisibility(View.GONE);
            badge.setVisibility(View.GONE);
            line.setText("No CIE result yet");
        } else {
            MarksMath.Band band = MarksMath.band(s.finalCie);
            int color = Ui.statusColor(this, band.isGood());
            fin.setText(s.finalCie + " / " + MarksMath.CIE_MAX);
            fin.setTextColor(color);
            bar.setProgressCompat(s.finalCie, false);
            bar.setIndicatorColor(color);
            Ui.badge(badge, band.isGood(), band.label);
            line.setText("MSE " + num.format(s.mse1) + " + " + num.format(s.mse2)
                    + " · Labs " + num.format(s.la1 + s.la2 + s.la3) + "/10");
        }

        StringBuilder d = new StringBuilder();
        if (s.hasCie) {
            d.append("MSE-1  ").append(num.format(s.mse1)).append(" / 20\n")
             .append("MSE-2  ").append(num.format(s.mse2)).append(" / 20\n")
             .append("LA-1  ").append(num.format(s.la1)).append(" / 4   ")
             .append("LA-2  ").append(num.format(s.la2)).append(" / 4   ")
             .append("LA-3  ").append(num.format(s.la3)).append(" / 2\n")
             .append("Total  ").append(num.format(s.total)).append(" / 50, rounded to ")
             .append(s.finalCie);
        }
        if (!s.teacherEntries.isEmpty()) {
            if (d.length() > 0) d.append("\n\n");
            d.append("Entered by your teacher\n").append(String.join("\n", s.teacherEntries));
        }
        if (d.length() == 0) d.append("Nothing published for this subject yet.");
        details.setText(d);

        card.setOnClickListener(v ->
                details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return card;
    }

    /** Average final CIE for the home card, or null when there is nothing to show. */
    public static Double averageFinalCie(android.content.Context context) {
        if (AttendanceDb.current() == AttendanceDb.Source.REAL) return null;
        try (Cursor c = AttendanceDb.get(context).rawQuery(
                "SELECT AVG(final) FROM cie_results WHERE student_id = ?",
                new String[]{String.valueOf(Session.requireStudentId())})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : null;
        }
    }

    private void showEmpty(String message) {
        findViewById(R.id.cardMarksSummary).setVisibility(View.GONE);
        findViewById(R.id.tvMarksListTitle).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.tvMarksSource)).setText("Continuous internal evaluation");
        TextView empty = findViewById(R.id.tvMarksEmpty);
        empty.setText(message);
        empty.setVisibility(View.VISIBLE);
    }
}
