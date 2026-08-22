package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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

@DesignerComponent(
        version = 1,
        description = "Manatest - Photo Picker compatible Android récents et Samsung One UI.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent implements ActivityResultListener {

    private final Activity activity;
    private final int PICK_IMAGE_REQUEST = 2001;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.activity = (Activity) container.$context();
        container.$form().registerForActivityResult(this);
    }

    @SimpleFunction(description = "Ouvre le sélecteur d'images natif sans demander de permissions manuelles.")
    public void OpenPhotoPicker() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent;
                    // Utilise le Photo Picker natif d'Android 13+ (ne nécessite AUCUNE permission)
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                        intent.setType("image/*");
                    } else {
                        intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        intent.setType("image/*");
                    }
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
                // Donne accès en lecture à l'URI retournée pour Kodular
                try {
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    activity.getContentResolver().takePersistableUriPermission(selectedImageUri, takeFlags);
                } catch (Exception ignored) {}

                final String resultUri = selectedImageUri.toString();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        OnPhotoPicked(resultUri);
                    }
                });
            } else {
                OnError("Aucune image n'a été sélectionnée.");
            }
        }
    }

    @SimpleEvent(description = "Déclenché après sélection d'une image. Renvoie le Result URI.")
    public void OnPhotoPicked(final String imageUri) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnPhotoPicked", imageUri);
    }

    @SimpleEvent(description = "Déclenché en cas de problème.")
    public void OnError(final String message) {
        EventDispatcher.dispatchEvent(Manatest.this, "OnError", message);
    }
}
