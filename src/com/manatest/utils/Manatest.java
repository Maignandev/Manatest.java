package com.manatest.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.appinventor.components.runtime.Image;
import com.google.appinventor.components.runtime.PermissionResultHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@DesignerComponent(
        version = 7,
        description = "Manatest - Formulaire « Ajouter un produit » construit en code : titre à saisie directe, multimédia (5 photos), lignes cliquables, stock +/-, validation, brouillon, JSON pour le serveur.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(
        permissionNames =
                "android.permission.READ_EXTERNAL_STORAGE," +
                "android.permission.READ_MEDIA_IMAGES"
)
public class Manatest extends AndroidNonvisibleComponent implements ActivityResultListener {

    private static final String PREFS_NAME = "ManatestProductForm";
    private static final String PREF_DRAFT = "draft";

    private static final int MAX_TITLE = 80;
    private static final int MAX_DESCRIPTION = 1000;
    private static final int MAX_SHORT = 30;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_STOCK = 999999;

    private final Context context;
    private final Activity activity;
    private final Form form;

    // Vraies valeurs (séparées de l'affichage) : clé du champ -> valeur
    private final Map<String, String> values = new LinkedHashMap<String, String>();

    // Photos en cours de sélection (pas encore validées) et état
    private final List<String> pending = new ArrayList<String>();
    private boolean dirty = false;
    private final Image[] boundImages = new Image[MAX_IMAGES];
    private final Map<String, Bitmap> thumbs = new HashMap<String, Bitmap>();
    private int pickRequestCode;
    private int fileCounter = 0;

    private ViewGroup formContainer;
    private EditText titleEdit;
    private boolean updatingTitle = false;

