package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

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
        description = "Manatest - Galerie d'images compatible Android 10 à 15",
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

    @SimpleFunction(description = "Ouvre le sélecteur d'images natif.")
    public void OpenPhotoPicker() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    activity.startActivityForResult(Intent.createChooser(intent, "Sélectionner une image"), PICK_IMAGE_REQUEST);
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
                copyAndSendImagePath(selectedImageUri);
            }
        }
    }

    private void copyAndSendImagePath(Uri contentUri) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(contentUri);
            
            // Repertoire externe de l'application accessible par le composant Image de Kodular
            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = context.getFilesDir();
            }
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            File file = new File(storageDir, "picked_image_" + System.currentTimeMillis() + ".jpg");
            outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int len;
            while (inputStream != null && (len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();

            // Renvoie le chemin absolu direct
            final String finalPath = file.getAbsolutePath();
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    OnPhotoPicked(finalPath);
                }
            });

        } catch (Exception e) {
            OnError("Copie fichier échouée: " + e.getMessage());
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {}
        }
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

