package com.example.mini_project;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.example.attendanceportal.BaseActivity;
import com.example.attendanceportal.Session;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class VaultActivity extends BaseActivity {

    private static final long MAX_BYTES = 10L * 1024 * 1024;   // 10 MB per file

    private DBHelper db;
    private LinearLayout container;
    private TextView tvEmpty;
    private VaultDoc pendingExport;

    // System file picker (Storage Access Framework): no storage permission needed
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importFile(uri);
            });

    // System "save as" screen for exporting a decrypted copy
    private final ActivityResultLauncher<String> exporter = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(), uri -> {
                if (uri != null && pendingExport != null) exportFile(pendingExport, uri);
                pendingExport = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!sessionOk) return;       // no active login: BaseActivity already sent the user to the login screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_vault);

        db = new DBHelper(this);
        container = findViewById(R.id.container);
        tvEmpty = findViewById(R.id.tvEmpty);

        findViewById(R.id.btnAddDoc).setOnClickListener(v -> picker.launch(new String[]{"*/*"}));
        refresh();
    }

    private File vaultDir() {
        // app-private, and a separate folder per student
        File dir = new File(getFilesDir(), "vault/" + Session.requireStudentId());
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ------------------------------------------------------------ import (encrypt)
    private void importFile(Uri uri) {
        String name = queryName(uri);
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";

        String stored = UUID.randomUUID().toString() + ".enc";   // real name is never used on disk
        File out = new File(vaultDir(), stored);

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Cannot open file");
            CryptoUtil.encryptStream(in, out, MAX_BYTES);
            db.addDocument(name, mime, stored);
            refresh();
            Toast.makeText(this, "Encrypted and saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            out.delete();   // never leave a half-written file behind
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String queryName(Uri uri) {
        String name = "document";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) name = c.getString(i);
            }
        }
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    // ------------------------------------------------------------ list
    private void refresh() {
        container.removeAllViews();
        try {
            List<VaultDoc> docs = db.getDocuments();
            tvEmpty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (VaultDoc d : docs) {
                View item = inflater.inflate(R.layout.item_document, container, false);
                ((TextView) item.findViewById(R.id.tvDocName)).setText(d.name);
                ((TextView) item.findViewById(R.id.tvDocType)).setText(d.mime);

                final VaultDoc doc = d;
                item.findViewById(R.id.btnView).setOnClickListener(v -> viewDoc(doc));
                item.findViewById(R.id.btnExport).setOnClickListener(v -> {
                    pendingExport = doc;
                    exporter.launch(doc.name);
                });
                item.findViewById(R.id.btnDocDelete).setOnClickListener(v -> confirmDelete(doc));
                container.addView(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not load vault: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ view (decrypt in memory)
    private void viewDoc(VaultDoc d) {
        if (!d.mime.startsWith("image/")) {
            Toast.makeText(this, "Preview works for images. Use Export for other files.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            byte[] data = CryptoUtil.decryptFile(new File(vaultDir(), d.file));

            // Downscale big photos so they don't run out of memory
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (bmp == null) throw new Exception("Not a valid image");

            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setImageBitmap(bmp);

            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle(d.name)
                    .setView(iv)
                    .setPositiveButton("Close", null)
                    .create();
            // Dialogs are separate windows, so they need FLAG_SECURE too
            dlg.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                                     WindowManager.LayoutParams.FLAG_SECURE);
            dlg.show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ export (decrypted copy)
    private void exportFile(VaultDoc d, Uri uri) {
        try {
            byte[] data = CryptoUtil.decryptFile(new File(vaultDir(), d.file));
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("Cannot write file");
                os.write(data);
            }
            Toast.makeText(this, "Exported a decrypted copy. It is no longer protected.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ delete
    private void confirmDelete(VaultDoc d) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + d.name + "?")
                .setMessage("The encrypted file is removed permanently.")
                .setPositiveButton("Delete", (dialog, w) -> {
                    new File(vaultDir(), d.file).delete();
                    db.deleteDocument(d.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
