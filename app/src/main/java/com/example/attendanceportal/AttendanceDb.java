package com.example.attendanceportal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            db.setForeignKeyConstraintsEnabled(true);
            installAppSchema(app, db);
            instance = db;
            return db;
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
    }

    private static void installAppSchema(Context app, SQLiteDatabase db) {
        db.beginTransaction();
        try {
            runSqlAsset(app, db, "teacher_schema.sql");
            runSqlAsset(app, db, "cie_and_admin.sql");
            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new IllegalStateException("Could not upgrade the attendance database", e);
        } finally {
            db.endTransaction();
        }
    }

    private static void runSqlAsset(Context app, SQLiteDatabase db, String name) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                app.getAssets().open(name), StandardCharsets.UTF_8))) {
            String statement;
            while ((statement = reader.readLine()) != null) {
                if (!statement.trim().isEmpty()) db.execSQL(statement);
            }
        }
    }
}
