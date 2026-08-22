package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.PermissionResultHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@DesignerComponent(
        version = 1,
        description = "Manatest - Ouvre la galerie et affiche l'image choisie avec gestion de permissions.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(permissionNames = {
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES"
})
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

    @SimpleFunction(description = "Demande la permission et ouvre le sélecteur d'images natif.")
    public void OpenPhotoPicker() {
        String permissionNeeded = "android.permission.READ_EXTERNAL_STORAGE";
        if (Build.VERSION.SDK_INT >= 33) {
            permissionNeeded = "android.permission.READ_MEDIA_IMAGES";
        }

        final String targetPermission = permissionNeeded;

        form.askPermission(targetPermission, new PermissionResultHandler() {
            @Override
            public void HandlePermissionResponse(String permission, boolean granted) {
                launchPickerIntent();
            }
        });
    }

    private void launchPickerIntent() {
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
                copyAndSendImagePath(selectedImageUri);
            }
        }
    }

    private void copyAndSendImagePath(final Uri contentUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    inputStream = context.getContentResolver().openInputStream(contentUri);
                    
                    File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (storageDir == null) {
                        storageDir = context.getFilesDir();
                    }
                    if (!storageDir.exists()) {
                        storageDir.mkdirs();
                    }

                    File file = new File(storageDir, "picked_img_" + System.currentTimeMillis() + ".jpg");
                    outputStream = new FileOutputStream(file);

                    byte[] buffer = new byte[4096];
                    int len;
                    while (inputStream != null && (len = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();

                    final String finalPath = file.getAbsolutePath();

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            OnPhotoPicked(finalPath);
                        }
                    });

                } catch (Exception e) {
                    OnError("Erreur traitement image: " + e.getMessage());
                } finally {
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    @SimpleEvent(description = "Déclenché après sélection d'une image.")
    public void OnPhotoPicked(final String imageUri) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnPhotoPicked", imageUri);
    }

    @SimpleEvent(description = "Déclenché en cas de problème.")
    public void OnError(final String message) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnError", message);
    }
}
