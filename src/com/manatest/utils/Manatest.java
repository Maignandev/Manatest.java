package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@DesignerComponent(
        version = 1,
        description = "Manatest - Ouvre la galerie et récupère l'image choisie.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent implements ActivityResultListener {

    private final Context context;
    private final Activity activity;
    private final int PICK_IMAGE_REQUEST = 2001;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = (Activity) container.$context();
        container.$form().registerForActivityResult(this);
    }

    @SimpleFunction(description = "Ouvre la galerie d'images native.")
    public void OpenPhotoPicker() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    activity.startActivityForResult(intent, PICK_IMAGE_REQUEST);
                } catch (Exception e) {
                    OnError("OpenPhotoPicker: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                String realPath = getRealPathFromURI(selectedImageUri);

                if (realPath != null && !realPath.isEmpty()) {
                    // Supprime file:// si présent pour la compatibilité Kodular
                    if (realPath.startsWith("file://")) {
                        realPath = realPath.replace("file://", "");
                    }
                    OnPhotoPicked(realPath);
                } else {
                    OnPhotoPicked(selectedImageUri.toString());
                }
            }
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String filePath = "";
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            android.database.Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(columnIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignoré pour passer automatiquement au fallback
        }

        // Si le chemin direct est inaccessible ou vide (Scoped Storage / Android 10+),
        // on copie le flux dans un fichier temporaire accessible
        if (filePath == null || filePath.isEmpty()) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = context.getContentResolver().openInputStream(contentUri);
                File file = new File(context.getCacheDir(), "picked_img_" + System.currentTimeMillis() + ".jpg");
                outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[2048];
                int len;
                while (inputStream != null && (len = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
                filePath = file.getAbsolutePath();
            } catch (Exception e) {
                OnError("Erreur de lecture image: " + e.getMessage());
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                } catch (Exception ignored) {}
            }
        }
        return filePath;
    }

    @SimpleEvent(description = "Déclenché après sélection d'une image depuis la galerie.")
    public void OnPhotoPicked(String imageUri) {
        EventDispatcher.dispatchEvent(this, "OnPhotoPicked", imageUri);
    }

    @SimpleEvent(description = "Déclenché en cas de problème.")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }
}

