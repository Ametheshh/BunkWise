package com.example.attendanceportal;

import android.database.Cursor;

import java.text.NumberFormat;

final class CieResult {
    private CieResult() { }

    static String display(Cursor cursor, int start) {
        if (cursor.isNull(start)) return "No CIE sheet available.";
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(2);
        String source = "PDF".equals(cursor.getString(start)) ? "Imported CIE sheet" : "Demo marks · simulated";
        return source + "\n"
                + "MSE-1 " + format.format(cursor.getDouble(start + 1)) + "/20 · "
                + "MSE-2 " + format.format(cursor.getDouble(start + 2)) + "/20\n"
                + "LA-1 " + format.format(cursor.getDouble(start + 3)) + "/4 · "
                + "LA-2 " + format.format(cursor.getDouble(start + 4)) + "/4 · "
                + "LA-3 " + format.format(cursor.getDouble(start + 5)) + "/2\n"
                + "Total CIE " + format.format(cursor.getDouble(start + 6)) + "/50 · "
                + "Final CIE " + format.format(cursor.getInt(start + 7)) + "/50";
    }
}
