package com.example.attendanceportal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies the bundled attendance.db (assets) into the app's private storage on first launch and opens it. */
public final class AttendanceDb {
    private static final String DB_NAME = "attendance.db";
    private static SQLiteDatabase instance;

    private AttendanceDb() { }

    public static synchronized SQLiteDatabase get(Context context) {
        if (instance != null && instance.isOpen()) return instance;

        Context app = context.getApplicationContext();
        File dbFile = app.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            File dir = dbFile.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(dbFile.getPath() + ".tmp");
            try (InputStream in = app.getAssets().open(DB_NAME);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (IOException e) {
                tmp.delete();
                throw new RuntimeException("Could not install the bundled database", e);
            }
            if (!tmp.renameTo(dbFile)) throw new RuntimeException("Could not install the bundled database");
        }
        instance = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        return instance;
    }
}
