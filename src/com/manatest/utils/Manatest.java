package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

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
        description = "Manatest - Ouvre la galerie et affiche l'image choisie.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.READ_EXTERNAL_STORAGE, android.permission.READ_MEDIA_IMAGES")
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

    @SimpleFunction(description = "Demande la permission et ouvre le sélecteur d'images.")
    public void OpenPhotoPicker() {
        String permissionNeeded = "android.permission.READ_EXTERNAL_STORAGE";
        if (Build.VERSION.SDK_INT >= 33) {
            permissionNeeded = "android.permission.READ_MEDIA_IMAGES";
        }

        form.askPermission(permissionNeeded, new PermissionResultHandler() {
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
                    Intent intent = new Intent(Intent.ACTION_PICK);
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
                processAndSendImage(selectedImageUri);
            } else {
                OnError("Aucune image sélectionnée.");
            }
        }
    }

    private void processAndSendImage(final Uri contentUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
                    File cacheFile = new File(context.getCacheDir(), "selected_image_" + System.currentTimeMillis() + ".jpg");
                    OutputStream outputStream = new FileOutputStream(cacheFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (inputStream != null && (bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    if (inputStream != null) inputStream.close();
                    outputStream.close();

                    // Formatage en file:// compatible avec le composant Image de Kodular
                    final String fileUriString = Uri.fromFile(cacheFile).toString();

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            OnPhotoPicked(fileUriString);
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            OnError("Erreur lors de la lecture du fichier: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    @SimpleEvent(description = "Déclenché après sélection d'une image.")
    public void OnPhotoPicked(final String imageUri) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnPhotoPicked", imageUri);
    }

    @SimpleEvent(description = "Déclenché en cas d'erreur.")
    public void OnError(final String message) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnError", message);
    }
}
