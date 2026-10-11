package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DesignerComponent(
        version = 9,
        description = "Manatest - Formulaire « Créer ma boutique » construit en code : champs à libellé animé, vérification instantanée du nom, spinner de catégories, clavier numérique pour le téléphone, logo, envoi au serveur et statut vendeur permanent.",
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

    // Préférences partagées avec ManaplaceUtils (même nom de fichier, mêmes clés)
    private static final String PREFS_NAME = "ManaplaceShop";
    private static final String PREF_CURRENT_UID = "current_uid";
    private static final String PREF_SELLER_PREFIX = "is_seller_";

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

    // Valeurs saisies (séparées de l'affichage : elles survivent à un nouveau rendu)
    private String shopName = "";
    private String shopCategory = "";
    private String shopPhone = "";
    private String shopAddress = "";
    private String logoPath = "";
    private final List<String> categories = new ArrayList<String>();

    private ViewGroup formContainer;
    private Typeface customFont;
    private Typeface customBoldFont;
    private boolean restoring = false;

    // Vues
    private FieldView nameField;
    private FieldView phoneField;
    private FieldView addressField;
    private FieldView catField;
    private TextView catValue;
    private TextView catError;
    private TextView nameStatus;
    private ImageView logoImage;
    private View logoGlyph;

    // Vérification du nom
    private String nameCheckUrl = "";
    private String nameCheckAuth = "";
    private int nameSeq = 0;
    private boolean nameChecking = false;
    private int nameState = 0; // 0 inconnu, 1 disponible, 2 déjà utilisé, 3 erreur réseau
    private boolean nameRequiredShown = false;
    private Runnable nameRunnable;

    // Envoi et statut vendeur
    private boolean submitting = false;
    private volatile boolean sellerListening = false;
    private Thread sellerThread;

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

    // #1A1A1B avec un alpha donné
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

    private void hideKeyboard() {
        try {
            View v = activity.getCurrentFocus();
            if (v == null) v = formContainer;
            InputMethodManager imm =
                    (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
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

    // =========================================================================
    // ÉVÉNEMENTS
    // =========================================================================

    @SimpleEvent(description = "Une catégorie vient d'être choisie dans le spinner. category : le nom choisi.")
    public void AfterCategorySelected(String category) {
        EventDispatcher.dispatchEvent(this, "AfterCategorySelected", category);
    }

    @SimpleEvent(description = "Le serveur a répondu à la vérification du nom. available : vrai si le nom est libre, faux s'il est déjà utilisé.")
    public void OnShopNameChecked(String name, boolean available) {
        EventDispatcher.dispatchEvent(this, "OnShopNameChecked", name, available);
    }

    @SimpleEvent(description = "Le formulaire est invalide. fieldKey : name ou category. message : le texte de l'erreur (aussi affiché en rouge à l'écran).")
    public void OnShopFormInvalid(String fieldKey, String message) {
        EventDispatcher.dispatchEvent(this, "OnShopFormInvalid", fieldKey, message);
    }

    @SimpleEvent(description = "Le logo a été choisi et compressé. logoPath : lien file:// de l'image.")
    public void OnShopLogoPicked(String logoPath) {
        EventDispatcher.dispatchEvent(this, "OnShopLogoPicked", logoPath);
    }

    @SimpleEvent(description = "Le serveur a accepté la création de la boutique (l'utilisateur est maintenant vendeur). responseCode : code HTTP, response : texte renvoyé.")
    public void OnShopCreated(int responseCode, String response) {
        EventDispatcher.dispatchEvent(this, "OnShopCreated", responseCode, response);
    }

    @SimpleEvent(description = "Le serveur a refusé la création de la boutique (ou erreur serveur). Le code 409 signale un nom déjà pris.")
    public void OnShopCreateFailed(int responseCode, String response) {
        EventDispatcher.dispatchEvent(this, "OnShopCreateFailed", responseCode, response);
    }

    @SimpleEvent(description = "Le serveur a répondu sur le statut vendeur. isSeller : vrai si l'utilisateur a déjà une boutique.")
    public void OnSellerStatusChecked(boolean isSeller) {
        EventDispatcher.dispatchEvent(this, "OnSellerStatusChecked", isSeller);
    }

    @SimpleEvent(description = "Le statut vendeur vient de changer (faux vers vrai à la création ou à la reconnexion). À utiliser pour masquer la page de création et l'onglet de la barre de navigation.")
    public void OnSellerStatusChanged(boolean isSeller) {
        EventDispatcher.dispatchEvent(this, "OnSellerStatusChanged", isSeller);
    }

    @SimpleEvent(description = "Une erreur s'est produite (code, réseau, permission, serveur).")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONSTRUCTION DU FORMULAIRE
    // =========================================================================

    @SimpleFunction(description = "Construit le formulaire « Créer ma boutique » dans l'arrangement donné (utilise un arrangement vertical dédié : son contenu est remplacé). Si l'utilisateur est déjà vendeur, rien n'est affiché et OnSellerStatusChecked(vrai) est déclenché.")
    public void BuildShopForm(final AndroidViewComponent container) {
        if (container == null || container.getView() == null) {
            OnError("BuildShopForm: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup target = realLayout(container);
                    if (target == null) {
                        OnError("BuildShopForm: conteneur invalide.");
                        return;
                    }
                    formContainer = target;
                    if (IsSeller()) {
                        OnSellerStatusChecked(true);
                        return;
                    }
                    render();
                } catch (Exception e) {
                    OnError("BuildShopForm: " + e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "Charge la police du texte du formulaire (ex: Manrope-Medium.ttf placé dans les Assets). Vide = police par défaut.")
    public void LoadCustomFont(String fontPath) {
        customFont = loadFontNamed(fontPath, "LoadCustomFont");
        if (formContainer != null) render();
    }

    @SimpleFunction(description = "Charge (facultatif) la police en gras du formulaire.")
    public void LoadCustomBoldFont(String fontPath) {
        customBoldFont = loadFontNamed(fontPath, "LoadCustomBoldFont");
        if (formContainer != null) render();
    }

    @SimpleFunction(description = "Définit la liste des catégories du spinner : un tableau JSON (« [\"Mode\",\"Beauté\"] » ou objets avec name / title) ou des noms séparés par des virgules.")
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

    @SimpleFunction(description = "Définit l'adresse du serveur qui vérifie le nom en direct. La requête GET est envoyée à chaque pause de frappe avec ?name=... (ou à la place de {name} dans l'adresse). Réponse attendue en JSON : {\"available\": true} ou {\"exists\": true}. authorization : en-tête Authorization (vide si inutile).")
    public void SetShopNameCheckUrl(String url, String authorization) {
        nameCheckUrl = url == null ? "" : url.trim();
        nameCheckAuth = authorization == null ? "" : authorization.trim();
        scheduleNameCheck();
    }

    private void render() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (formContainer == null) return;
                    formContainer.removeAllViews();
                    addTitle();
                    addNameSection();
                    addSeparator();
                    addCategorySection();
                    addSeparator();
                    addPhoneSection();
                    addSeparator();
                    addAddressSection();
                    addLogoSection();
                } catch (Exception e) {
                    OnError("render: " + e.getMessage());
                }
            }
        });
    }

    // ---- éléments de base ----

    private LinearLayout section() {
        LinearLayout s = new LinearLayout(context);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        s.setPadding(dp(20), dp(16), dp(20), dp(16));
        return s;
    }

    private TextView desc(String s) {
        TextView t = text(s, 12, cTitle(), false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        p.leftMargin = dp(2);
        t.setLayoutParams(p);
        return t;
    }

    private void addSeparator() {
        View v = new View(context);
        v.setBackgroundColor(Color.parseColor("#F5F5F5"));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        formContainer.addView(v);
    }

    private void addTitle() {
        TextView t = text("Configure ta boutique pour commencer à vendre", 25, cTitle(), false);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        t.setPadding(dp(20), dp(20), dp(20), dp(12));
        formContainer.addView(t);
    }

    // ---- champ à libellé animé (nom, téléphone, adresse, spinner) ----

    private class FieldView {
        final FrameLayout box;
        final TextView label;
        final GradientDrawable bg;
        EditText edit;
        boolean floated = false;
        boolean focused = false;
        boolean error = false;

        FieldView(String hint) {
            box = new FrameLayout(context);
            box.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(FIELD_HEIGHT_DP)));
            bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
            bg.setCornerRadius(dp(24));
            box.setBackground(bg);

            label = text(hint, 15, cHint(), false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, dp(22),
                    Gravity.CENTER_VERTICAL | Gravity.START);
            lp.leftMargin = dp(20);
            label.setLayoutParams(lp);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setSingleLine(true);
            label.setClickable(false);
            label.setPivotX(0f);
            label.setPivotY((float) dp(11));
            updateStroke();
        }

        // À appeler en dernier : le libellé passe au-dessus du contenu
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
            float ty = f ? -(float) dp(17) : 0f;
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
    }

    private FieldView buildEditField(final String key, String hint, int inputType,
                                     int maxLen, int imeAction, String initial) {
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
        e.setPadding(dp(20), dp(24), dp(20), dp(6));
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
                onFieldChanged(key, s);
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

    private void onFieldChanged(String key, String value) {
        if ("name".equals(key)) {
            shopName = value;
            nameRequiredShown = false;
            scheduleNameCheck();
        } else if ("phone".equals(key)) {
            shopPhone = value;
        } else if ("address".equals(key)) {
            shopAddress = value;
        }
    }

    // ---- sections ----

    private void addNameSection() {
        LinearLayout sec = section();
        nameField = buildEditField("name", "Nom de la boutique",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                MAX_NAME, EditorInfo.IME_ACTION_NEXT, shopName);
        sec.addView(nameField.box);
        sec.addView(desc("Le nom de votre boutique est le premier point de contact avec vos futurs clients"));

        nameStatus = text("", 12, COLOR_ERROR, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        p.leftMargin = dp(2);
        nameStatus.setLayoutParams(p);
        nameStatus.setVisibility(View.GONE);
        sec.addView(nameStatus);
        showNameStatus();
        formContainer.addView(sec);
    }

    private void addPhoneSection() {
        LinearLayout sec = section();
        phoneField = buildEditField("phone", "Téléphone du magasin",
                InputType.TYPE_CLASS_PHONE,
                MAX_PHONE, EditorInfo.IME_ACTION_NEXT, shopPhone);
        sec.addView(phoneField.box);
        sec.addView(desc("Indiquez le numéro de téléphone officiel de votre établissement. Ce contact permet à vos clients de vous joindre facilement."));
        formContainer.addView(sec);
    }

    private void addAddressSection() {
        LinearLayout sec = section();
        addressField = buildEditField("address", "Adresse du magasin",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                MAX_ADDRESS, EditorInfo.IME_ACTION_DONE, shopAddress);
        sec.addView(addressField.box);
        sec.addView(desc("Indiquez l'emplacement physique exact de votre commerce."));
        formContainer.addView(sec);
    }

    // ---- spinner de catégories ----

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

    private void addCategorySection() {
        LinearLayout sec = section();
        catField = new FieldView("Catégorie");

        catValue = text(shopCategory, 15, cText(), false);
        catValue.setSingleLine(true);
        catValue.setEllipsize(TextUtils.TruncateAt.END);
        catValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        catValue.setPadding(dp(20), dp(24), dp(48), dp(6));
        catField.box.addView(catValue, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(
                dp(14), dp(8), Gravity.END | Gravity.CENTER_VERTICAL);
        ap.rightMargin = dp(20);
        catField.box.addView(new ArrowView(context), ap);

        catField.attachLabel();
        catField.setFloated(!shopCategory.isEmpty(), false);
        catField.box.setClickable(true);
        catField.box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCategoryPopup();
            }
        });

        sec.addView(catField.box);
        sec.addView(desc("Sélectionnez le secteur d'activité qui correspond le mieux à vos produits"));

        catError = text("", 12, COLOR_ERROR, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        p.leftMargin = dp(2);
        catError.setLayoutParams(p);
        catError.setVisibility(View.GONE);
        sec.addView(catError);
        formContainer.addView(sec);
    }

    private void clearEditFocus() {
        if (nameField != null && nameField.edit != null) nameField.edit.clearFocus();
        if (phoneField != null && phoneField.edit != null) phoneField.edit.clearFocus();
        if (addressField != null && addressField.edit != null) addressField.edit.clearFocus();
    }

    private void openCategoryPopup() {
        if (categories.isEmpty()) {
            OnError("Aucune catégorie : appelle SetShopCategories avant d'ouvrir le spinner.");
            return;
        }
        hideKeyboard();
        clearEditFocus();

        final ListPopupWindow pop = new ListPopupWindow(context);
        pop.setAnchorView(catField.box);
        pop.setModal(true);
        pop.setWidth(catField.box.getWidth());
        pop.setHeight(categories.size() <= 5
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : dp(5 * 48 + 8));
        pop.setVerticalOffset(dp(4));

        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Color.WHITE);
        pbg.setCornerRadius(dp(16));
        pbg.setStroke(dp(1), cStroke());
        pop.setBackgroundDrawable(pbg);

        pop.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return categories.size();
            }

            @Override
            public Object getItem(int position) {
                return categories.get(position);
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
                    tv.setPadding(dp(20), 0, dp(20), 0);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setLayoutParams(new AbsListView.LayoutParams(
                            AbsListView.LayoutParams.MATCH_PARENT, dp(48)));
                }
                String item = categories.get(position);
                tv.setText(item);
                tv.setBackgroundColor(item.equals(shopCategory)
                        ? Color.parseColor("#F2F2F2") : Color.TRANSPARENT);
                return tv;
            }
        });

        pop.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectCategory(categories.get(position), true);
                pop.dismiss();
            }
        });

        pop.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                catField.focused = false;
                catField.updateStroke();
                if (shopCategory.isEmpty()) catField.setFloated(false, true);
            }
        });

        catField.focused = true;
        catField.updateStroke();
        catField.setFloated(true, true);
        pop.show();
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

    // ---- logo ----

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

    private void addLogoSection() {
        LinearLayout sec = section();
        sec.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout outer = new FrameLayout(context);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(158), dp(158));
        op.gravity = Gravity.CENTER_HORIZONTAL;
        op.topMargin = dp(20);
        op.bottomMargin = dp(20);
        outer.setLayoutParams(op);
        GradientDrawable obg = new GradientDrawable();
        obg.setColor(Color.WHITE);
        obg.setCornerRadius(dp(30));
        outer.setBackground(obg);
        outer.setElevation((float) dp(6));
        outer.setRotation(-4f);

        final int innerRadius = dp(26);
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
        outer.addView(inner, new FrameLayout.LayoutParams(dp(134), dp(134), Gravity.CENTER));

        logoGlyph = new PhotoGlyph(context);
        inner.addView(logoGlyph, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

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
        sec.addView(desc("Ajoutez votre logo pour finaliser l'apparence de votre magasin sur l'application"));
        formContainer.addView(sec);
        updateLogoView();
    }

    private void updateLogoView() {
        if (logoImage == null || logoGlyph == null) return;
        if (logoPath.isEmpty()) {
            logoImage.setImageDrawable(null);
            logoImage.setVisibility(View.GONE);
            logoGlyph.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap b = decodeSampled(stripFile(logoPath), 400);
        if (b != null) {
            logoImage.setImageBitmap(b);
            logoImage.setVisibility(View.VISIBLE);
            logoGlyph.setVisibility(View.GONE);
        }
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

    // =========================================================================
    // SÉLECTION DU LOGO (permission + galerie comme ManaplaceUtils,
    // copie dans le cache comme Manatest, puis compression)
    // =========================================================================

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

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        if (requestCode != pickRequestCode) return;
        if (resultCode != Activity.RESULT_OK || data == null) return; // annulation : pas une erreur
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
                        updateLogoView();
                        OnShopLogoPicked(result);
                    }
                });
            }
        }).start();
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

    // =========================================================================
    // VÉRIFICATION DU NOM EN DIRECT
    // =========================================================================

    private void scheduleNameCheck() {
        if (nameRunnable != null) handler.removeCallbacks(nameRunnable);
        nameSeq++;
        nameState = 0;
        nameChecking = false;
        showNameStatus();

        final String n = shopName == null ? "" : shopName.trim();
        if (n.isEmpty() || nameCheckUrl.isEmpty()) return;

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
        if (nameCheckUrl.contains("{name}")) {
            return nameCheckUrl.replace("{name}", enc);
        }
        return nameCheckUrl + (nameCheckUrl.contains("?") ? "&" : "?") + "name=" + enc;
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

    private void runNameCheck(final String name, final int seq) {
        final String url = nameCheckUrl;
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

    // =========================================================================
    // LECTURE DES SAISIES
    // =========================================================================

    @SimpleFunction(description = "Retourne la catégorie choisie dans le spinner (vide si aucune).")
    public String GetSelectedCategory() {
        return shopCategory == null ? "" : shopCategory;
    }

    @SimpleFunction(description = "Retourne le nom de la boutique saisi.")
    public String GetShopName() {
        return shopName == null ? "" : shopName.trim();
    }

    @SimpleFunction(description = "Retourne le téléphone du magasin saisi.")
    public String GetShopPhone() {
        return shopPhone == null ? "" : shopPhone.trim();
    }

    @SimpleFunction(description = "Retourne l'adresse du magasin saisie.")
    public String GetShopAddress() {
        return shopAddress == null ? "" : shopAddress.trim();
    }

    @SimpleFunction(description = "Retourne le lien file:// du logo choisi (vide si aucun).")
    public String GetShopLogo() {
        return logoPath == null ? "" : logoPath;
    }

    @SimpleFunction(description = "Retourne tout le formulaire en JSON : name, category, phone, address, logo, uid, formValid.")
    public String GetShopFormJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", GetShopName());
            o.put("category", GetSelectedCategory());
            o.put("phone", GetShopPhone());
            o.put("address", GetShopAddress());
            o.put("logo", GetShopLogo());
            o.put("uid", prefs().getString(PREF_CURRENT_UID, ""));
            o.put("formValid", firstError() == null);
            return o.toString();
        } catch (Exception e) {
            OnError("GetShopFormJson: " + e.getMessage());
            return "{}";
        }
    }

    // =========================================================================
    // VALIDATION (sécurité : nom et catégorie uniquement)
    // =========================================================================

    // Retourne {clé, message} ou null si le formulaire est valide
    private String[] firstError() {
        if (shopName == null || shopName.trim().isEmpty()) {
            return new String[]{"name", "Le nom de la boutique est obligatoire"};
        }
        if (nameState == 2) {
            return new String[]{"name", "Ce nom est déjà utilisé"};
        }
        if (nameChecking) {
            return new String[]{"name", "Vérification du nom en cours, patiente un instant"};
        }
        if (shopCategory == null || shopCategory.trim().isEmpty()) {
            return new String[]{"category", "Choisis une catégorie"};
        }
        return null;
    }

    @SimpleFunction(description = "Vérifie le formulaire. Retourne vrai si le nom (obligatoire, libre) et la catégorie (obligatoire) sont valides. Sinon retourne faux, affiche l'erreur en rouge sous le champ et déclenche OnShopFormInvalid. À mettre directement dans un « si ».")
    public boolean ValidateShopForm() {
        final String[] err = firstError();
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

    @SimpleFunction(description = "Retourne le premier message d'erreur du formulaire, ou \"\" si tout est valide (sans rien afficher).")
    public String GetShopFormError() {
        String[] err = firstError();
        return err == null ? "" : err[1];
    }

    // =========================================================================
    // ENVOI AU SERVEUR (multipart/form-data)
    // =========================================================================

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
            return true; // 2xx sans JSON : succès
        }
    }

    @SimpleFunction(description = "Envoie la création de boutique au serveur (POST multipart). Champs : uid, name, category, phone, address, formValid=true, et le fichier logo. authorization : en-tête Authorization (ex : Bearer <jeton Firebase>). uid : l'UID Firebase de l'utilisateur, réutilisé par le serveur pour créer le nœud vendeur. Le formulaire est vérifié avant l'envoi. Réponse : OnShopCreated (succès, l'utilisateur devient vendeur pour toujours) ou OnShopCreateFailed.")
    public void SubmitShopForm(final String url, final String authorization, final String uid) {
        if (url == null || url.trim().isEmpty()) {
            OnError("SubmitShopForm: adresse du serveur vide.");
            return;
        }
        if (uid == null || uid.trim().isEmpty()) {
            OnError("SubmitShopForm: uid vide.");
            return;
        }
        if (submitting) return;
        if (!ValidateShopForm()) return;

        submitting = true;
        final String cleanUid = uid.trim();
        prefs().edit().putString(PREF_CURRENT_UID, cleanUid).apply();

        final Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("uid", cleanUid);
        fields.put("name", GetShopName());
        fields.put("category", GetSelectedCategory());
        fields.put("phone", GetShopPhone());
        fields.put("address", GetShopAddress());
        fields.put("formValid", "true");
        final String logo = stripFile(GetShopLogo());
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
                                setSeller(cleanUid, true);
                                OnShopCreated(code, body);
                            } else {
                                if (code == 409) {
                                    nameState = 2;
                                    showNameStatus();
                                }
                                OnShopCreateFailed(code, body);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            submitting = false;
                            OnError("SubmitShopForm: " + e.getMessage());
                        }
                    });
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    // =========================================================================
    // STATUT VENDEUR PERMANENT
    // =========================================================================

    private boolean readSeller(String uid) {
        return uid != null && !uid.isEmpty() && prefs().getBoolean(PREF_SELLER_PREFIX + uid, false);
    }

    // Enregistre le statut ; déclenche OnSellerStatusChanged seulement s'il change
    private void setSeller(String uid, boolean value) {
        if (uid == null || uid.isEmpty()) return;
        boolean old = readSeller(uid);
        SharedPreferences.Editor ed = prefs().edit();
        if (value) {
            ed.putBoolean(PREF_SELLER_PREFIX + uid, true);
        } else {
            ed.remove(PREF_SELLER_PREFIX + uid);
        }
        ed.apply();
        if (old != value) OnSellerStatusChanged(value);
    }

    private Boolean sellerFromJson(JSONObject o) {
        String[] keys = {"seller", "isSeller", "is_seller", "hasShop", "has_shop", "shop"};
        for (int i = 0; i < keys.length; i++) {
            if (o.has(keys[i])) {
                Object v = o.opt(keys[i]);
                if (v instanceof Boolean) return (Boolean) v;
                if (v == null || v == JSONObject.NULL) return Boolean.FALSE;
                if (v instanceof Number) return Boolean.valueOf(((Number) v).intValue() != 0);
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    return Boolean.valueOf("true".equalsIgnoreCase(s) || "1".equals(s)
                            || "seller".equalsIgnoreCase(s) || "vendeur".equalsIgnoreCase(s));
                }
                return Boolean.TRUE; // objet « shop » présent
            }
        }
        if (o.has("role")) {
            String r = o.optString("role", "");
            return Boolean.valueOf("seller".equalsIgnoreCase(r) || "vendeur".equalsIgnoreCase(r));
        }
        JSONObject data = o.optJSONObject("data");
        if (data != null) return sellerFromJson(data);
        return null;
    }

    // Appel réseau synchrone (à lancer hors du fil principal). null = réponse inexploitable
    private Boolean fetchSeller(String url, String auth, String uid) throws Exception {
        String enc = URLEncoder.encode(uid, "UTF-8");
        String target = url.contains("{uid}")
                ? url.replace("{uid}", enc)
                : url + (url.contains("?") ? "&" : "?") + "uid=" + enc;
        String[] r = httpGet(target, auth);
        int code = Integer.parseInt(r[0]);
        if (code >= 400) throw new Exception("serveur " + code);
        return sellerFromJson(new JSONObject(r[1].trim()));
    }

    private void applySellerResult(String uid, boolean isSeller) {
        setSeller(uid, isSeller);
        OnSellerStatusChecked(isSeller);
    }

    @SimpleFunction(description = "Mémorise l'utilisateur connecté (UID Firebase). À appeler à la connexion : IsSeller répond tout de suite avec le statut déjà connu sur ce téléphone.")
    public void SetShopUser(String uid) {
        prefs().edit().putString(PREF_CURRENT_UID, uid == null ? "" : uid.trim()).apply();
    }

    @SimpleFunction(description = "Retourne vrai si l'utilisateur connecté est déjà vendeur (statut gardé dans le téléphone et confirmé par le serveur).")
    public boolean IsSeller() {
        return readSeller(prefs().getString(PREF_CURRENT_UID, ""));
    }

    @SimpleFunction(description = "Retourne vrai si la page « Créer ma boutique » doit encore être affichée (faux dès que l'utilisateur est vendeur).")
    public boolean ShouldShowShopCreation() {
        return !IsSeller();
    }

    @SimpleFunction(description = "Demande une fois au serveur si l'utilisateur est vendeur (utile à la reconnexion, même après effacement des données du téléphone). GET url?uid=... (ou {uid} dans l'adresse). Réponse JSON attendue : {\"seller\": true} (ou isSeller, hasShop). Résultat dans OnSellerStatusChecked ; OnSellerStatusChanged si le statut change.")
    public void CheckSellerStatus(final String url, final String authorization, final String uid) {
        if (url == null || url.trim().isEmpty() || uid == null || uid.trim().isEmpty()) {
            OnError("CheckSellerStatus: adresse ou uid vide.");
            return;
        }
        final String cleanUid = uid.trim();
        final String auth = authorization == null ? "" : authorization.trim();
        SetShopUser(cleanUid);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Boolean r = fetchSeller(url.trim(), auth, cleanUid);
                    if (r == null) {
                        fail("CheckSellerStatus: réponse inattendue (seller / isSeller / hasShop manquant).");
                        return;
                    }
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            applySellerResult(cleanUid, r.booleanValue());
                        }
                    });
                } catch (Exception e) {
                    fail("CheckSellerStatus: " + e.getMessage());
                }
            }
        }).start();
    }

    @SimpleFunction(description = "Écoute le serveur en continu : vérifie le statut vendeur tout de suite puis toutes les intervalSeconds secondes (minimum 5) et déclenche OnSellerStatusChanged dès que l'utilisateur devient vendeur. L'écoute s'arrête d'elle-même une fois vendeur.")
    public void StartSellerStatusListener(final String url, final String authorization,
                                          final String uid, int intervalSeconds) {
        if (url == null || url.trim().isEmpty() || uid == null || uid.trim().isEmpty()) {
            OnError("StartSellerStatusListener: adresse ou uid vide.");
            return;
        }
        StopSellerStatusListener();
        final String cleanUid = uid.trim();
        final String cleanUrl = url.trim();
        final String auth = authorization == null ? "" : authorization.trim();
        final long every = Math.max(5, intervalSeconds) * 1000L;
        SetShopUser(cleanUid);
        sellerListening = true;

        sellerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean reported = false;
                while (sellerListening) {
                    try {
                        final Boolean r = fetchSeller(cleanUrl, auth, cleanUid);
                        if (r != null && sellerListening) {
                            runOnUi(new Runnable() {
                                @Override
                                public void run() {
                                    applySellerResult(cleanUid, r.booleanValue());
                                }
                            });
                            if (r.booleanValue()) {
                                sellerListening = false;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        if (!reported) {
                            reported = true;
                            fail("StartSellerStatusListener: " + e.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(every);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        });
        sellerThread.setDaemon(true);
        sellerThread.start();
    }

    @SimpleFunction(description = "Arrête l'écoute du statut vendeur.")
    public void StopSellerStatusListener() {
        sellerListening = false;
        if (sellerThread != null) {
            sellerThread.interrupt();
            sellerThread = null;
        }
    }

    @SimpleFunction(description = "À appeler UNIQUEMENT après la suppression totale du compte : efface le statut vendeur de l'utilisateur courant et arrête l'écoute. La page de création peut alors réapparaître.")
    public void ClearSellerStatus() {
        StopSellerStatusListener();
        setSeller(prefs().getString(PREF_CURRENT_UID, ""), false);
    }

    @SimpleFunction(description = "Vide tous les champs, la catégorie et le logo du formulaire.")
    public void ClearShopForm() {
        shopName = "";
        shopCategory = "";
        shopPhone = "";
        shopAddress = "";
        logoPath = "";
        nameState = 0;
        nameChecking = false;
        nameRequiredShown = false;
        nameSeq++;
        if (formContainer != null && !IsSeller()) render();
    }
}
