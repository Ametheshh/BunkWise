package com.example.mini_project;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Every value is AES-GCM encrypted before it is written to SQLite. */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "securecampus.db";
    private static final int DB_VERSION = 3;

    private static final String CREATE_SUBJECTS =
            "CREATE TABLE IF NOT EXISTS subjects (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, attended TEXT NOT NULL, conducted TEXT NOT NULL)";

    private static final String CREATE_SEMESTERS =
            "CREATE TABLE IF NOT EXISTS semesters (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, credits TEXT NOT NULL, weighted TEXT NOT NULL)";

    private static final String CREATE_DOCUMENTS =
            "CREATE TABLE IF NOT EXISTS documents (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, mime TEXT NOT NULL, file TEXT NOT NULL)";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SUBJECTS);
        db.execSQL(CREATE_SEMESTERS);
        db.execSQL(CREATE_DOCUMENTS);
    }

    // Upgrading keeps the existing attendance data and only adds the new table
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL(CREATE_SEMESTERS);
        if (oldV < 3) db.execSQL(CREATE_DOCUMENTS);
    }

    // ------------------------------------------------------------ attendance
    public void addSubject(String name, int attended, int conducted) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("name", CryptoUtil.encrypt(name));
        cv.put("attended", CryptoUtil.encrypt(String.valueOf(attended)));
        cv.put("conducted", CryptoUtil.encrypt(String.valueOf(conducted)));
        getWritableDatabase().insert("subjects", null, cv);
    }

    public void updateCounts(int id, int attended, int conducted) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("attended", CryptoUtil.encrypt(String.valueOf(attended)));
        cv.put("conducted", CryptoUtil.encrypt(String.valueOf(conducted)));
        getWritableDatabase().update("subjects", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteSubject(int id) {
        getWritableDatabase().delete("subjects", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Subject> getAll() throws Exception {
        List<Subject> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, attended, conducted FROM subjects ORDER BY id", null);
        while (c.moveToNext()) {
            int id = c.getInt(0);
            String name = CryptoUtil.decrypt(c.getString(1));
            int att = Integer.parseInt(CryptoUtil.decrypt(c.getString(2)));
            int con = Integer.parseInt(CryptoUtil.decrypt(c.getString(3)));
            list.add(new Subject(id, name, att, con));
        }
        c.close();
        return list;
    }

    // ------------------------------------------------------------ semesters (CGPA)
    public void addSemester(String name, double credits, double weighted) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put("name", CryptoUtil.encrypt(name));
        cv.put("credits", CryptoUtil.encrypt(Double.toString(credits)));
        cv.put("weighted", CryptoUtil.encrypt(Double.toString(weighted)));
        getWritableDatabase().insert("semesters", null, cv);
    }

    public void deleteSemester(int id) {
        getWritableDatabase().delete("semesters", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Semester> getSemesters() throws Exception {
        List<Semester> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, credits, weighted FROM semesters ORDER BY id", null);
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
        cv.put("name", CryptoUtil.encrypt(name));
        cv.put("mime", CryptoUtil.encrypt(mime));
        cv.put("file", file);
        getWritableDatabase().insert("documents", null, cv);
    }

    public void deleteDocument(int id) {
        getWritableDatabase().delete("documents", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<VaultDoc> getDocuments() throws Exception {
        List<VaultDoc> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, mime, file FROM documents ORDER BY id DESC", null);
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
