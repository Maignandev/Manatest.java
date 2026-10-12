package com.manatest.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.PermissionResultHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@DesignerComponent(
        version = 10,
        description = "Manatest - Pages de la boutique et du produit construites en code : Modifier le profil de la boutique (cartes à libellé animé, nom unique vérifié en direct, catégorie, logo), Prix (devise HTG / USD, frais de livraison Non / Oui, montant total) et État et variantes (boîte de dialogue, couleur avec pastille, taille).",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(
        permissionNames =
                "android.permission.INTERNET," +
                "android.permission.READ_EXTERNAL_STORAGE," +
                "android.permission.READ_MEDIA_IMAGES"
)
public class Manatest extends AndroidNonvisibleComponent implements ActivityResultListener {

    private static final String PREFS_NAME = "ManaplaceShop";
    private static final String PREF_CURRENT_UID = "current_uid";

    private static final int MAX_NAME = 50;
    private static final int MAX_PHONE = 20;
    private static final int MAX_ADDRESS = 120;
    private static final int FIELD_HEIGHT_DP = 64;
    private static final long NAME_DEBOUNCE_MS = 450L;
    private static final int LOGO_MAX_WIDTH = 800;
    private static final int LOGO_QUALITY = 85;

    private static final int COLOR_FOCUS = Color.parseColor("#0055D4");
    private static final int COLOR_ERROR = Color.parseColor("#E53935");
    private static final int COLOR_OK = Color.parseColor("#2E7D32");

    private final Context context;
    private final Activity activity;
    private final Form form;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int pickRequestCode;

    private Typeface customFont;
    private Typeface customBoldFont;
    private ViewGroup activeContainer;
    private boolean restoring = false;
    private String currentUid = "";

    // Page 1 : modifier la boutique
    private ViewGroup editContainer;
    private String shopName = "";
    private String shopCategory = "";
    private String shopPhone = "";
    private String shopAddress = "";
    private String logoPath = "";
    private String logoCurrent = "";
    private boolean logoChanged = false;
    private String origName = "";
    private String origCategory = "";
    private String origPhone = "";
    private String origAddress = "";
    private final List<String> categories = new ArrayList<String>();
    private FieldView nameField;
    private FieldView phoneField;
    private FieldView addressField;
    private FieldView catField;
    private TextView catValue;
    private TextView catError;
    private TextView nameStatus;
    private ImageView logoImage;
    private View logoGlyph;
    private int logoToken = 0;
    private String nameCheckUrl = "";
    private String nameCheckAuth = "";
    private int nameSeq = 0;
    private boolean nameChecking = false;
    private int nameState = 0; // 0 inconnu, 1 disponible, 2 déjà utilisé, 3 erreur réseau
    private boolean nameRequiredShown = false;
    private Runnable nameRunnable;
    private boolean submitting = false;
    private int radioButtonColor = Color.parseColor("#C01A1A1B");

    // Page 2 : prix
    private ViewGroup priceContainer;
    private String priceText = "";
    private String feeText = "";
    private String priceCurrency = "HTG";
    private boolean deliveryOn = false;
    private FieldView priceField;
    private FieldView feeField;
    private TextView priceCurTv;
    private TextView deliveryTv;
    private TextView totalValueTv;
    private TextView totalCurTv;
    private TextView priceErr;
    private TextView feeErr;

    // Page 3 : état et variantes
    private ViewGroup variantContainer;
    private String condition = "";
    private String color = "";
    private String size = "";
    private final List<String> conditions = new ArrayList<String>();
    private FieldView condField;
    private FieldView colorField;
    private FieldView sizeField;
    private LinearLayout swatchRow;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.form = container.$form();
        this.context = container.$context();
        this.activity = (Activity) container.$context();
        this.pickRequestCode = form.registerForActivityResult(this);
        conditions.add("Neuf");
        conditions.add("Bon état");
        conditions.add("Comme neuf");
        conditions.add("Assez bon état");
        currentUid = prefs().getString(PREF_CURRENT_UID, "");
    }

    // =========================================================================
    // OUTILS INTERNES (repris de la création de boutique)
    // =========================================================================


    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics());
    }

    private int ink(int alpha) {
        return Color.argb(alpha, 0x1A, 0x1A, 0x1B);
    }

    private int cTitle() { return ink(0xC0); }   // grand titre et sous-descriptions

    private int cText() { return ink(0xE9); }    // texte saisi / sélectionné

    private int cHint() { return ink(0x91); }    // libellé (hint)

    private int cStroke() { return ink(0x86); }  // contour au repos

    private void runOnUi(Runnable r) {
        activity.runOnUiThread(r);
    }

    private void fail(final String message) {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                OnError(message);
            }
        });
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private ViewGroup realLayout(AndroidViewComponent component) {
        if (component == null) return null;
        View v = component.getView();
        for (int i = 0; i < 3; i++) {
            if (v instanceof ScrollView || v instanceof HorizontalScrollView) {
                ViewGroup sv = (ViewGroup) v;
                if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup) {
                    v = sv.getChildAt(0);
                    continue;
                }
                break;
            }
            if (v instanceof FrameLayout) {
                ViewGroup fl = (ViewGroup) v;
                if (fl.getChildCount() == 1 && fl.getChildAt(0) instanceof ViewGroup) {
                    v = fl.getChildAt(0);
                    continue;
                }
            }
            break;
        }
        return v instanceof ViewGroup ? (ViewGroup) v : null;
    }

    private Typeface loadFontNamed(String fontPath, String who) {
        if (fontPath == null || fontPath.trim().isEmpty()) return null;
        try {
            if (fontPath.startsWith("/")) {
                return Typeface.createFromFile(new File(fontPath));
            }
            return Typeface.createFromAsset(context.getAssets(), fontPath);
        } catch (Exception e) {
            OnError(who + ": police introuvable (" + fontPath + ").");
            return null;
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            if (customBoldFont != null) {
                t.setTypeface(customBoldFont);
            } else if (customFont != null) {
                t.setTypeface(Typeface.create(customFont, Typeface.BOLD));
            } else {
                t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
        } else if (customFont != null) {
            t.setTypeface(customFont);
        }
        return t;
    }

    private String stripFile(String p) {
        return (p != null && p.startsWith("file://")) ? p.substring(7) : p;
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toString("UTF-8");
    }

    private String[] httpGet(String target, String auth) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(target).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/json");
            if (auth != null && !auth.isEmpty()) {
                c.setRequestProperty("Authorization", auth);
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new String[]{String.valueOf(code), readAll(is)};
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Boolean nameAvailableFromJson(JSONObject o) {
        if (o.has("available")) return Boolean.valueOf(o.optBoolean("available"));
        String[] taken = {"exists", "taken", "used", "isUsed", "already_used", "alreadyUsed"};
        for (int i = 0; i < taken.length; i++) {
            if (o.has(taken[i])) return Boolean.valueOf(!o.optBoolean(taken[i]));
        }
        JSONObject data = o.optJSONObject("data");
        if (data != null) return nameAvailableFromJson(data);
        return null;
    }

    private Bitmap decodeSampled(String path, int maxWidth) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) {
            return null;
        }
    }

    @SimpleFunction(description = "Ouvre la galerie pour choisir le logo de la boutique (demande la permission si besoin). Appelée automatiquement au toucher de la tuile logo. OnShopLogoPicked se déclenche quand le logo est prêt.")
    public void OpenLogoPicker() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? "android.permission.READ_MEDIA_IMAGES"
                : "android.permission.READ_EXTERNAL_STORAGE";

        form.askPermission(permission, new PermissionResultHandler() {
            @Override
            public void HandlePermissionResponse(String permissionName, boolean granted) {
                if (!granted) {
                    OnError("Permission refusée.");
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent i = new Intent(Intent.ACTION_PICK);
                            i.setType("image/*");
                            form.startActivityForResult(i, pickRequestCode);
                        } catch (Exception e) {
                            OnError("OpenLogoPicker: " + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    private String processLogo(Uri uri) {
        File raw = null;
        try {
            File dir = new File(context.getCacheDir(), "manatest_logo");
            dir.mkdirs();
            long t = System.currentTimeMillis();
            raw = new File(dir, "raw_" + t);

            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                fail("Logo: image illisible.");
                return null;
            }
            FileOutputStream out = new FileOutputStream(raw);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                try { in.close(); } catch (Exception ignored) { }
                try { out.close(); } catch (Exception ignored) { }
            }

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(raw.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                fail("Logo: ce fichier n'est pas une image.");
                return null;
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= LOGO_MAX_WIDTH) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(raw.getAbsolutePath(), o);
            if (bmp == null) {
                fail("Logo: décodage impossible.");
                return null;
            }

            int degrees = 0;
            try {
                ExifInterface exif = new ExifInterface(raw.getAbsolutePath());
                int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            } catch (Exception ignored) {
            }
            if (degrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (rotated != bmp) {
                    bmp.recycle();
                    bmp = rotated;
                }
            }

            File dest = new File(dir, "logo_" + t + ".jpg");
            FileOutputStream fos = new FileOutputStream(dest);
            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, LOGO_QUALITY, fos);
                fos.flush();
            } finally {
                try { fos.close(); } catch (Exception ignored) { }
                bmp.recycle();
            }
            return "file://" + dest.getAbsolutePath();
        } catch (Exception e) {
            fail("Logo: " + e.getMessage());
            return null;
        } finally {
            if (raw != null) raw.delete();
        }
    }

    private class PhotoGlyph extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PhotoGlyph(Context c) {
            super(c);
            int grey = Color.parseColor("#C4C4C4");
            stroke.setColor(grey);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth((float) dp(4));
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            fill.setColor(grey);
            fill.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float s = stroke.getStrokeWidth() / 2f;
            canvas.drawRoundRect(new RectF(s, s, w - s, h - s), w * 0.2f, w * 0.2f, stroke);
            canvas.drawCircle(w * 0.68f, h * 0.30f, w * 0.06f, fill);
            path.reset();
            path.moveTo(w * 0.12f, h * 0.78f);
            path.lineTo(w * 0.38f, h * 0.50f);
            path.lineTo(w * 0.55f, h * 0.68f);
            path.lineTo(w * 0.68f, h * 0.58f);
            path.lineTo(w * 0.88f, h * 0.78f);
            canvas.drawPath(path, stroke);
        }
    }

    // =========================================================================
    // ÉVÉNEMENTS
    // =========================================================================

    @SimpleEvent(description = "Une catégorie vient d'être choisie dans le menu de la page Modifier la boutique. category : le nom choisi.")
    public void AfterCategorySelected(String category) {
        EventDispatcher.dispatchEvent(this, "AfterCategorySelected", category);
    }

    @SimpleEvent(description = "Le serveur a répondu à la vérification du nom. available : vrai si le nom est libre, faux s'il est déjà utilisé.")
    public void OnShopNameChecked(String name, boolean available) {
        EventDispatcher.dispatchEvent(this, "OnShopNameChecked", name, available);
    }

    @SimpleEvent(description = "Le formulaire boutique est invalide. fieldKey : name ou category. message : le texte de l'erreur (aussi affiché en rouge à l'écran).")
    public void OnShopFormInvalid(String fieldKey, String message) {
        EventDispatcher.dispatchEvent(this, "OnShopFormInvalid", fieldKey, message);
    }

    @SimpleEvent(description = "Un nouveau logo a été choisi et compressé. logoPath : lien file:// de l'image.")
    public void OnShopLogoPicked(String logoPath) {
        EventDispatcher.dispatchEvent(this, "OnShopLogoPicked", logoPath);
    }

    @SimpleEvent(description = "Le serveur a accepté la modification de la boutique. responseCode : code HTTP, response : texte renvoyé.")
    public void OnShopUpdated(int responseCode, String response) {
        EventDispatcher.dispatchEvent(this, "OnShopUpdated", responseCode, response);
    }

    @SimpleEvent(description = "Le serveur a refusé la modification de la boutique (ou erreur serveur). Le code 409 signale un nom déjà pris.")
    public void OnShopUpdateFailed(int responseCode, String response) {
        EventDispatcher.dispatchEvent(this, "OnShopUpdateFailed", responseCode, response);
    }

    @SimpleEvent(description = "La devise a changé dans la page Prix. currency : HTG ou USD.")
    public void OnCurrencyChanged(String currency) {
        EventDispatcher.dispatchEvent(this, "OnCurrencyChanged", currency);
    }

    @SimpleEvent(description = "Le choix des frais de livraison a changé. included : vrai pour Oui, faux pour Non.")
    public void OnDeliveryChoiceChanged(boolean included) {
        EventDispatcher.dispatchEvent(this, "OnDeliveryChoiceChanged", included);
    }

    @SimpleEvent(description = "La page Prix est invalide. fieldKey : price ou deliveryFee. message : le texte de l'erreur (aussi affiché en rouge à l'écran).")
    public void OnPriceFormInvalid(String fieldKey, String message) {
        EventDispatcher.dispatchEvent(this, "OnPriceFormInvalid", fieldKey, message);
    }

    @SimpleEvent(description = "Un état a été choisi dans la boîte de dialogue (Neuf, Bon état...). condition : l'état choisi.")
    public void OnConditionSelected(String condition) {
        EventDispatcher.dispatchEvent(this, "OnConditionSelected", condition);
    }

    @SimpleEvent(description = "Une erreur s'est produite (code, réseau, permission, serveur).")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // OUTILS DE DESSIN COMMUNS
    // =========================================================================

    private interface TextCb {
        void on(String value);
    }

    private interface PickCb {
        void on(String value);
    }

    // Toutes les tailles de composants sont des pourcentages de l'écran
    private int wp(float pct) {
        return Math.round(context.getResources().getDisplayMetrics().widthPixels * pct / 100f);
    }

    private int hp(float pct) {
        return Math.round(context.getResources().getDisplayMetrics().heightPixels * pct / 100f);
    }

    private int fieldH() {
        return Math.max(hp(8.5f), dp(48));
    }

    private void hideKeyboard() {
        try {
            View v = activity.getCurrentFocus();
            if (v == null) v = activeContainer;
            InputMethodManager imm =
                    (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void clearAllFocus() {
        FieldView[] all = {nameField, phoneField, addressField, priceField, feeField, colorField, sizeField};
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null && all[i].edit != null) all[i].edit.clearFocus();
        }
    }

    private LinearLayout section() {
        LinearLayout s = new LinearLayout(context);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        s.setPadding(wp(5.5f), hp(2f), wp(5.5f), hp(2f));
        return s;
    }

    private TextView desc(String s) {
        TextView t = text(s, 12, cTitle(), false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = hp(1.5f);
        p.leftMargin = wp(0.5f);
        t.setLayoutParams(p);
        return t;
    }

    // Titre de bloc : taille 15, noir
    private TextView blockTitle(String s) {
        TextView t = text(s, 15, Color.BLACK, true);
        t.setPadding(wp(0.5f), 0, 0, hp(1.5f));
        return t;
    }

    private TextView errorText() {
        TextView t = text("", 12, COLOR_ERROR, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = hp(0.8f);
        p.leftMargin = wp(0.5f);
        t.setLayoutParams(p);
        t.setVisibility(View.GONE);
        return t;
    }

    private void addSeparator(ViewGroup parent) {
        View v = new View(context);
        v.setBackgroundColor(Color.parseColor("#F5F5F5"));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, hp(1.8f)));
        parent.addView(v);
    }

    private class ArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ArrowView(Context c) {
            super(c);
            paint.setColor(cText());
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            path.reset();
            path.moveTo(0f, 0f);
            path.lineTo(w, 0f);
            path.lineTo(w / 2f, h);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    // ---- champ à libellé animé (même design que la création de boutique) ----

    private class FieldView {
        final FrameLayout box;
        final TextView label;
        final GradientDrawable bg;
        final int fh = fieldH();
        EditText edit;
        TextView value;
        boolean floated = false;
        boolean focused = false;
        boolean error = false;
        boolean disabled = false;

        FieldView(String hint) {
            box = new FrameLayout(context);
            box.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, fh));
            bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
            bg.setCornerRadius(fh * 0.375f);
            box.setBackground(bg);

            label = text(hint, 15, cHint(), false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, Math.round(fh * 0.34f),
                    Gravity.CENTER_VERTICAL | Gravity.START);
            lp.leftMargin = wp(5.5f);
            label.setLayoutParams(lp);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setSingleLine(true);
            label.setClickable(false);
            label.setPivotX(0f);
            label.setPivotY(fh * 0.17f);
            updateStroke();
        }

        void attachLabel() {
            box.addView(label);
        }

        void updateStroke() {
            int w;
            int c;
            if (focused) {
                w = dp(2);
                c = COLOR_FOCUS;
            } else if (error) {
                w = dp(1);
                c = COLOR_ERROR;
            } else {
                w = dp(1);
                c = cStroke();
            }
            bg.setStroke(w, c);
        }

        void setFloated(boolean f, boolean animate) {
            if (animate && floated == f) return;
            floated = f;
            float ty = f ? -(fh * 0.27f) : 0f;
            float sc = f ? 0.8f : 1f;
            if (animate) {
                label.animate()
                        .translationY(ty)
                        .scaleX(sc)
                        .scaleY(sc)
                        .setDuration(160)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                label.animate().cancel();
                label.setTranslationY(ty);
                label.setScaleX(sc);
                label.setScaleY(sc);
            }
        }

        // Carte grisée : la saisie est impossible
        void setDisabled(boolean d) {
            disabled = d;
            label.setAlpha(d ? 0.45f : 1f);
            if (edit != null) {
                edit.setEnabled(!d);
                edit.setFocusable(!d);
                edit.setFocusableInTouchMode(!d);
                if (d) edit.clearFocus();
            }
        }
    }

    private FieldView editField(String hint, int inputType, int maxLen, int imeAction,
                                String initial, final TextCb cb, float rightPadPct) {
        final FieldView f = new FieldView(hint);
        final EditText e = new EditText(context);
        e.setBackground(null);
        e.setTextSize(15);
        e.setTextColor(cText());
        e.setHintTextColor(cHint());
        e.setTypeface(customFont != null ? customFont : Typeface.DEFAULT);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setImeOptions(imeAction);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        e.setPadding(wp(5.5f), Math.round(f.fh * 0.375f), wp(rightPadPct), Math.round(f.fh * 0.09f));
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        f.edit = e;
        f.box.addView(e, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        f.attachLabel();

        restoring = true;
        e.setText(initial);
        restoring = false;
        f.setFloated(initial != null && !initial.isEmpty(), false);

        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                f.focused = hasFocus;
                f.updateStroke();
                f.setFloated(hasFocus || e.getText().length() > 0, true);
            }
        });

        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable ed) {
                if (restoring) return;
                String s = ed.toString();
                if (s.length() > 0 && !f.floated) f.setFloated(true, true);
                f.error = false;
                f.updateStroke();
                if (cb != null) cb.on(s);
            }
        });

        if (imeAction == EditorInfo.IME_ACTION_DONE) {
            e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideKeyboard();
                        e.clearFocus();
                        return true;
                    }
                    return false;
                }
            });
        }
        return f;
    }

    // Carte cliquable avec flèche (menu déroulant ou boîte de dialogue)
    private FieldView dropField(String hint, String current) {
        FieldView f = new FieldView(hint);
        f.value = text(current == null ? "" : current, 15, cText(), false);
        f.value.setSingleLine(true);
        f.value.setEllipsize(TextUtils.TruncateAt.END);
        f.value.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        f.value.setPadding(wp(5.5f), Math.round(f.fh * 0.375f), wp(13.3f), Math.round(f.fh * 0.09f));
        f.box.addView(f.value, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(
                wp(3.9f), wp(2.2f), Gravity.END | Gravity.CENTER_VERTICAL);
        ap.rightMargin = wp(5.5f);
        f.box.addView(new ArrowView(context), ap);

        f.attachLabel();
        f.setFloated(current != null && !current.isEmpty(), false);
        f.box.setClickable(true);
        return f;
    }

    // Petit sélecteur « HTG ▾ » ou « Non ▾ » posé à droite d'une carte
    private TextView addSelector(FieldView f, String initial, final Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(wp(2.2f), wp(2.2f), wp(2.2f), wp(2.2f));
        TextView t = text(initial, 15, cText(), false);
        row.addView(t);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(wp(3.9f), wp(2.2f));
        ap.leftMargin = wp(2.2f);
        row.addView(new ArrowView(context), ap);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        lp.rightMargin = wp(3.3f);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        f.box.addView(row, lp);
        return t;
    }

    // Menu déroulant (même style que le spinner de catégories)
    private void showListPopup(View anchor, int widthPx, int hOffset, final List<String> items,
                               final String selected, final PickCb cb, final Runnable onDismiss) {
        if (items.isEmpty()) {
            OnError("Aucun choix disponible dans le menu.");
            return;
        }
        hideKeyboard();
        clearAllFocus();

        final ListPopupWindow pop = new ListPopupWindow(context);
        pop.setAnchorView(anchor);
        pop.setModal(true);
        pop.setWidth(widthPx);
        pop.setHeight(items.size() <= 5 ? ViewGroup.LayoutParams.WRAP_CONTENT : hp(6.2f) * 5 + hp(1f));
        pop.setHorizontalOffset(hOffset);
        pop.setVerticalOffset(hp(0.5f));

        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Color.WHITE);
        pbg.setCornerRadius(wp(4.4f));
        pbg.setStroke(dp(1), cStroke());
        pop.setBackgroundDrawable(pbg);

        pop.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = text("", 15, cText(), false);
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setPadding(wp(5.5f), 0, wp(5.5f), 0);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setLayoutParams(new AbsListView.LayoutParams(
                            AbsListView.LayoutParams.MATCH_PARENT, hp(6.2f)));
                }
                String item = items.get(position);
                tv.setText(item);
                tv.setBackgroundColor(item.equals(selected)
                        ? Color.parseColor("#F2F2F2") : Color.TRANSPARENT);
                return tv;
            }
        });

        pop.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                cb.on(items.get(position));
                pop.dismiss();
            }
        });

        pop.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (onDismiss != null) onDismiss.run();
            }
        });
        pop.show();
    }

    private void rerenderAll() {
        if (editContainer != null) renderEdit();
        if (priceContainer != null) renderPrice();
        if (variantContainer != null) renderVariants();
    }

    // =========================================================================
    // POLICES, UTILISATEUR
    // =========================================================================

    @SimpleFunction(description = "Charge la police du texte des 3 pages (ex: Manrope-Medium.ttf placé dans les Assets). Vide = police par défaut.")
    public void LoadCustomFont(String fontPath) {
        customFont = loadFontNamed(fontPath, "LoadCustomFont");
        rerenderAll();
    }

    @SimpleFunction(description = "Charge (facultatif) la police en gras des titres.")
    public void LoadCustomBoldFont(String fontPath) {
        customBoldFont = loadFontNamed(fontPath, "LoadCustomBoldFont");
        rerenderAll();
    }

    @SimpleFunction(description = "Mémorise l'utilisateur connecté (UID Firebase). Il est joint à la vérification du nom pour que ton propre nom ne soit pas refusé, et réutilisé par SubmitShopEdit.")
    public void SetShopUser(String uid) {
        currentUid = uid == null ? "" : uid.trim();
        prefs().edit().putString(PREF_CURRENT_UID, currentUid).apply();
    }

    // =========================================================================
    // PAGE 1 : MODIFIER LE PROFIL DE LA BOUTIQUE
    // =========================================================================

    @SimpleFunction(description = "Construit la page « Modifier le profil de la boutique » dans l'arrangement donné (arrangement vertical dédié : son contenu est remplacé). À brancher sur OnEditShopClick. Préremplis les champs avec SetShopEditValues.")
    public void BuildShopEditForm(final AndroidViewComponent container) {
        if (container == null || container.getView() == null) {
            OnError("BuildShopEditForm: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                ViewGroup target = realLayout(container);
                if (target == null) {
                    OnError("BuildShopEditForm: conteneur invalide.");
                    return;
                }
                editContainer = target;
                renderEdit();
            }
        });
    }

    @SimpleFunction(description = "Préremplit la page avec les informations actuelles de la boutique (par exemple celles reçues par OnShopInfoLoaded). logo : adresse http(s) du logo actuel ou chemin local ; il n'est renvoyé au serveur que si l'utilisateur en choisit un nouveau. Le nom donné ici n'est pas considéré comme « déjà utilisé ».")
    public void SetShopEditValues(String name, String category, String phone, String address, String logo) {
        shopName = name == null ? "" : name.trim();
        shopCategory = category == null ? "" : category.trim();
        shopPhone = phone == null ? "" : phone.trim();
        shopAddress = address == null ? "" : address.trim();
        logoCurrent = logo == null ? "" : logo.trim();
        logoPath = "";
        logoChanged = false;
        origName = shopName;
        origCategory = shopCategory;
        origPhone = shopPhone;
        origAddress = shopAddress;
        nameState = 0;
        nameChecking = false;
        nameRequiredShown = false;
        nameSeq++;
        if (editContainer != null) renderEdit();
    }

    @SimpleFunction(description = "Définit la liste des catégories du menu : un tableau JSON (« [\"Mode\",\"Beauté\"] » ou objets avec name / title) ou des noms séparés par des virgules.")
    public void SetShopCategories(String categoriesList) {
        categories.clear();
        if (categoriesList == null || categoriesList.trim().isEmpty()) return;
        String t = categoriesList.trim();
        try {
            if (t.startsWith("[")) {
                JSONArray a = new JSONArray(t);
                for (int i = 0; i < a.length(); i++) {
                    Object o = a.get(i);
                    String s;
                    if (o instanceof JSONObject) {
                        JSONObject jo = (JSONObject) o;
                        s = jo.optString("name", jo.optString("title", jo.optString("label", "")));
                    } else {
                        s = String.valueOf(o);
                    }
                    s = s == null ? "" : s.trim();
                    if (!s.isEmpty()) categories.add(s);
                }
            } else {
                String[] parts = t.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String s = parts[i].trim();
                    if (!s.isEmpty()) categories.add(s);
                }
            }
        } catch (Exception e) {
            OnError("SetShopCategories: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Définit l'adresse du serveur qui vérifie le nom en direct. La requête GET est envoyée à chaque pause de frappe avec ?name=...&uid=... (ou à la place de {name} et {uid} dans l'adresse). Réponse attendue en JSON : {\"available\": true} ou {\"exists\": true}. Le serveur doit ignorer la boutique de cet uid. Si le nom n'a pas changé, aucune vérification n'est faite.")
    public void SetShopNameCheckUrl(String url, String authorization) {
        nameCheckUrl = url == null ? "" : url.trim();
        nameCheckAuth = authorization == null ? "" : authorization.trim();
        scheduleNameCheck();
    }

    private void renderEdit() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (editContainer == null) return;
                    activeContainer = editContainer;
                    editContainer.removeAllViews();

                    TextView intro = text("Ces informations seront visibles par vos clients sur votre profil",
                            15, cTitle(), false);
                    intro.setPadding(wp(5.5f), hp(2f), wp(5.5f), 0);
                    editContainer.addView(intro);

                    // nom
                    LinearLayout s1 = section();
                    s1.addView(blockTitle("Nom de la boutique"));
                    nameField = editField("Nom de la boutique",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                            MAX_NAME, EditorInfo.IME_ACTION_NEXT, shopName, new TextCb() {
                                @Override
                                public void on(String v) {
                                    shopName = v;
                                    nameRequiredShown = false;
                                    scheduleNameCheck();
                                }
                            }, 5.5f);
                    s1.addView(nameField.box);
                    nameStatus = errorText();
                    s1.addView(nameStatus);
                    showNameStatus();
                    editContainer.addView(s1);

                    // catégorie
                    LinearLayout s2 = section();
                    catField = dropField("Catégorie", shopCategory);
                    catField.box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openCategoryPopup();
                        }
                    });
                    catValue = catField.value;
                    s2.addView(catField.box);
                    catError = errorText();
                    s2.addView(catError);
                    editContainer.addView(s2);

                    // coordonnées
                    LinearLayout s3 = section();
                    s3.addView(blockTitle("Coordonnées de la boutique"));
                    phoneField = editField("Téléphone de la boutique", InputType.TYPE_CLASS_PHONE,
                            MAX_PHONE, EditorInfo.IME_ACTION_NEXT, shopPhone, new TextCb() {
                                @Override
                                public void on(String v) {
                                    shopPhone = v;
                                }
                            }, 5.5f);
                    s3.addView(phoneField.box);
                    addressField = editField("Adresse complète",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                            MAX_ADDRESS, EditorInfo.IME_ACTION_DONE, shopAddress, new TextCb() {
                                @Override
                                public void on(String v) {
                                    shopAddress = v;
                                }
                            }, 5.5f);
                    LinearLayout.LayoutParams alp = (LinearLayout.LayoutParams) addressField.box.getLayoutParams();
                    alp.topMargin = hp(2.3f);
                    addressField.box.setLayoutParams(alp);
                    s3.addView(addressField.box);
                    editContainer.addView(s3);

                    addSeparator(editContainer);

                    // média
                    LinearLayout s4 = section();
                    s4.addView(blockTitle("Média"));
                    addLogoCard(s4);
                    editContainer.addView(s4);

                    addSeparator(editContainer);
                    View pad = new View(context);
                    pad.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, hp(3f)));
                    editContainer.addView(pad);
                } catch (Exception e) {
                    OnError("renderEdit: " + e.getMessage());
                }
            }
        });
    }

    private void openCategoryPopup() {
        if (categories.isEmpty()) {
            OnError("Aucune catégorie : appelle SetShopCategories avant d'ouvrir le menu.");
            return;
        }
        catField.focused = true;
        catField.updateStroke();
        catField.setFloated(true, true);
        showListPopup(catField.box, catField.box.getWidth(), 0, categories, shopCategory,
                new PickCb() {
                    @Override
                    public void on(String v) {
                        selectCategory(v, true);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        catField.focused = false;
                        catField.updateStroke();
                        if (shopCategory.isEmpty()) catField.setFloated(false, true);
                    }
                });
    }

    private void selectCategory(String c, boolean fire) {
        shopCategory = c == null ? "" : c;
        if (catValue != null) catValue.setText(shopCategory);
        if (catField != null) {
            catField.error = false;
            catField.updateStroke();
            catField.setFloated(!shopCategory.isEmpty(), true);
        }
        if (catError != null) catError.setVisibility(View.GONE);
        if (fire) AfterCategorySelected(shopCategory);
    }

    // ---- logo (même carte inclinée que la création) ----

    private void addLogoCard(LinearLayout sec) {
        sec.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout outer = new FrameLayout(context);
        final int outerSide = wp(44f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(outerSide, outerSide);
        op.gravity = Gravity.CENTER_HORIZONTAL;
        op.topMargin = hp(1f);
        op.bottomMargin = hp(2.5f);
        outer.setLayoutParams(op);
        GradientDrawable obg = new GradientDrawable();
        obg.setColor(Color.WHITE);
        obg.setCornerRadius(Math.round(outerSide * 0.19f));
        outer.setBackground(obg);
        outer.setElevation((float) dp(6));
        outer.setRotation(-4f);

        final int innerSide = wp(37.2f);
        final int innerRadius = Math.round(innerSide * 0.19f);
        FrameLayout inner = new FrameLayout(context);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(Color.parseColor("#E8E8E8"));
        ibg.setCornerRadius(innerRadius);
        inner.setBackground(ibg);
        inner.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), innerRadius);
            }
        });
        inner.setClipToOutline(true);
        outer.addView(inner, new FrameLayout.LayoutParams(innerSide, innerSide, Gravity.CENTER));

        logoGlyph = new PhotoGlyph(context);
        inner.addView(logoGlyph, new FrameLayout.LayoutParams(wp(12.2f), wp(12.2f), Gravity.CENTER));

        logoImage = new ImageView(context);
        logoImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoImage.setVisibility(View.GONE);
        inner.addView(logoImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        outer.setClickable(true);
        outer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLogoPicker();
            }
        });

        sec.addView(outer);
        updateLogoView();
    }

    private void updateLogoView() {
        if (logoImage == null || logoGlyph == null) return;
        if (!logoPath.isEmpty()) {
            Bitmap b = decodeSampled(stripFile(logoPath), 400);
            if (b != null) {
                logoImage.setImageBitmap(b);
                logoImage.setVisibility(View.VISIBLE);
                logoGlyph.setVisibility(View.GONE);
                return;
            }
        }
        if (!logoCurrent.isEmpty()) {
            loadCurrentLogo();
            return;
        }
        logoImage.setImageDrawable(null);
        logoImage.setVisibility(View.GONE);
        logoGlyph.setVisibility(View.VISIBLE);
    }

    // Logo actuel de la boutique (adresse http(s) ou fichier local)
    private void loadCurrentLogo() {
        final String src = logoCurrent;
        final int token = ++logoToken;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap b = null;
                HttpURLConnection c = null;
                try {
                    if (src.startsWith("http://") || src.startsWith("https://")) {
                        c = (HttpURLConnection) new URL(src).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        InputStream is = c.getInputStream();
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) > 0) {
                            bos.write(buf, 0, n);
                        }
                        is.close();
                        byte[] bytes = bos.toByteArray();
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                        int sample = 1;
                        while (o.outWidth / (sample * 2) >= 400) sample *= 2;
                        o.inJustDecodeBounds = false;
                        o.inSampleSize = sample;
                        b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                    } else {
                        b = decodeSampled(stripFile(src), 400);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
                final Bitmap bmp = b;
                if (bmp == null) return;
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        if (token != logoToken || logoImage == null || logoGlyph == null) return;
                        if (!logoPath.isEmpty()) return;
                        logoImage.setImageBitmap(bmp);
                        logoImage.setVisibility(View.VISIBLE);
                        logoGlyph.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        if (requestCode != pickRequestCode) return;
        if (resultCode != Activity.RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) {
            OnError("Aucune image sélectionnée (URI nulle).");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = processLogo(uri);
                if (result == null) return;
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        logoPath = result;
                        logoChanged = true;
                        updateLogoView();
                        OnShopLogoPicked(result);
                    }
                });
            }
        }).start();
    }

    // ---- vérification du nom en direct ----

    private boolean nameUnchanged() {
        String n = shopName == null ? "" : shopName.trim();
        return !origName.isEmpty() && n.equalsIgnoreCase(origName);
    }

    private void scheduleNameCheck() {
        if (nameRunnable != null) handler.removeCallbacks(nameRunnable);
        nameSeq++;
        nameState = 0;
        nameChecking = false;
        showNameStatus();

        final String n = shopName == null ? "" : shopName.trim();
        if (n.isEmpty() || nameCheckUrl.isEmpty() || nameUnchanged()) return;

        nameChecking = true;
        final int seq = nameSeq;
        nameRunnable = new Runnable() {
            @Override
            public void run() {
                runNameCheck(n, seq);
            }
        };
        handler.postDelayed(nameRunnable, NAME_DEBOUNCE_MS);
    }

    private String buildNameUrl(String name) throws Exception {
        String enc = URLEncoder.encode(name, "UTF-8");
        String uidEnc = URLEncoder.encode(currentUid == null ? "" : currentUid, "UTF-8");
        String u = nameCheckUrl.replace("{uid}", uidEnc);
        String r;
        if (u.contains("{name}")) {
            r = u.replace("{name}", enc);
        } else {
            r = u + (u.contains("?") ? "&" : "?") + "name=" + enc;
        }
        if (!currentUid.isEmpty() && !nameCheckUrl.contains("{uid}")) {
            r += (r.contains("?") ? "&" : "?") + "uid=" + uidEnc;
        }
        return r;
    }

    private void runNameCheck(final String name, final int seq) {
        final String auth = nameCheckAuth;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] r = httpGet(buildNameUrl(name), auth);
                    int code = Integer.parseInt(r[0]);
                    if (code >= 400) throw new Exception("serveur " + code);
                    Boolean avail = nameAvailableFromJson(new JSONObject(r[1].trim()));
                    if (avail == null) throw new Exception("réponse inattendue (available / exists manquant)");
                    final boolean available = avail.booleanValue();
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (seq != nameSeq) return;
                            nameChecking = false;
                            nameState = available ? 1 : 2;
                            showNameStatus();
                            OnShopNameChecked(name, available);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (seq != nameSeq) return;
                            nameChecking = false;
                            nameState = 3;
                            showNameStatus();
                            OnError("Vérification du nom: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showNameStatus() {
        if (nameStatus == null) return;
        String msg = "";
        int color = COLOR_ERROR;
        if (nameRequiredShown) {
            msg = "Le nom de la boutique est obligatoire";
        } else if (nameUnchanged()) {
            msg = "";
        } else if (nameState == 2) {
            msg = "Ce nom est déjà utilisé";
        } else if (nameState == 1 && shopName != null && !shopName.trim().isEmpty()) {
            msg = "Ce nom est disponible";
            color = COLOR_OK;
        }
        nameStatus.setTextColor(color);
        nameStatus.setText(msg);
        nameStatus.setVisibility(msg.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- lecture et validation (sécurité : nom et catégorie) ----

    @SimpleFunction(description = "Retourne le nom de la boutique saisi.")
    public String GetShopName() {
        return shopName == null ? "" : shopName.trim();
    }

    @SimpleFunction(description = "Retourne la catégorie choisie (vide si aucune).")
    public String GetSelectedCategory() {
        return shopCategory == null ? "" : shopCategory;
    }

    @SimpleFunction(description = "Retourne le téléphone de la boutique saisi.")
    public String GetShopPhone() {
        return shopPhone == null ? "" : shopPhone.trim();
    }

    @SimpleFunction(description = "Retourne l'adresse de la boutique saisie.")
    public String GetShopAddress() {
        return shopAddress == null ? "" : shopAddress.trim();
    }

    @SimpleFunction(description = "Retourne le lien file:// du NOUVEAU logo choisi (vide si le logo n'a pas changé).")
    public String GetShopLogo() {
        return logoPath == null ? "" : logoPath;
    }

    @SimpleFunction(description = "Retourne vrai si au moins un champ (nom, catégorie, téléphone, adresse, logo) a été modifié depuis SetShopEditValues.")
    public boolean IsShopEditChanged() {
        return !GetShopName().equals(origName)
                || !GetSelectedCategory().equals(origCategory)
                || !GetShopPhone().equals(origPhone)
                || !GetShopAddress().equals(origAddress)
                || logoChanged;
    }

    @SimpleFunction(description = "Retourne toute la page en JSON : name, category, phone, address, logo (nouveau logo seulement), uid, changed, formValid.")
    public String GetShopEditJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", GetShopName());
            o.put("category", GetSelectedCategory());
            o.put("phone", GetShopPhone());
            o.put("address", GetShopAddress());
            o.put("logo", GetShopLogo());
            o.put("uid", currentUid);
            o.put("changed", IsShopEditChanged());
            o.put("formValid", firstShopError() == null);
            return o.toString();
        } catch (Exception e) {
            OnError("GetShopEditJson: " + e.getMessage());
            return "{}";
        }
    }

    private String[] firstShopError() {
        if (shopName == null || shopName.trim().isEmpty()) {
            return new String[]{"name", "Le nom de la boutique est obligatoire"};
        }
        if (!nameUnchanged() && nameState == 2) {
            return new String[]{"name", "Ce nom est déjà utilisé"};
        }
        if (!nameUnchanged() && nameChecking) {
            return new String[]{"name", "Vérification du nom en cours, patiente un instant"};
        }
        if (shopCategory == null || shopCategory.trim().isEmpty()) {
            return new String[]{"category", "Choisis une catégorie"};
        }
        return null;
    }

    @SimpleFunction(description = "Vérifie la page. Retourne vrai si le nom (obligatoire, pas déjà utilisé) et la catégorie (obligatoire) sont valides. Sinon retourne faux, affiche l'erreur en rouge sous le champ et déclenche OnShopFormInvalid. À mettre directement dans un « si » sur ton bouton ✓.")
    public boolean ValidateShopEdit() {
        final String[] err = firstShopError();
        if (err == null) return true;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if ("name".equals(err[0])) {
                    if (nameField != null) {
                        nameField.error = true;
                        nameField.updateStroke();
                    }
                    if (shopName == null || shopName.trim().isEmpty()) {
                        nameRequiredShown = true;
                    }
                    showNameStatus();
                } else if ("category".equals(err[0])) {
                    if (catField != null) {
                        catField.error = true;
                        catField.updateStroke();
                    }
                    if (catError != null) {
                        catError.setText(err[1]);
                        catError.setVisibility(View.VISIBLE);
                    }
                }
                OnShopFormInvalid(err[0], err[1]);
            }
        });
        return false;
    }

    @SimpleFunction(description = "Retourne le premier message d'erreur de la page, ou \"\" si tout est valide (sans rien afficher).")
    public String GetShopEditError() {
        String[] err = firstShopError();
        return err == null ? "" : err[1];
    }

    // ---- envoi au serveur (multipart/form-data) ----

    private void mpField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes("UTF-8"));
        out.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".getBytes("UTF-8"));
        out.write((value == null ? "" : value).getBytes("UTF-8"));
        out.write("\r\n".getBytes("UTF-8"));
    }

    private boolean responseIsSuccess(String body) {
        try {
            JSONObject o = new JSONObject(body.trim());
            if (o.has("success")) return o.optBoolean("success");
            if (o.has("error")) return false;
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @SimpleFunction(description = "Envoie les modifications de la boutique au serveur (POST multipart). Champs : action=update_shop, uid, name, category, phone, address, formValid=true, et le fichier logo seulement si un nouveau logo a été choisi. authorization : en-tête Authorization (ex : Bearer <jeton Firebase>). uid : l'UID Firebase de l'utilisateur. Le serveur doit vérifier le jeton et utiliser l'uid du jeton, jamais celui envoyé. La page est vérifiée avant l'envoi. Réponse : OnShopUpdated ou OnShopUpdateFailed (409 = nom déjà pris).")
    public void SubmitShopEdit(final String url, final String authorization, final String uid) {
        if (url == null || url.trim().isEmpty()) {
            OnError("SubmitShopEdit: adresse du serveur vide.");
            return;
        }
        if (uid == null || uid.trim().isEmpty()) {
            OnError("SubmitShopEdit: uid vide.");
            return;
        }
        if (submitting) return;
        if (!ValidateShopEdit()) return;

        submitting = true;
        final String cleanUid = uid.trim();
        currentUid = cleanUid;
        prefs().edit().putString(PREF_CURRENT_UID, cleanUid).apply();

        final Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("action", "update_shop");
        fields.put("uid", cleanUid);
        fields.put("name", GetShopName());
        fields.put("category", GetSelectedCategory());
        fields.put("phone", GetShopPhone());
        fields.put("address", GetShopAddress());
        fields.put("formValid", "true");
        final String logo = logoChanged ? stripFile(GetShopLogo()) : "";
        final String target = url.trim();
        final String auth = authorization == null ? "" : authorization.trim();

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    String boundary = "----Manatest" + System.currentTimeMillis();
                    c = (HttpURLConnection) new URL(target).openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    if (!auth.isEmpty()) {
                        c.setRequestProperty("Authorization", auth);
                    }
                    DataOutputStream out = new DataOutputStream(c.getOutputStream());
                    for (Map.Entry<String, String> e : fields.entrySet()) {
                        mpField(out, boundary, e.getKey(), e.getValue());
                    }
                    if (logo != null && !logo.isEmpty()) {
                        File f = new File(logo);
                        if (f.exists()) {
                            out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                            out.write("Content-Disposition: form-data; name=\"logo\"; filename=\"logo.jpg\"\r\n".getBytes("UTF-8"));
                            out.write("Content-Type: image/jpeg\r\n\r\n".getBytes("UTF-8"));
                            FileInputStream in = new FileInputStream(f);
                            try {
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = in.read(buf)) > 0) {
                                    out.write(buf, 0, n);
                                }
                            } finally {
                                try { in.close(); } catch (Exception ignored) { }
                            }
                            out.write("\r\n".getBytes("UTF-8"));
                        }
                    }
                    out.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
                    out.flush();
                    out.close();

                    final int code = c.getResponseCode();
                    InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    final String body = readAll(is);
                    final boolean ok = code >= 200 && code < 300 && responseIsSuccess(body);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            submitting = false;
                            if (ok) {
                                origName = GetShopName();
                                origCategory = GetSelectedCategory();
                                origPhone = GetShopPhone();
                                origAddress = GetShopAddress();
                                if (logoChanged) {
                                    logoCurrent = logoPath;
                                    logoChanged = false;
                                }
                                nameState = 0;
                                showNameStatus();
                                OnShopUpdated(code, body);
                            } else {
                                if (code == 409) {
                                    nameState = 2;
                                    showNameStatus();
                                }
                                OnShopUpdateFailed(code, body);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            submitting = false;
                            OnError("SubmitShopEdit: " + e.getMessage());
                        }
                    });
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    @SimpleFunction(description = "Vide tous les champs de la page Modifier la boutique.")
    public void ClearShopEditForm() {
        shopName = "";
        shopCategory = "";
        shopPhone = "";
        shopAddress = "";
        logoPath = "";
        logoCurrent = "";
        logoChanged = false;
        origName = "";
        origCategory = "";
        origPhone = "";
        origAddress = "";
        nameState = 0;
        nameChecking = false;
        nameRequiredShown = false;
        nameSeq++;
        if (editContainer != null) renderEdit();
    }

    // =========================================================================
    // PAGE 2 : PRIX
    // =========================================================================

    @SimpleFunction(description = "Construit la page « Prix » dans l'arrangement donné (arrangement vertical dédié : son contenu est remplacé) : prix du produit avec menu HTG / USD, question des frais de livraison avec menu Non / Oui (carte des frais grisée tant que c'est Non), et carte Montant total toujours au même endroit. Branche-la sur OnProductFormFieldClick (id = price).")
    public void BuildPriceForm(final AndroidViewComponent container) {
        if (container == null || container.getView() == null) {
            OnError("BuildPriceForm: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                ViewGroup target = realLayout(container);
                if (target == null) {
                    OnError("BuildPriceForm: conteneur invalide.");
                    return;
                }
                priceContainer = target;
                renderPrice();
            }
        });
    }

    @SimpleFunction(description = "Préremplit la page Prix. basePrice : le prix du produit seul (sans frais). currency : HTG ou USD. deliveryChoice : Oui / Non (ou true / false). deliveryFee : les frais de livraison (ignorés si Non).")
    public void SetPriceValues(String basePrice, String currency, String deliveryChoice, String deliveryFee) {
        BigDecimal p = parseMoney(basePrice, false);
        priceText = p == null ? "" : plainAmount(p);
        priceCurrency = "USD".equalsIgnoreCase(currency == null ? "" : currency.trim())
                || "$".equals(currency == null ? "" : currency.trim()) ? "USD" : "HTG";
        deliveryOn = isYes(deliveryChoice);
        BigDecimal f = deliveryOn ? parseMoney(deliveryFee, true) : null;
        feeText = f == null ? "" : plainAmount(f);
        if (priceContainer != null) renderPrice();
    }

    @SimpleFunction(description = "Vide la page Prix (HTG, Non, aucun montant).")
    public void ClearPriceForm() {
        priceText = "";
        feeText = "";
        priceCurrency = "HTG";
        deliveryOn = false;
        if (priceContainer != null) renderPrice();
    }

    private boolean isYes(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return t.equals("oui") || t.equals("true") || t.equals("yes") || t.equals("1");
    }

    private String curLabel() {
        return "USD".equals(priceCurrency) ? "$" : "HTG";
    }

    // « 5000 » ou « 5 000,5 » : 2 décimales maximum ; null si invalide (ou <= 0 sans allowZero)
    private BigDecimal parseMoney(String text, boolean allowZero) {
        if (text == null) return null;
        String t = text.trim().replace(" ", "").replace("\u00A0", "").replace(",", ".");
        if (!t.matches("\\d{1,12}(\\.\\d{1,2})?")) return null;
        BigDecimal bd = new BigDecimal(t);
        if (!allowZero && bd.signum() <= 0) return null;
        return bd;
    }

    private String plainAmount(BigDecimal bd) {
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatAmount(BigDecimal bd) {
        String s = String.format(Locale.US, "%,.2f", bd);
        String r = s.replace(",", " ").replace(".", ",");
        if (r.endsWith(",00")) r = r.substring(0, r.length() - 3);
        return r;
    }

    private BigDecimal totalValue() {
        BigDecimal p = parseMoney(priceText, false);
        BigDecimal f = deliveryOn ? parseMoney(feeText, true) : null;
        BigDecimal t = p == null ? BigDecimal.ZERO : p;
        if (f != null) t = t.add(f);
        return t;
    }

    private void updateTotal() {
        if (totalValueTv != null) totalValueTv.setText(formatAmount(totalValue()));
        if (totalCurTv != null) totalCurTv.setText(curLabel());
    }

    private void renderPrice() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (priceContainer == null) return;
                    activeContainer = priceContainer;
                    priceContainer.removeAllViews();
                    int numberType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

                    // prix du produit + menu de devise
                    LinearLayout s1 = section();
                    priceField = editField("Prix du produit", numberType, 15,
                            EditorInfo.IME_ACTION_NEXT, priceText, new TextCb() {
                                @Override
                                public void on(String v) {
                                    priceText = v;
                                    if (priceErr != null) priceErr.setVisibility(View.GONE);
                                    updateTotal();
                                }
                            }, 30f);
                    priceCurTv = addSelector(priceField, curLabel(), new Runnable() {
                        @Override
                        public void run() {
                            openCurrencyPopup();
                        }
                    });
                    s1.addView(priceField.box);
                    priceErr = errorText();
                    s1.addView(priceErr);
                    priceContainer.addView(s1);

                    // frais de livraison + menu Non / Oui
                    LinearLayout s2 = section();
                    s2.addView(blockTitle("Voulez-vous ajouter les frais de livraison ?"));
                    feeField = editField("Frais de livraison", numberType, 15,
                            EditorInfo.IME_ACTION_DONE, feeText, new TextCb() {
                                @Override
                                public void on(String v) {
                                    feeText = v;
                                    if (feeErr != null) feeErr.setVisibility(View.GONE);
                                    updateTotal();
                                }
                            }, 30f);
                    deliveryTv = addSelector(feeField, deliveryOn ? "Oui" : "Non", new Runnable() {
                        @Override
                        public void run() {
                            openDeliveryPopup();
                        }
                    });
                    feeField.setDisabled(!deliveryOn);
                    s2.addView(feeField.box);
                    feeErr = errorText();
                    s2.addView(feeErr);
                    priceContainer.addView(s2);

                    // montant total (toujours au même endroit)
                    LinearLayout s3 = section();
                    FrameLayout card = new FrameLayout(context);
                    card.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, hp(9.2f)));
                    GradientDrawable cbg = new GradientDrawable();
                    cbg.setColor(Color.parseColor("#F5F5F5"));
                    cbg.setCornerRadius(wp(4.4f));
                    card.setBackground(cbg);

                    TextView lbl = text("Montant total", 12, cHint(), false);
                    FrameLayout.LayoutParams l1 = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP | Gravity.START);
                    l1.leftMargin = wp(5.5f);
                    l1.topMargin = hp(1.3f);
                    card.addView(lbl, l1);

                    totalValueTv = text("0", 25, cText(), false);
                    FrameLayout.LayoutParams l2 = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM | Gravity.START);
                    l2.leftMargin = wp(5.5f);
                    l2.bottomMargin = hp(1f);
                    card.addView(totalValueTv, l2);

                    totalCurTv = text(curLabel(), 20, cText(), false);
                    FrameLayout.LayoutParams l3 = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM | Gravity.END);
                    l3.rightMargin = wp(5.5f);
                    l3.bottomMargin = hp(1f);
                    card.addView(totalCurTv, l3);

                    s3.addView(card);
                    s3.addView(desc("Seul le montant total sera affiché au client"));
                    priceContainer.addView(s3);

                    addSeparator(priceContainer);
                    updateTotal();
                } catch (Exception e) {
                    OnError("renderPrice: " + e.getMessage());
                }
            }
        });
    }

    private void openCurrencyPopup() {
        final List<String> items = new ArrayList<String>();
        items.add("HTG");
        items.add("USD");
        int w = wp(39f);
        showListPopup(priceField.box, w, priceField.box.getWidth() - w - wp(3.3f), items, priceCurrency,
                new PickCb() {
                    @Override
                    public void on(String v) {
                        setCurrency(v, true);
                    }
                }, null);
    }

    private void setCurrency(String c, boolean fire) {
        priceCurrency = "USD".equals(c) ? "USD" : "HTG";
        if (priceCurTv != null) priceCurTv.setText(curLabel());
        updateTotal();
        if (fire) OnCurrencyChanged(priceCurrency);
    }

    private void openDeliveryPopup() {
        final List<String> items = new ArrayList<String>();
        items.add("Non");
        items.add("Oui");
        int w = wp(39f);
        showListPopup(feeField.box, w, feeField.box.getWidth() - w - wp(3.3f), items,
                deliveryOn ? "Oui" : "Non",
                new PickCb() {
                    @Override
                    public void on(String v) {
                        setDeliveryOn("Oui".equals(v), true);
                    }
                }, null);
    }

    private void setDeliveryOn(boolean on, boolean fire) {
        deliveryOn = on;
        if (deliveryTv != null) deliveryTv.setText(on ? "Oui" : "Non");
        if (!on) {
            feeText = "";
            if (feeField != null && feeField.edit != null) {
                restoring = true;
                feeField.edit.setText("");
                restoring = false;
                feeField.setFloated(false, true);
                feeField.error = false;
                feeField.updateStroke();
            }
            if (feeErr != null) feeErr.setVisibility(View.GONE);
        }
        if (feeField != null) feeField.setDisabled(!on);
        updateTotal();
        if (fire) OnDeliveryChoiceChanged(on);
    }

    // ---- lecture et validation du prix ----

    @SimpleFunction(description = "Retourne le prix du produit seul, à 2 décimales (ex : 5000.50), ou \"\" s'il est vide ou invalide.")
    public String GetPriceAmount() {
        BigDecimal p = parseMoney(priceText, false);
        return p == null ? "" : plainAmount(p);
    }

    @SimpleFunction(description = "Retourne la devise choisie : HTG ou USD.")
    public String GetPriceCurrency() {
        return priceCurrency;
    }

    @SimpleFunction(description = "Retourne le choix des frais de livraison : Oui ou Non.")
    public String GetDeliveryChoice() {
        return deliveryOn ? "Oui" : "Non";
    }

    @SimpleFunction(description = "Retourne vrai si le choix est Oui (livraison incluse), faux si Non. Le serveur le renvoie à la page détails : faux = texte « Livraison inclus » barré.")
    public boolean GetDeliveryIncluded() {
        return deliveryOn;
    }

    @SimpleFunction(description = "Retourne les frais de livraison à 2 décimales (0.00 si Non).")
    public String GetDeliveryFee() {
        BigDecimal f = deliveryOn ? parseMoney(feeText, true) : null;
        return plainAmount(f == null ? BigDecimal.ZERO : f);
    }

    @SimpleFunction(description = "Retourne le montant total (prix + frais si Oui) à 2 décimales. C'est le prix à envoyer au serveur et affiché au client.")
    public String GetPriceTotal() {
        return plainAmount(totalValue());
    }

    @SimpleFunction(description = "Retourne le montant total prêt à afficher : « 5 000,50 HTG » ou « $25 ».")
    public String GetPriceTotalDisplay() {
        String a = formatAmount(totalValue());
        return "USD".equals(priceCurrency) ? "$" + a : a + " HTG";
    }

    @SimpleFunction(description = "Retourne toute la page Prix en JSON : price (total), basePrice, deliveryFee, currency, deliveryIncluded, delivery (Oui / Non), formValid.")
    public String GetPriceFormJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("price", GetPriceTotal());
            o.put("basePrice", GetPriceAmount());
            o.put("deliveryFee", GetDeliveryFee());
            o.put("currency", priceCurrency);
            o.put("deliveryIncluded", deliveryOn);
            o.put("delivery", GetDeliveryChoice());
            o.put("formValid", firstPriceError() == null);
            return o.toString();
        } catch (Exception e) {
            OnError("GetPriceFormJson: " + e.getMessage());
            return "{}";
        }
    }

    private String[] firstPriceError() {
        if (parseMoney(priceText, false) == null) {
            return new String[]{"price", "Entre un prix supérieur à 0 (2 décimales maximum)"};
        }
        if (deliveryOn && parseMoney(feeText, true) == null) {
            return new String[]{"deliveryFee", "Entre les frais de livraison (0 ou plus)"};
        }
        return null;
    }

    @SimpleFunction(description = "Vérifie la page Prix. Retourne faux (et affiche l'erreur en rouge, avec OnPriceFormInvalid) si le prix est vide ou invalide, ou si Oui est choisi sans frais valides. À mettre dans un « si » sur ton bouton ✓.")
    public boolean ValidatePriceForm() {
        final String[] err = firstPriceError();
        if (err == null) return true;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if ("price".equals(err[0])) {
                    if (priceField != null) {
                        priceField.error = true;
                        priceField.updateStroke();
                    }
                    if (priceErr != null) {
                        priceErr.setText(err[1]);
                        priceErr.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (feeField != null) {
                        feeField.error = true;
                        feeField.updateStroke();
                    }
                    if (feeErr != null) {
                        feeErr.setText(err[1]);
                        feeErr.setVisibility(View.VISIBLE);
                    }
                }
                OnPriceFormInvalid(err[0], err[1]);
            }
        });
        return false;
    }

    // =========================================================================
    // PAGE 3 : ÉTAT ET VARIANTES
    // =========================================================================

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<String, Integer>();

    static {
        String[][] c = {
                {"rouge", "#D50000"}, {"red", "#D50000"},
                {"bleu", "#1565C0"}, {"blue", "#1565C0"},
                {"vert", "#2E9E4F"}, {"verte", "#2E9E4F"}, {"green", "#2E9E4F"},
                {"jaune", "#FFD600"}, {"yellow", "#FFD600"},
                {"noir", "#111111"}, {"noire", "#111111"}, {"black", "#111111"},
                {"blanc", "#FFFFFF"}, {"blanche", "#FFFFFF"}, {"white", "#FFFFFF"},
                {"gris", "#9E9E9E"}, {"grise", "#9E9E9E"}, {"gray", "#9E9E9E"}, {"grey", "#9E9E9E"},
                {"rose", "#F48FB1"}, {"pink", "#F48FB1"},
                {"orange", "#FF9800"},
                {"violet", "#7B1FA2"}, {"violette", "#7B1FA2"}, {"purple", "#7B1FA2"},
                {"marron", "#6D4C41"}, {"brown", "#6D4C41"},
                {"beige", "#D9C3A0"},
                {"turquoise", "#1ABC9C"},
                {"bordeaux", "#6D0F1F"},
                {"marine", "#14213D"}, {"navy", "#14213D"},
                {"or", "#D4AF37"}, {"dore", "#D4AF37"}, {"gold", "#D4AF37"},
                {"argent", "#C0C0C0"}, {"argente", "#C0C0C0"}, {"silver", "#C0C0C0"},
                {"kaki", "#8A8B4B"},
                {"saumon", "#FA8072"},
                {"cyan", "#00BCD4"},
                {"magenta", "#D81B60"},
                {"lavande", "#B39DDB"},
                {"creme", "#FFF3D6"}
        };
        for (int i = 0; i < c.length; i++) {
            NAMED_COLORS.put(c[i][0], Color.parseColor(c[i][1]));
        }
    }

    // Reconnaît un nom de couleur (français ou anglais) ou un code #RRGGBB ; null si inconnue
    private Integer resolveColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        try {
            if (s.startsWith("#") && (s.length() == 7 || s.length() == 9)) {
                return Color.parseColor(s);
            }
        } catch (Exception ignored) {
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        Integer base = NAMED_COLORS.get(s);
        if (base == null) {
            String[] tokens = s.split("[\\s,/\\-]+");
            for (int i = 0; i < tokens.length && base == null; i++) {
                String t = tokens[i];
                base = NAMED_COLORS.get(t);
                if (base == null && t.endsWith("s") && t.length() > 2) {
                    base = NAMED_COLORS.get(t.substring(0, t.length() - 1));
                }
            }
        }
        if (base == null) return null;
        if (s.contains("fonc")) return blendColor(base, Color.BLACK, 0.30f);
        if (s.contains("clair") || s.contains("pastel")) return blendColor(base, Color.WHITE, 0.40f);
        return base;
    }

    private int blendColor(int from, int to, float amount) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(r, g, b);
    }

    @SimpleFunction(description = "Construit la page « État et variantes » dans l'arrangement donné (arrangement vertical dédié : son contenu est remplacé) : carte État qui ouvre une boîte de dialogue, puis Couleur (avec une pastille de la couleur reconnue) et Taille. Branche-la sur OnProductFormFieldClick (id = variants, condition, color ou size).")
    public void BuildVariantsForm(final AndroidViewComponent container) {
        if (container == null || container.getView() == null) {
            OnError("BuildVariantsForm: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                ViewGroup target = realLayout(container);
                if (target == null) {
                    OnError("BuildVariantsForm: conteneur invalide.");
                    return;
                }
                variantContainer = target;
                renderVariants();
            }
        });
    }

    @SimpleFunction(description = "Préremplit la page État et variantes. Les valeurs vides sont acceptées.")
    public void SetVariantValues(String condition, String color, String size) {
        this.condition = condition == null ? "" : condition.trim();
        this.color = color == null ? "" : color.trim();
        this.size = size == null ? "" : size.trim();
        if (variantContainer != null) renderVariants();
    }

    @SimpleFunction(description = "Définit la couleur des boutons radio de la boîte de dialogue de l'état (même réglage que dans ManaplaceUtils, #C01A1A1B par défaut).")
    public void SetRadioButtonColor(int color) {
        radioButtonColor = color;
    }

    @SimpleFunction(description = "Remplace la liste des états de la boîte de dialogue : un tableau JSON ou des noms séparés par des virgules. Par défaut : Neuf, Bon état, Comme neuf, Assez bon état.")
    public void SetConditionOptions(String optionsList) {
        if (optionsList == null || optionsList.trim().isEmpty()) return;
        List<String> list = new ArrayList<String>();
        String t = optionsList.trim();
        try {
            if (t.startsWith("[")) {
                JSONArray a = new JSONArray(t);
                for (int i = 0; i < a.length(); i++) {
                    String s = String.valueOf(a.get(i)).trim();
                    if (!s.isEmpty()) list.add(s);
                }
            } else {
                String[] parts = t.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String s = parts[i].trim();
                    if (!s.isEmpty()) list.add(s);
                }
            }
        } catch (Exception e) {
            OnError("SetConditionOptions: " + e.getMessage());
            return;
        }
        if (list.isEmpty()) return;
        conditions.clear();
        conditions.addAll(list);
    }

    @SimpleFunction(description = "Vide la page État et variantes.")
    public void ClearVariantsForm() {
        condition = "";
        color = "";
        size = "";
        if (variantContainer != null) renderVariants();
    }

    private void renderVariants() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (variantContainer == null) return;
                    activeContainer = variantContainer;
                    variantContainer.removeAllViews();

                    // état : carte qui ouvre la boîte de dialogue
                    LinearLayout s1 = section();
                    s1.setPadding(wp(5.5f), hp(3f), wp(5.5f), hp(3f));
                    condField = dropField("État", condition);
                    condField.box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openConditionDialog();
                        }
                    });
                    s1.addView(condField.box);
                    variantContainer.addView(s1);

                    addSeparator(variantContainer);

                    // variantes
                    TextView title = text("Variantes", 15, ink(0xA6), true);
                    title.setPadding(wp(5.5f), hp(2f), wp(5.5f), 0);
                    variantContainer.addView(title);

                    LinearLayout s2 = section();
                    colorField = editField("Couleur",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                            60, EditorInfo.IME_ACTION_NEXT, color, new TextCb() {
                                @Override
                                public void on(String v) {
                                    color = v;
                                    updateSwatch();
                                }
                            }, 5.5f);
                    s2.addView(colorField.box);

                    // pastilles des couleurs reconnues (une par couleur, séparées par une virgule)
                    swatchRow = new LinearLayout(context);
                    swatchRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    sp.leftMargin = wp(4.4f);
                    sp.topMargin = hp(1.3f);
                    swatchRow.setLayoutParams(sp);
                    swatchRow.setVisibility(View.GONE);
                    s2.addView(swatchRow);

                    sizeField = editField("Taille",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                            30, EditorInfo.IME_ACTION_DONE, size, new TextCb() {
                                @Override
                                public void on(String v) {
                                    size = v;
                                }
                            }, 5.5f);
                    LinearLayout.LayoutParams zlp = (LinearLayout.LayoutParams) sizeField.box.getLayoutParams();
                    zlp.topMargin = hp(2f);
                    sizeField.box.setLayoutParams(zlp);
                    s2.addView(sizeField.box);
                    variantContainer.addView(s2);

                    updateSwatch();
                } catch (Exception e) {
                    OnError("renderVariants: " + e.getMessage());
                }
            }
        });
    }

    // « Vert, Bleu , rouge » ou « rouge et noir » : une couleur par élément reconnu
    private List<Integer> resolveColors(String raw) {
        List<Integer> out = new ArrayList<Integer>();
        if (raw == null) return out;
        String[] parts = raw.toLowerCase(Locale.ROOT).split("\\s*(?:,|;|/|\\+|&|\\bet\\b|\\band\\b)\\s*");
        for (int i = 0; i < parts.length && out.size() < 8; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            Integer c = resolveColor(p);
            if (c != null && !out.contains(c)) out.add(c);
        }
        return out;
    }

    private void updateSwatch() {
        if (swatchRow == null) return;
        swatchRow.removeAllViews();
        List<Integer> cols = resolveColors(color);
        if (cols.isEmpty()) {
            swatchRow.setVisibility(View.GONE);
            return;
        }
        int side = wp(7f);
        for (int i = 0; i < cols.size(); i++) {
            View v = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
            if (i > 0) lp.leftMargin = wp(2f);
            v.setLayoutParams(lp);
            GradientDrawable g = new GradientDrawable();
            g.setColor(cols.get(i).intValue());
            g.setStroke(dp(1), Color.BLACK);
            v.setBackground(g);
            swatchRow.addView(v);
        }
        swatchRow.setVisibility(View.VISIBLE);
    }

    private void openConditionDialog() {
        hideKeyboard();
        clearAllFocus();
        if (conditions.isEmpty()) {
            OnError("Aucun état disponible : appelle SetConditionOptions.");
            return;
        }
        try {
            final AlertDialog[] ref = new AlertDialog[1];

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(wp(6f), hp(2.5f), wp(6f), hp(1.5f));
            GradientDrawable wbg = new GradientDrawable();
            wbg.setColor(Color.WHITE);
            wbg.setCornerRadius(wp(7f));
            content.setBackground(wbg);

            TextView title = text("État", 18, ink(0xE9), true);
            title.setPadding(0, 0, 0, hp(1f));
            content.addView(title);

            RadioGroup group = new RadioGroup(context);
            group.setOrientation(LinearLayout.VERTICAL);
            ColorStateList tint = ColorStateList.valueOf(radioButtonColor);
            for (int i = 0; i < conditions.size(); i++) {
                final String item = conditions.get(i);
                RadioButton rb = new RadioButton(context);
                rb.setId(View.generateViewId());
                rb.setText(item);
                rb.setTextColor(ink(0xC0));
                rb.setTextSize(15);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rb.setButtonTintList(tint);
                }
                if (customFont != null) rb.setTypeface(customFont);
                rb.setPadding(wp(2.2f), hp(1.5f), wp(2.2f), hp(1.5f));
                rb.setChecked(item.equals(condition));
                rb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectCondition(item, true);
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
                group.addView(rb);
            }
            content.addView(group);

            TextView cancel = text("Annuler", 14, ink(0xC0), true);
            cancel.setPadding(wp(3f), hp(1.5f), wp(1f), hp(1f));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.gravity = Gravity.END;
            cancel.setLayoutParams(clp);
            cancel.setClickable(true);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ref[0] != null) ref[0].dismiss();
                }
            });
            content.addView(cancel);

            AlertDialog d = new AlertDialog.Builder(activity).setView(content).create();
            ref[0] = d;
            d.show();
            try {
                d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                d.getWindow().setLayout(wp(90f), ViewGroup.LayoutParams.WRAP_CONTENT);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            OnError("openConditionDialog: " + e.getMessage());
        }
    }

    private void selectCondition(String c, boolean fire) {
        condition = c == null ? "" : c;
        if (condField != null) {
            if (condField.value != null) condField.value.setText(condition);
            condField.setFloated(!condition.isEmpty(), true);
        }
        if (fire) OnConditionSelected(condition);
    }

    @SimpleFunction(description = "Retourne l'état choisi (Neuf, Bon état...), vide si aucun.")
    public String GetCondition() {
        return condition == null ? "" : condition;
    }

    @SimpleFunction(description = "Retourne la couleur saisie, vide si aucune.")
    public String GetColor() {
        return color == null ? "" : color.trim();
    }

    @SimpleFunction(description = "Retourne la taille saisie, vide si aucune.")
    public String GetSize() {
        return size == null ? "" : size.trim();
    }

    @SimpleFunction(description = "Retourne la page en JSON : condition, color, size. À brancher sur SetProductFormValue (clés condition, color, size) de ton formulaire produit.")
    public String GetVariantsJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("condition", GetCondition());
            o.put("color", GetColor());
            o.put("size", GetSize());
            return o.toString();
        } catch (Exception e) {
            OnError("GetVariantsJson: " + e.getMessage());
            return "{}";
        }
    }
}
