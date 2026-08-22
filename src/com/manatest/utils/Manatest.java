import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// =========================================================================
// 6. GALERIE D'IMAGES & COMPRESSION
// =========================================================================

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
                e.printStackTrace();
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
            
            // Formatage avec préfixe file:// pour affichage instantané dans Kodular
            if (realPath != null && !realPath.isEmpty()) {
                if (!realPath.startsWith("file://")) {
                    realPath = "file://" + realPath;
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
        e.printStackTrace();
    }

    // Solution de secours pour Android 10+ (Scoped Storage)
    if (filePath == null || filePath.isEmpty()) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            File file = new File(context.getCacheDir(), "picked_img_" + System.currentTimeMillis() + ".jpg");
            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.close();
            inputStream.close();
            filePath = file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    return filePath;
}

