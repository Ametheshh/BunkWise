package com.example.attendanceportal;

import com.example.mini_project.R;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small formatting and styling helpers shared by the screens. */
public final class Ui {
    private Ui() { }

    public static int statusColor(Context c, boolean ok) {
        return ContextCompat.getColor(c, ok ? R.color.att_ok : R.color.att_bad);
    }

    public static void badge(TextView tv, boolean ok, String text) {
        tv.setText(text);
        tv.setBackgroundResource(ok ? R.drawable.bg_badge_ok : R.drawable.bg_badge_bad);
        tv.setTextColor(statusColor(tv.getContext(), ok));
        tv.setVisibility(View.VISIBLE);
    }

    public static String pct(double p) {
        return String.format(Locale.US, "%.1f%%", p);
    }

    public static String classes(int n) {
        return n + (n == 1 ? " class" : " classes");
    }

    /** One line telling the student what to do next. */
    public static String advice(Models.SubjectSummary s, int threshold) {
        if (!s.hasClasses()) return "No classes recorded yet.";
        if (s.isShortage()) {
            return "Attend the next " + classes(s.needed) + " in a row to reach " + threshold + "%.";
        }
        if (s.canMiss > 0) {
            return "Safe. You can miss up to " + classes(s.canMiss) + " more and stay at " + threshold + "% or above.";
        }
        return "Eligible, but you are right at " + threshold + "%. Missing a class would drop you below it.";
    }

    private static Date parse(String pattern, String text) {
        try {
            return new SimpleDateFormat(pattern, Locale.US).parse(text);
        } catch (ParseException e) {
            return null;
        }
    }

    /** "2026-08-07" to "07 Aug 2026". */
    public static String niceDate(String ymd) {
        Date d = parse("yyyy-MM-dd", ymd);
        return d == null ? ymd : new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
    }

    /** "2026-08-07" to "Fri". */
    public static String dayName(String ymd) {
        Date d = parse("yyyy-MM-dd", ymd);
        return d == null ? "" : new SimpleDateFormat("EEE", Locale.US).format(d);
    }

    /** "2026-08" to "Aug 2026". */
    public static String niceMonth(String ym) {
        Date d = parse("yyyy-MM", ym);
        return d == null ? ym : new SimpleDateFormat("MMM yyyy", Locale.US).format(d);
    }

    /** Fills one row of item_history_row. */
    public static void bindHistoryRow(View row, Models.HistoryItem h) {
        ((TextView) row.findViewById(R.id.tvHDate)).setText(niceDate(h.date));
        ((TextView) row.findViewById(R.id.tvHDay)).setText(dayName(h.date));
        ((TextView) row.findViewById(R.id.tvHSubject)).setText(h.subjectName);
        ((TextView) row.findViewById(R.id.tvHSlot)).setText(h.slot);
        badge((TextView) row.findViewById(R.id.tvHStatus), h.isPresent(), h.status);
    }
}