    private Typeface iconFont;
    private String iconPlus = "";
    private String iconMinus = "";
    private String iconPhoto = "";
    private String iconStore = "";
    private String iconTruck = "";
    private int iconSize = 22;
    private String invalidKey = "";

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.form = container.$form();
        this.context = container.$context();
        this.activity = (Activity) container.$context();
        this.pickRequestCode = form.registerForActivityResult(this);
    }

    // =========================================================================
    // OUTILS INTERNES
    // =========================================================================

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics());
    }

    private int col(String hex) {
        return Color.parseColor(hex);
    }

    // #1A1A1B + alpha C0 (écriture RRGGBBAA)
    private int cText() {
        return Color.argb(0xC0, 0x1A, 0x1A, 0x1B);
    }

    // #1A1A1B + alpha E9 (écriture RRGGBBAA)
    private int cHead() {
        return Color.argb(0xE9, 0x1A, 0x1A, 0x1B);
    }

    private boolean has(String key) {
        String v = values.get(key);
        return v != null && !v.isEmpty();
    }

    private String get(String key) {
        String v = values.get(key);
        return v == null ? "" : v;
    }

    private void runOnUi(Runnable r) {
        activity.runOnUiThread(r);
    }

    // Accepte un vrai caractère Phosphor, ou un code hexadécimal (« e3d2 », « \ue3d2 », « U+E3D2 », « &#xE3D2; »)
    private String glyph(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        String h = t.replaceFirst("(?i)^(\\\\u|u\\+|&#x|0x)", "").replace(";", "");
        if (h.length() >= 4 && h.length() <= 6 && h.matches("[0-9a-fA-F]+")) {
            try {
                return new String(Character.toChars(Integer.parseInt(h, 16)));
            } catch (Exception ignored) {
            }
        }
        return t;
    }

    // Retrouve la vraie vue où ajouter les lignes (déroule les enveloppes d'arrangement / défilement)
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

    private Typeface loadFont(String fontPath) {
        if (fontPath == null || fontPath.trim().isEmpty()) return null;
        try {
            if (fontPath.startsWith("/")) {
                return Typeface.createFromFile(new File(fontPath));
            }
            return Typeface.createFromAsset(context.getAssets(), fontPath);
        } catch (Exception e) {
            OnError("BuildProductForm: police introuvable (" + fontPath + ").");
            return null;
        }
    }

    // =========================================================================
    // ÉVÉNEMENTS
    // =========================================================================

    @SimpleEvent(description = "L'utilisateur a touché une ligne du formulaire. fieldKey : description, category, price, variants, condition, color, size, stock (bouton Modifier), address ou delivery. Ouvre le panneau de saisie correspondant. (Le titre se saisit directement dans le formulaire, et les photos s'ouvrent toutes seules.)")
    public void OnProductFormFieldClick(String fieldKey) {
        EventDispatcher.dispatchEvent(this, "OnProductFormFieldClick", fieldKey);
    }

    @SimpleEvent(description = "Déclenché après sélection : les photos sont validées (5 photos atteintes, ou bouton OK touché). photosJson : liste JSON de liens file:// ; count : nombre de photos.")
    public void OnPhotoPicked(String photosJson, int count) {
        EventDispatcher.dispatchEvent(this, "OnPhotoPicked", photosJson, count);
    }

    @SimpleEvent(description = "Le titre a changé pendant la saisie directe dans le formulaire.")
    public void OnProductTitleChanged(String title) {
        EventDispatcher.dispatchEvent(this, "OnProductTitleChanged", title);
    }

    @SimpleEvent(description = "Le stock a changé avec les boutons plus / moins.")
    public void OnProductStockChanged(int stock) {
        EventDispatcher.dispatchEvent(this, "OnProductStockChanged", stock);
    }

    @SimpleEvent(description = "ValidateProductForm a trouvé un champ invalide. La ligne concernée passe en rouge.")
    public void OnProductFormInvalid(String fieldKey, String message) {
        EventDispatcher.dispatchEvent(this, "OnProductFormInvalid", fieldKey, message);
    }

    @SimpleEvent(description = "Une erreur s'est produite.")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONSTRUCTION DU FORMULAIRE
    // =========================================================================

    @SimpleFunction(description = "Construit le formulaire « Ajouter un produit » dans l'arrangement donné. fontPath : police Phosphor (ex: Phosphor-Bold.ttf). plusChar : icône « + » . minusChar : icône « - » du stock. photoChar : icône photo. storeChar : icône adresse. truckChar : icône livraison. Caractère Phosphor ou code hexadécimal (ex: e3d2). Vide = pas d'icône. iconSize : taille des icônes (ex: 22).")
    public void BuildProductForm(
            final AndroidViewComponent container,
            final String fontPath,
            final String plusChar,
            final String minusChar,
            final String photoChar,
            final String storeChar,
            final String truckChar,
            final int iconSize) {
        if (container == null || container.getView() == null) {
            OnError("BuildProductForm: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup target = realLayout(container);
                    if (target == null) {
                        OnError("BuildProductForm: conteneur invalide.");
                        return;
                    }
                    formContainer = target;
                    iconFont = loadFont(fontPath);
                    iconPlus = glyph(plusChar);
                    iconMinus = glyph(minusChar);
                    iconPhoto = glyph(photoChar);
                    iconStore = glyph(storeChar);
                    iconTruck = glyph(truckChar);
                    Manatest.this.iconSize = iconSize > 0 ? iconSize : 22;
                    loadPendingFromValues();
                    render();
                } catch (Exception e) {
                    OnError("BuildProductForm: " + e.getMessage());
                }
            }
        });
    }

    private void render() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (formContainer == null) return;
                    formContainer.removeAllViews();

                    addMediaSection();
                    addTitleRow();
                    addDivider();
                    addIconTextRow("description", iconPlus, "Description du produit", 2);
                    addDivider();
                    addIconTextRow("category", iconPlus, "Choisir une catégorie", 1);
                    addDivider();
                    addPriceRow();
                    addSeparator();
                    addVariantsSection();
                    addSeparator();
                    addStockSection();
                    addSeparator();
                    addAddressSection();
                    addSeparator();
                    addDeliveryRow();
                    addSeparator();
                } catch (Exception e) {
                    OnError("render: " + e.getMessage());
                }
            }
        });
    }

    // ---- éléments de base ----

    private LinearLayout rowBase(int left, int top, int right, int bottom, boolean horizontal) {
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        r.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        return r;
    }

    private TextView text(String s, int sp, int color, boolean bold, int maxLines) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        if (maxLines > 0) {
            t.setMaxLines(maxLines);
            t.setEllipsize(TextUtils.TruncateAt.END);
        }
        return t;
    }

    private void weight(View v) {
        v.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private TextView icon(String ch, int sp, int color, int marginRightDp) {
        if (iconFont == null || ch == null || ch.isEmpty()) return null;
        TextView t = new TextView(context);
        t.setText(ch);
        t.setTypeface(iconFont);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) dp(sp));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(marginRightDp), 0);
        t.setLayoutParams(p);
        return t;
    }

    private void clickable(View v, final String key) {
        v.setClickable(true);
        try {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true)) {
                v.setBackgroundResource(tv.resourceId);
            }
        } catch (Exception ignored) {
        }
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                OnProductFormFieldClick(key);
            }
        });
    }

    private void addDivider() {
        View d = new View(context);
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        d.setBackgroundColor(col("#EEEEEE"));
        formContainer.addView(d);
    }

    private void addSeparator() {
        View d = new View(context);
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        d.setBackgroundColor(col("#F2F2F2"));
        formContainer.addView(d);
    }

    private int valueColor(String key) {
        if (key.equals(invalidKey)) return col("#D93025");
        return has(key) ? cText() : col("#6E6E73");
    }

    private int iconColor() {
        return col("#1A1A1B");
    }

    // ---- section multimédia ----

    private Bitmap thumb(String path) {
        Bitmap cached = thumbs.get(path);
        if (cached != null) return cached;
        try {
            String p = path.startsWith("file://") ? path.substring(7) : path;
            if (!new File(p).exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(p, o);
            int s = 1;
            while ((o.outWidth / (s * 2)) >= 400 && (o.outHeight / (s * 2)) >= 400) {
                s *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(p, o);
            if (b != null) thumbs.put(path, b);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private LinearLayout tileBase(int fillColor, int strokeColor) {
        LinearLayout tile = new LinearLayout(context);
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(130), dp(130));
        lp.setMargins(0, 0, dp(12), 0);
        tile.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(20));
        if (strokeColor != 0) {
            bg.setStroke(dp(1), strokeColor);
        }
        tile.setBackground(bg);
        return tile;
    }

    private void addMediaSection() {
        LinearLayout head = rowBase(22, 16, 22, 10, true);
        head.addView(text("Contenu multimédia", 18, cHead(), true, 1));
        TextView count = text("  (" + pending.size() + "/" + MAX_IMAGES + ")", 18,
                col("#6E6E73"), false, 1);
        head.addView(count);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(spacer);

        if (dirty) {
            TextView ok = text("OK", 18, Color.WHITE, true, 1);
            ok.setGravity(Gravity.CENTER);
            ok.setPadding(dp(22), dp(6), dp(22), dp(6));
            GradientDrawable okBg = new GradientDrawable();
            okBg.setColor(col("#3949AB"));
            okBg.setCornerRadius(dp(20));
            ok.setBackground(okBg);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ConfirmProductPhotos();
                }
            });
            head.addView(ok);
        }
        formContainer.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(22), dp(4), dp(10), dp(4));

        View.OnClickListener pick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenPhotoPicker();
            }
        };

        if (pending.isEmpty()) {
            // Grande tuile grise avec l'icône photo
            LinearLayout ph = tileBase(col("#F5F5F5"), 0);
            TextView ic = icon(iconPhoto, iconSize + 12, col("#9A9AA0"), 0);
            if (ic != null) ph.addView(ic);
            ph.setOnClickListener(pick);
            strip.addView(ph);
        } else {
            for (int i = 0; i < pending.size(); i++) {
                strip.addView(filledTile(i, pending.get(i)));
            }
        }

        if (pending.size() < MAX_IMAGES) {
            // Petit carré blanc avec « + » au milieu
            LinearLayout plus = tileBase(Color.WHITE, col("#EEEEEE"));
            TextView ic = icon(iconPlus, iconSize + 6, col("#6E6E73"), 0);
            if (ic != null) {
                plus.addView(ic);
            } else {
                TextView fb = text("+", iconSize + 12, col("#6E6E73"), false, 1);
                plus.addView(fb);
            }
            plus.setOnClickListener(pick);
            strip.addView(plus);
        }

        hs.addView(strip);
        formContainer.addView(hs);

        TextView hint = text(pending.isEmpty() ? "Ajouter des photos"
                : "Maintiens une photo pour la retirer",
                pending.isEmpty() ? 22 : 14, col("#6E6E73"), false, 1);
        hint.setGravity(Gravity.CENTER);
        hint.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        hint.setPadding(dp(16), dp(12), dp(16), dp(18));
        formContainer.addView(hint);
    }

    private View filledTile(final int index, String path) {
        ImageView iv = new ImageView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(130), dp(130));
        lp.setMargins(0, 0, dp(12), 0);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(col("#F5F5F5"));
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        iv.setClipToOutline(true);
        Bitmap b = thumb(path);
        if (b != null) {
            iv.setImageBitmap(b);
        }
        iv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (index >= 0 && index < pending.size()) {
                    pending.remove(index);
                    dirty = true;
                    render();
                }
                return true;
            }
        });
        return iv;
    }

    // ---- ouverture du sélecteur de photos (jusqu'à 5) ----

    @SimpleFunction(description = "Ouvre la galerie d'images native (avec demande de permission) pour choisir des photos, 5 au maximum au total. Appelée automatiquement quand on touche une tuile photo. OnPhotoPicked se déclenche quand les 5 photos sont atteintes ou quand l'utilisateur touche OK.")
    public void OpenPhotoPicker() {
        if (MAX_IMAGES - pending.size() <= 0) {
            OnError("Maximum " + MAX_IMAGES + " photos.");
            return;
        }
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
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("image/*");
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                            form.startActivityForResult(i, pickRequestCode);
                        } catch (Exception e) {
                            OnError("OpenPhotoPicker: " + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        if (requestCode != pickRequestCode || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        final List<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        int remaining = MAX_IMAGES - pending.size();
        while (uris.size() > remaining) {
            uris.remove(uris.size() - 1);
        }
        if (remaining <= 0) {
            OnError("Maximum " + MAX_IMAGES + " photos.");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> done = new ArrayList<String>();
                for (int i = 0; i < uris.size(); i++) {
                    String p = copyToCache(uris.get(i));
                    if (p != null) done.add(p);
                }
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < done.size(); i++) {
                            if (pending.size() >= MAX_IMAGES) break;
                            pending.add(done.get(i));
                        }
                        dirty = true;
                        render();
                        if (pending.size() >= MAX_IMAGES) {
                            ConfirmProductPhotos();
                        }
                    }
                });
            }
        }).start();
    }

    private String copyToCache(Uri uri) {
        try {
            File dir = new File(context.getCacheDir(), "manatest_photos");
            dir.mkdirs();
            File f = new File(dir, "p_" + System.currentTimeMillis() + "_" + (fileCounter++) + ".jpg");
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream out = new FileOutputStream(f);
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
            return "file://" + f.getAbsolutePath();
        } catch (Exception e) {
            OnError("Photo: " + e.getMessage());
            return null;
        }
    }

    @SimpleFunction(description = "Valide les photos choisies (équivaut au bouton OK). Enregistre la liste, met à jour les composants Image reliés et déclenche OnPhotoPicked.")
    public void ConfirmProductPhotos() {
        try {
            JSONArray a = new JSONArray(pending);
            if (pending.isEmpty()) {
                values.remove("images");
            } else {
                values.put("images", a.toString());
            }
            dirty = false;
            saveDraft();
            bindImages();
            render();
            OnPhotoPicked(a.toString(), pending.size());
        } catch (Exception e) {
            OnError("ConfirmProductPhotos: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Relie jusqu'à 5 vrais composants Image Kodular : leur Picture est mise à jour avec les photos validées (image1 = première photo, etc.). Un composant vide est ignoré.")
    public void BindProductImages(Image image1, Image image2, Image image3, Image image4, Image image5) {
        boundImages[0] = image1;
        boundImages[1] = image2;
        boundImages[2] = image3;
        boundImages[3] = image4;
        boundImages[4] = image5;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                bindImages();
            }
        });
    }

    private void bindImages() {
        List<String> ok = confirmedImages();
        for (int i = 0; i < MAX_IMAGES; i++) {
            Image img = boundImages[i];
            if (img == null) continue;
            try {
                img.Picture(i < ok.size() ? ok.get(i) : "");
            } catch (Exception e) {
                OnError("BindProductImages: " + e.getMessage());
            }
        }
    }

    private List<String> confirmedImages() {
        List<String> l = new ArrayList<String>();
        try {
            if (has("images")) {
                JSONArray a = new JSONArray(get("images"));
                for (int i = 0; i < a.length() && i < MAX_IMAGES; i++) {
                    String s = a.optString(i, "");
                    if (!s.isEmpty()) l.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    private void loadPendingFromValues() {
        pending.clear();
        pending.addAll(confirmedImages());
        dirty = false;
    }

    @SimpleFunction(description = "Retourne la liste JSON des photos validées (liens file://).")
    public String GetProductFormImages() {
        return new JSONArray(confirmedImages()).toString();
    }

    @SimpleFunction(description = "Retourne le lien de la photo validée à la position index (1 à 5), ou \"\".")
    public String GetProductFormImage(int index) {
        List<String> l = confirmedImages();
        return (index >= 1 && index <= l.size()) ? l.get(index - 1) : "";
    }

    @SimpleFunction(description = "Retourne le nombre de photos validées.")
    public int GetProductFormImageCount() {
        return confirmedImages().size();
    }

    // ---- titre : saisie directe ----

    private void createTitleEdit() {
        titleEdit = new EditText(context);
        titleEdit.setHint("Titre du produit");
        titleEdit.setHintTextColor(col("#6E6E73"));
        titleEdit.setTextSize(22);
        titleEdit.setTextColor(cText());
        titleEdit.setBackground(null);
        titleEdit.setSingleLine(true);
        titleEdit.setPadding(0, dp(10), 0, dp(10));
        titleEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TITLE)});
        titleEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        titleEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (updatingTitle) return;
                String v = e.toString().trim();
                if (v.isEmpty()) {
                    values.remove("title");
                } else {
                    values.put("title", v);
                }
                if (invalidKey.equals("title")) {
                    invalidKey = "";
                }
                titleEdit.setTextColor(cText());
                saveDraft();
                OnProductTitleChanged(v);
            }
        });
        titleEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        InputMethodManager imm = (InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    } catch (Exception ignored) {
                    }
                    v.clearFocus();
                    return true;
                }
                return false;
            }
        });
    }

    private void syncTitle() {
        if (titleEdit == null) return;
        String want = get("title");
        if (!titleEdit.getText().toString().trim().equals(want)) {
            updatingTitle = true;
            titleEdit.setText(want);
            titleEdit.setSelection(titleEdit.getText().length());
            updatingTitle = false;
        }
    }

    private void addTitleRow() {
        if (titleEdit == null) createTitleEdit();
        syncTitle();
        ViewGroup old = (ViewGroup) titleEdit.getParent();
        if (old != null) old.removeView(titleEdit);

        LinearLayout row = rowBase(22, 12, 16, 12, true);
        titleEdit.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleEdit.setTextColor(valueColor("title"));
        row.addView(titleEdit);
        formContainer.addView(row);
    }

    // ---- lignes ----

    private void addIconTextRow(String key, String iconChar, String placeholder, int maxLines) {
        LinearLayout row = rowBase(22, 22, 16, 22, true);
        TextView ic = icon(iconChar, iconSize, iconColor(), 14);
        if (ic != null) {
            row.addView(ic);
        }
        TextView t = text(has(key) ? get(key) : placeholder, 22, valueColor(key), false, maxLines);
        weight(t);
        row.addView(t);
        clickable(row, key);
        formContainer.addView(row);
    }

    private void addPriceRow() {
        LinearLayout row = rowBase(22, 22, 16, 22, true);
        String shown = has("price") ? formatPrice(get("price")) : "0,00 HTG";
        TextView t = text(shown, 22, valueColor("price"), false, 1);
        weight(t);
        row.addView(t);
        clickable(row, "price");
        formContainer.addView(row);
    }

    private void addVariantsSection() {
        LinearLayout head = rowBase(22, 18, 16, 6, false);
        head.addView(text("État et variantes", 18, cHead(), true, 1));
        formContainer.addView(head);

        boolean any = has("condition") || has("color") || has("size");
        if (!any) {
            LinearLayout row = rowBase(22, 16, 16, 24, true);
            TextView ic = icon(iconPlus, iconSize, iconColor(), 14);
            if (ic != null) {
                row.addView(ic);
            }
            TextView t = text("Plus d'options (neuf, couleur...)", 22, col("#6E6E73"), false, 1);
            weight(t);
            row.addView(t);
            clickable(row, "variants");
            formContainer.addView(row);
            return;
        }

        String[][] lines = {
                {"condition", "État"},
                {"color", "Couleur"},
                {"size", "Taille"}
        };
        for (int i = 0; i < lines.length; i++) {
            String key = lines[i][0];
            LinearLayout row = rowBase(22, 8, 22, i == lines.length - 1 ? 16 : 8, true);
            TextView label = text(lines[i][1], 22, cText(), false, 1);
            weight(label);
            row.addView(label);
            TextView value = text(has(key) ? get(key) : "—", 22, valueColor(key), false, 1);
            value.setGravity(Gravity.END);
            value.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(value);
            clickable(row, key);
            formContainer.addView(row);
        }
    }

    // ---- stock ----

    private int currentStock() {
        try {
            return has("stock") ? Integer.parseInt(get("stock")) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void changeStock(int delta) {
        int n = Math.max(0, Math.min(MAX_STOCK, currentStock() + delta));
        values.put("stock", String.valueOf(n));
        if (invalidKey.equals("stock")) invalidKey = "";
        saveDraft();
        render();
        OnProductStockChanged(n);
    }

    private TextView stepButton(String glyphChar, String fallback, final int delta) {
        TextView b = new TextView(context);
        b.setGravity(Gravity.CENTER);
        if (iconFont != null && glyphChar != null && !glyphChar.isEmpty()) {
            b.setText(glyphChar);
            b.setTypeface(iconFont);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) dp(iconSize));
            b.setIncludeFontPadding(false);
        } else {
            b.setText(fallback);
            b.setTextSize(iconSize + 8);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        b.setTextColor(iconColor());
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeStock(delta);
            }
        });
        return b;
    }

    private void addStockSection() {
        LinearLayout head = rowBase(22, 18, 22, 6, true);
        TextView title = text("Stock", 18, cHead(), true, 1);
        weight(title);
        head.addView(title);
        TextView modify = text("Modifier", 18, col("#3949AB"), false, 1);
        modify.setPadding(dp(8), dp(4), 0, dp(4));
        modify.setClickable(true);
        modify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnProductFormFieldClick("stock");
            }
        });
        head.addView(modify);
        formContainer.addView(head);

        LinearLayout row = rowBase(22, 6, 12, 18, true);
        TextView label = text("Disponible", 22, cText(), false, 1);
        weight(label);
        row.addView(label);

        row.addView(stepButton(iconMinus, "−", -1));

        TextView box = text(String.valueOf(currentStock()), 22, valueColor("stock"), false, 1);
        box.setGravity(Gravity.CENTER);
        box.setMinWidth(dp(90));
        box.setMinHeight(dp(42));
        box.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(col("#F5F5F5"));
        boxBg.setCornerRadius(dp(12));
        box.setBackground(boxBg);
        row.addView(box);

        row.addView(stepButton(iconPlus, "+", 1));
        formContainer.addView(row);
    }

    // ---- adresse / livraison ----

    private void addAddressSection() {
        LinearLayout row = rowBase(22, 18, 22, 18, false);
        row.setGravity(Gravity.START);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic = icon(iconStore, iconSize, iconColor(), 14);
        if (ic != null) {
            head.addView(ic);
        }
        head.addView(text("Adresse", 18, cHead(), true, 1));
        row.addView(head);

        TextView value = text(has("address") ? get("address") : "Ajouter une adresse", 22,
                valueColor("address"), false, 2);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, dp(8), 0, 0);
        value.setLayoutParams(vp);
        row.addView(value);

        clickable(row, "address");
        formContainer.addView(row);
    }

    private void addDeliveryRow() {
        LinearLayout row = rowBase(22, 22, 22, 22, true);
        TextView ic = icon(iconTruck, iconSize, iconColor(), 14);
        if (ic != null) {
            row.addView(ic);
        }
        TextView label = text("Livraison", 22, cText(), false, 1);
        weight(label);
        row.addView(label);
        if (has("delivery")) {
            row.addView(text(get("delivery"), 22, valueColor("delivery"), false, 1));
        }
        clickable(row, "delivery");
        formContainer.addView(row);
    }

    // =========================================================================
    // VALEURS : SET / GET
    // =========================================================================

    @SimpleFunction(description = "Enregistre la valeur d'un champ et met à jour son affichage. fieldKey : title, description, category, category_id (non affiché), price, condition, color, size, stock, address, delivery, images (liste JSON de liens). Le texte long est coupé par « … » seulement à l'écran : la vraie valeur reste entière. Une valeur vide efface le champ.")
    public void SetProductFormValue(String fieldKey, String value) {
        String k = fieldKey == null ? "" : fieldKey.trim();
        String v = value == null ? "" : value.trim();
        if (k.isEmpty()) {
            OnError("SetProductFormValue: fieldKey vide.");
            return;
        }
        if (k.equals("price") && !v.isEmpty()) {
            String n = normalizePrice(v);
            if (n.isEmpty()) {
                OnError("SetProductFormValue: prix invalide (nombre supérieur à 0, 2 décimales maximum).");
                return;
            }
            v = n;
        }
        if (k.equals("stock") && !v.isEmpty()) {
            if (!v.matches("\\d{1,6}")) {
                OnError("SetProductFormValue: le stock doit être un nombre entier (0 ou plus).");
                return;
            }
            v = String.valueOf(Integer.parseInt(v));
        }
        if (k.equals("images") && !v.isEmpty()) {
            try {
                new JSONArray(v);
            } catch (Exception e) {
                OnError("SetProductFormValue: images doit être une liste JSON.");
                return;
            }
        }
        if (v.isEmpty()) {
            values.remove(k);
        } else {
            values.put(k, v);
        }
        if (k.equals(invalidKey)) {
            invalidKey = "";
        }
        if (k.equals("images")) {
            loadPendingFromValues();
            bindImages();
        }
        saveDraft();
        render();
    }

    @SimpleFunction(description = "Retourne la vraie valeur entière d'un champ (pas le texte coupé de l'écran). Sert à préremplir un panneau de saisie.")
    public String GetProductFormValue(String fieldKey) {
        return fieldKey == null ? "" : get(fieldKey.trim());
    }

    @SimpleFunction(description = "Retourne le stock actuel (nombre entier, 0 par défaut). Utile pour l'afficher ailleurs ou pour l'envoi au serveur.")
    public int GetProductFormStock() {
        return currentStock();
    }

    @SimpleFunction(description = "Retourne le produit complet en JSON, prêt à envoyer au serveur : title, description, category, category_id, price (nombre), currency (HTG), condition, color, size, stock (nombre), address, delivery, deliveryIncluded, images (liste), image, image2 à image5.")
    public String GetProductFormJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("title", get("title"));
            o.put("description", get("description"));
            o.put("category", get("category"));
            if (has("category_id")) {
                o.put("category_id", get("category_id"));
            }
            String p = get("price");
            o.put("price", p.isEmpty() ? 0 : new BigDecimal(p).doubleValue());
            o.put("currency", "HTG");
            o.put("condition", get("condition"));
            o.put("color", get("color"));
            o.put("size", get("size"));
            o.put("stock", currentStock());
            o.put("address", get("address"));
            o.put("delivery", get("delivery"));
            o.put("deliveryIncluded", isTruthy(get("delivery")));
            List<String> imgs = confirmedImages();
            o.put("images", new JSONArray(imgs));
            String[] names = {"image", "image2", "image3", "image4", "image5"};
            for (int i = 0; i < imgs.size() && i < names.length; i++) {
                o.put(names[i], imgs.get(i));
            }
            return o.toString();
        } catch (Exception e) {
            OnError("GetProductFormJson: " + e.getMessage());
            return "{}";
        }
    }

    private boolean isTruthy(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.US);
        return t.equals("true") || t.equals("oui") || t.equals("yes") || t.equals("1")
                || t.equals("inclus") || t.equals("incluse") || t.equals("included");
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    @SimpleFunction(description = "Vérifie la valeur d'un champ avant de l'enregistrer. Retourne \"\" si tout est bon, sinon le message d'erreur à afficher. Règles : titre obligatoire (80 caractères max), description (1000 max), catégorie obligatoire, prix supérieur à 0 (2 décimales max), stock entier, état/couleur/taille (30 max).")
    public String ValidateProductField(String fieldKey, String value) {
        String k = fieldKey == null ? "" : fieldKey.trim();
        String v = value == null ? "" : value.trim();
        int len = v.length();
        if (k.equals("title")) {
            if (len == 0) return "Le titre est obligatoire.";
            if (len > MAX_TITLE) return "Titre trop long : " + len + "/" + MAX_TITLE + ".";
        } else if (k.equals("description")) {
            if (len > MAX_DESCRIPTION) {
                return "Description trop longue : " + len + "/" + MAX_DESCRIPTION + ".";
            }
        } else if (k.equals("category")) {
            if (len == 0) return "Choisis une catégorie.";
        } else if (k.equals("price")) {
            if (normalizePrice(v).isEmpty()) {
                return "Prix invalide : entre un nombre supérieur à 0 (2 décimales maximum).";
            }
        } else if (k.equals("stock")) {
            if (!v.matches("\\d{1,6}")) return "Le stock doit être un nombre entier (0 ou plus).";
        } else if (k.equals("condition") || k.equals("color") || k.equals("size")) {
            if (len > MAX_SHORT) return "Trop long : " + len + "/" + MAX_SHORT + ".";
        }
        return "";
    }

    @SimpleFunction(description = "Vérifie tout le formulaire avant l'envoi. Retourne \"\" si tout est bon, sinon le premier message d'erreur. La ligne concernée passe en rouge et OnProductFormInvalid est déclenché.")
    public String ValidateProductForm() {
        String[] order = {"title", "category", "price", "description", "stock",
                "condition", "color", "size"};
        for (int i = 0; i < order.length; i++) {
            String k = order[i];
            boolean optional = !(k.equals("title") || k.equals("category") || k.equals("price"));
            if (optional && !has(k)) continue;
            String msg = ValidateProductField(k, get(k));
            if (!msg.isEmpty()) {
                invalidKey = k;
                render();
                OnProductFormInvalid(k, msg);
                return msg;
            }
        }
        if (!invalidKey.isEmpty()) {
            invalidKey = "";
            render();
        }
        return "";
    }

    // =========================================================================
    // PRIX ET TEXTE
    // =========================================================================

    private String normalizePrice(String text) {
        if (text == null) return "";
        String t = text.trim().replace(" ", "").replace("\u00A0", "").replace(",", ".");
        if (!t.matches("\\d{1,12}(\\.\\d{1,2})?")) return "";
        BigDecimal bd = new BigDecimal(t);
        if (bd.signum() <= 0) return "";
        return bd.setScale(2).toPlainString();
    }

    private String formatPrice(String amount) {
        try {
            BigDecimal bd = new BigDecimal(amount);
            String s = String.format(Locale.US, "%,.2f", bd);
            return s.replace(",", " ").replace(".", ",") + " HTG";
        } catch (Exception e) {
            return amount + " HTG";
        }
    }

    @SimpleFunction(description = "Transforme un prix saisi (« 5000 », « 5 000,5 ») en nombre à 2 décimales (« 5000.50 »). Retourne \"\" si le format est invalide ou si le prix n'est pas supérieur à 0.")
    public String NormalizePrice(String text) {
        return normalizePrice(text);
    }

    @SimpleFunction(description = "Met un prix en forme pour l'affichage : « 5 000,50 HTG ».")
    public String FormatPriceDisplay(String amount) {
        String n = normalizePrice(amount);
        return n.isEmpty() ? "0,00 HTG" : formatPrice(n);
    }

    @SimpleFunction(description = "Coupe un texte à maxChars caractères en ajoutant « … » (pour l'affichage seulement).")
    public String ShortenText(String text, int maxChars) {
        String t = text == null ? "" : text;
        if (maxChars <= 0 || t.length() <= maxChars) return t;
        return t.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }

    // =========================================================================
    // BROUILLON
    // =========================================================================

    private void saveDraft() {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            p.edit().putString(PREF_DRAFT, new JSONObject(values).toString()).apply();
        } catch (Exception e) {
            OnError("Brouillon: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Remet dans le formulaire le brouillon sauvegardé dans le téléphone (chaque modification le met à jour). Retourne vrai si un brouillon a été restauré. À appeler après BuildProductForm.")
    public boolean RestoreProductFormDraft() {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = p.getString(PREF_DRAFT, "");
            if (raw == null || raw.isEmpty()) return false;
            JSONObject o = new JSONObject(raw);
            values.clear();
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k, "");
                if (!v.isEmpty()) {
                    values.put(k, v);
                }
            }
            loadPendingFromValues();
            bindImages();
            render();
            return !values.isEmpty();
        } catch (Exception e) {
            OnError("RestoreProductFormDraft: " + e.getMessage());
            return false;
        }
    }

    @SimpleFunction(description = "Efface toutes les valeurs, les photos et le brouillon, et remet le formulaire à zéro (pour un nouveau produit).")
    public void ClearProductForm() {
        values.clear();
        pending.clear();
        thumbs.clear();
        dirty = false;
        invalidKey = "";
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(PREF_DRAFT).apply();
        } catch (Exception ignored) {
        }
        bindImages();
        render();
    }
}
