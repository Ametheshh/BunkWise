package com.example.mini_project;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.attendanceportal.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * CGPA semesters and vault documents. Every value is AES-GCM encrypted before it is written, and every
 * row belongs to the logged-in student: methods use the student from Session, never a parameter, so one
 * student cannot read or delete another student's rows.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "securecampus.db";
    private static final int DB_VERSION = 4;

    private static final String CREATE_SEMESTERS =
            "CREATE TABLE IF NOT EXISTS semesters (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "student_id INTEGER NOT NULL DEFAULT 0, " +
            "name TEXT NOT NULL, credits TEXT NOT NULL, weighted TEXT NOT NULL)";

    private static final String CREATE_DOCUMENTS =
            "CREATE TABLE IF NOT EXISTS documents (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "student_id INTEGER NOT NULL DEFAULT 0, " +
            "name TEXT NOT NULL, mime TEXT NOT NULL, file TEXT NOT NULL)";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SEMESTERS);
        db.execSQL(CREATE_DOCUMENTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL(CREATE_SEMESTERS);     // new definition already has student_id
        if (oldV < 3) db.execSQL(CREATE_DOCUMENTS);
        // Version 4: rows belong to a student. Rows saved by older versions get student_id 0, which no
        // logged-in student matches, so they are simply no longer shown.
        if (oldV >= 2 && oldV < 4) db.execSQL("ALTER TABLE semesters ADD COLUMN student_id INTEGER NOT NULL DEFAULT 0");
        if (oldV >= 3 && oldV < 4) db.execSQL("ALTER TABLE documents ADD COLUMN student_id INTEGER NOT NULL DEFAULT 0");
    }

    private static long sid() {
        return Session.requireStudentId();
    }

    // ------------------------------------------------------------ semesters (CGPA)
    public void addSemester(String name, double credits, double weighted) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("student_id", sid());
        cv.put("name", CryptoUtil.encrypt(name));
        cv.put("credits", CryptoUtil.encrypt(Double.toString(credits)));
        cv.put("weighted", CryptoUtil.encrypt(Double.toString(weighted)));
        getWritableDatabase().insert("semesters", null, cv);
    }

    public void deleteSemester(int id) {
        getWritableDatabase().delete("semesters", "id = ? AND student_id = ?",
                new String[]{String.valueOf(id), String.valueOf(sid())});
    }

    public List<Semester> getSemesters() throws Exception {
        List<Semester> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, credits, weighted FROM semesters WHERE student_id = ? ORDER BY id",
                new String[]{String.valueOf(sid())});
        while (c.moveToNext()) {
            int id = c.getInt(0);
            String name = CryptoUtil.decrypt(c.getString(1));
            double cr = Double.parseDouble(CryptoUtil.decrypt(c.getString(2)));
            double w = Double.parseDouble(CryptoUtil.decrypt(c.getString(3)));
            list.add(new Semester(id, name, cr, w));
        }
        c.close();
        return list;
    }

    // ------------------------------------------------------------ document vault
    /** name and mime are encrypted; 'file' is a random UUID name of the encrypted file on disk. */
    public void addDocument(String name, String mime, String file) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("student_id", sid());
        cv.put("name", CryptoUtil.encrypt(name));
        cv.put("mime", CryptoUtil.encrypt(mime));
        cv.put("file", file);
        getWritableDatabase().insert("documents", null, cv);
    }

    public void deleteDocument(int id) {
        getWritableDatabase().delete("documents", "id = ? AND student_id = ?",
                new String[]{String.valueOf(id), String.valueOf(sid())});
    }

    public List<VaultDoc> getDocuments() throws Exception {
        List<VaultDoc> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, mime, file FROM documents WHERE student_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(sid())});
        while (c.moveToNext()) {
            list.add(new VaultDoc(c.getInt(0),
                    CryptoUtil.decrypt(c.getString(1)),
                    CryptoUtil.decrypt(c.getString(2)),
                    c.getString(3)));
        }
        c.close();
        return list;
    }
}
