package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
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
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@DesignerComponent(
        version = 11,
        description = "Manatest - Page boutique unique pour le vendeur et les visiteurs : header avec le logo, barre de statut transparente, bouton Ajouter un produit (vendeur), compteur d'articles et menu hamburger. Le header se place dans l'arrangement scrollable, au-dessus de la grille, et défile avec elle.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class Manatest extends AndroidNonvisibleComponent {

    // Mêmes préférences que ManaplaceUtils / création de boutique
    private static final String PREFS_NAME = "ManaplaceShop";
    private static final String PREF_CURRENT_UID = "current_uid";
    private static final String HEADER_TAG = "manatest_header";

    private final Context context;
    private final Activity activity;

    private Typeface customFont;

    // Données de la boutique affichée
    private String shopUidValue = "";
    private boolean ownerFlag = false;
    private String shopNameValue = "";
    private String shopCategoryValue = "";
    private int articleCount = 0;
    private Bitmap logoBitmap;

    // Couleurs déduites du logo (calculées sur fond blanc)
    private int topColor = Color.WHITE;
    private double topLum = 1.0;
    private double contentLum = 1.0;
    private int onColor = Color.parseColor("#1A1A1B");

    // Dimensions
    private int screenH;
    private int statusBarH;
    private int logoHeightPx;
    private int extraTop = 0;       // partie du logo qui passe sous la barre de statut
    private boolean edgeMode = false; // vrai : le contenu démarre sous la barre (transparente)

    // Vues
    private LinearLayout headerHolder;
    private ScrollView scrollView;
    private FrameLayout logoFrame;
    private LinearLayout.LayoutParams logoFrameParams;
    private FrameLayout contentLayer;
    private ImageView logoImage;
    private HamburgerView hamburger;
    private TextView nameTv;
    private TextView categoryTv;
    private TextView countTv;
    private View statusScrim;
    private View decorBackdrop;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;

    // Barre de statut d'origine
    private boolean barsSaved = false;
    private int origStatusColor;
    private int origSysUiFlags;

    // Menu
    private FrameLayout menuOverlay;
    private View menuPanel;
    private int menuPanelWidth;
    private boolean menuOpen = false;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = (Activity) container.$context();
    }

    // =========================================================================
    // OUTILS
    // =========================================================================

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics());
    }

    private int ink(int alpha) {
        return Color.argb(alpha, 0x1A, 0x1A, 0x1B);
    }

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

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (customFont != null) t.setTypeface(customFont);
        return t;
    }

    private ScrollView findScrollView(View v, int depth) {
        if (v instanceof ScrollView) return (ScrollView) v;
        if (depth > 3 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            ScrollView s = findScrollView(g.getChildAt(i), depth + 1);
            if (s != null) return s;
        }
        return null;
    }

    private double luminance(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    private float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private int blend(int a, int b, float p) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * p),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * p),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * p));
    }

    private int statusBarHeight() {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(24);
    }

    private boolean isOwner(String uid) {
        String me = prefs().getString(PREF_CURRENT_UID, "");
        return uid != null && !uid.isEmpty() && uid.equals(me);
    }

    // =========================================================================
    // ÉVÉNEMENTS
    // =========================================================================

    @SimpleEvent(description = "Le vendeur propriétaire a touché « Ajouter un produit ».")
    public void OnAddProductClick() {
        EventDispatcher.dispatchEvent(this, "OnAddProductClick");
    }

    @SimpleEvent(description = "Le vendeur propriétaire a touché « Édit » dans le menu. Ouvre ici la page d'édition de la boutique. shopUid : l'UID de la boutique.")
    public void OnEditShopClick(String shopUid) {
        EventDispatcher.dispatchEvent(this, "OnEditShopClick", shopUid);
    }

    @SimpleEvent(description = "Quelqu'un a touché « Contact » dans le menu. Ouvre ici la page des infos de la boutique. shopUid : l'UID de la boutique.")
    public void OnShopContactClick(String shopUid) {
        EventDispatcher.dispatchEvent(this, "OnShopContactClick", shopUid);
    }

    @SimpleEvent(description = "Le logo du header est chargé et affiché.")
    public void OnShopLogoLoaded() {
        EventDispatcher.dispatchEvent(this, "OnShopLogoLoaded");
    }

    @SimpleEvent(description = "Une erreur s'est produite (construction, logo, JSON). Les messages [diag] expliquent pourquoi la page ne s'affiche pas correctement.")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    private Typeface tryLoadTypeface(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try {
            if (name.startsWith("/")) return Typeface.createFromFile(new File(name));
        } catch (Exception ignored) {
        }
        try {
            return Typeface.createFromAsset(context.getAssets(), name);
        } catch (Exception ignored) {
        }
        try {
            File f = new File(Environment.getExternalStorageDirectory(), "AppInventor/assets/" + name);
            if (f.exists()) return Typeface.createFromFile(f);
        } catch (Exception ignored) {
        }
        return null;
    }

    // Manrope Medium est la police par défaut si le fichier est dans les Assets
    private void ensureDefaultFont() {
        if (customFont == null) customFont = tryLoadTypeface("Manrope-Medium.ttf");
    }

    @SimpleFunction(description = "Charge la police du texte (ex: Manrope-Medium.ttf dans les Assets). Si tu ne l'appelles pas, Manrope-Medium.ttf est utilisée automatiquement quand elle est dans les Assets. À appeler avant BuildShopHome.")
    public void LoadCustomFont(String fontPath) {
        if (fontPath == null || fontPath.trim().isEmpty()) {
            customFont = null;
            return;
        }
        Typeface t = tryLoadTypeface(fontPath.trim());
        if (t == null) {
            OnError("LoadCustomFont: police introuvable (" + fontPath + "). Mets le fichier dans les Assets.");
        } else {
            customFont = t;
        }
    }

    @SimpleFunction(description = "Mémorise l'utilisateur connecté (UID Firebase). À appeler une fois à la connexion : BuildShopHome s'en sert pour savoir si la boutique affichée est la sienne.")
    public void SetShopUser(String uid) {
        prefs().edit().putString(PREF_CURRENT_UID, uid == null ? "" : uid.trim()).apply();
    }

    @SimpleFunction(description = "Retourne vrai si la boutique affichée appartient à l'utilisateur connecté (SetShopUser).")
    public boolean IsShopOwner() {
        return isOwner(shopUidValue);
    }

    @SimpleFunction(description = "Définit le nom de la boutique (grand texte centré du header) et sa catégorie (texte centré en haut).")
    public void SetShopHeader(final String name, final String category) {
        shopNameValue = name == null ? "" : name.trim();
        shopCategoryValue = category == null ? "" : category.trim();
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if (nameTv != null) nameTv.setText(shopNameValue);
                if (categoryTv != null) categoryTv.setText(shopCategoryValue);
            }
        });
    }

    // =========================================================================
    // ICÔNES
    // =========================================================================

    private class HamburgerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HamburgerView(Context c) {
            super(c);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth((float) dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float s = paint.getStrokeWidth() / 2f;
            canvas.drawLine(s, s, w - s, s, paint);
            canvas.drawLine(s, h / 2f, w - s, h / 2f, paint);
            canvas.drawLine(s, h - s, w - s, h - s, paint);
        }
    }

    private class CloseView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseView(Context c) {
            super(c);
            paint.setColor(ink(0xFF));
            paint.setStrokeWidth((float) dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float s = paint.getStrokeWidth() / 2f;
            canvas.drawLine(s, s, w - s, h - s, paint);
            canvas.drawLine(w - s, s, s, h - s, paint);
        }
    }

    // =========================================================================
    // CONSTRUCTION DE LA PAGE
    // =========================================================================

    @SimpleFunction(description = "Construit la page boutique dans l'arrangement scrollable donné : le header (logo, nom, bouton, compteur) se place tout en haut, la grille de ManaplaceUtils (BuildProductGridFromJson, version corrigée) se place dessous, et tout défile ensemble. shopUid : l'UID de la boutique à afficher. Si c'est celui de l'utilisateur connecté (SetShopUser), la page est celle du vendeur : bouton « Ajouter un produit » et « Édit » dans le menu. Sinon c'est un visiteur : seulement « Contact ».")
    public void BuildShopHome(final AndroidViewComponent scrollContainer, final String shopUid) {
        if (scrollContainer == null || scrollContainer.getView() == null) {
            OnError("BuildShopHome: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    buildShopHomeInternal(scrollContainer, shopUid);
                } catch (Exception e) {
                    OnError("BuildShopHome: " + e.getMessage());
                }
            }
        });
    }

    private void buildShopHomeInternal(AndroidViewComponent container, String uid) {
        ensureDefaultFont();

        ScrollView sc = findScrollView(container.getView(), 0);
        if (sc == null) {
            OnError("BuildShopHome: le conteneur doit être un arrangement vertical scrollable.");
            return;
        }
        if (sc.getChildCount() == 0 || !(sc.getChildAt(0) instanceof LinearLayout)
                || ((LinearLayout) sc.getChildAt(0)).getOrientation() != LinearLayout.VERTICAL) {
            OnError("BuildShopHome: contenu de l'arrangement scrollable inattendu.");
            return;
        }
        LinearLayout inner = (LinearLayout) sc.getChildAt(0);

        detachScrollListener();
        destroyMenu();

        String me = prefs().getString(PREF_CURRENT_UID, "");
        shopUidValue = uid == null ? "" : uid.trim();
        if (shopUidValue.isEmpty()) shopUidValue = me; // pas d'uid donné : sa propre boutique
        ownerFlag = isOwner(shopUidValue);
        if (me.isEmpty()) {
            OnError("[diag] utilisateur inconnu : appelle SetShopUser avec un uid non vide avant BuildShopHome. "
                    + "La page est en mode visiteur (le bouton Ajouter un produit et Édit sont masqués).");
        }

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        screenH = dm.heightPixels;
        int screenW = dm.widthPixels;
        statusBarH = statusBarHeight();
        logoHeightPx = (int) (screenH * 0.35);
        int btnH = (int) (screenH * 0.07);
        int btnW = (int) (screenW * 0.80);
        int cardH = (int) (screenH * 0.05);
        int cardW = (int) (screenW * 0.30);

        // Retire un ancien header avant d'en poser un nouveau
        for (int i = inner.getChildCount() - 1; i >= 0; i--) {
            if (HEADER_TAG.equals(inner.getChildAt(i).getTag())) inner.removeViewAt(i);
        }

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setTag(HEADER_TAG);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        holder.addView(buildLogoFrame());

        if (ownerFlag) {
            TextView btn = text("Ajouter un produit", 15, Color.WHITE);
            btn.setGravity(Gravity.CENTER);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(Color.argb(0xED, 0x1A, 0x1A, 0x1B));
            btnBg.setCornerRadius(dp(20));
            btn.setBackground(btnBg);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(btnW, btnH);
            bp.gravity = Gravity.CENTER_HORIZONTAL;
            bp.topMargin = dp(10);
            btn.setLayoutParams(bp);
            btn.setClickable(true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OnAddProductClick();
                }
            });
            holder.addView(btn);
        }

        countTv = text(countLabel(articleCount), 12, ink(0x7E));
        countTv.setGravity(Gravity.CENTER);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F5F5F5"));
        cardBg.setCornerRadius(dp(8));
        countTv.setBackground(cardBg);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cardW, cardH);
        cp.gravity = Gravity.END;
        cp.rightMargin = dp(15);
        cp.topMargin = ownerFlag ? dp(6) : dp(10);
        cp.bottomMargin = dp(10);
        countTv.setLayoutParams(cp);
        holder.addView(countTv);

        inner.addView(holder, 0);

        headerHolder = holder;
        scrollView = sc;

        saveSystemBars();
        applySystemBars();
        applyLogoToView();
        attachScrollListener();
        applyScroll();

        // Le Form peut réappliquer ses couleurs après Initialize, et la disposition finale
        // n'est connue qu'après le premier affichage : on détecte et on réapplique
        int[] delays = {150, 450, 1000};
        for (int i = 0; i < delays.length; i++) {
            holder.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applySystemBars();
                    detectEdge();
                }
            }, delays[i]);
        }
        holder.postDelayed(new Runnable() {
            @Override
            public void run() {
                diagnose();
            }
        }, 1500);
        holder.postDelayed(new Runnable() {
            @Override
            public void run() {
                diagnose();
            }
        }, 3500);
    }

    private void diagnose() {
        if (headerHolder != null && headerHolder.getParent() == null) {
            OnError("[diag] le header a été supprimé du conteneur : BuildProductGridFromJson vide encore tout le conteneur. Applique le patch ManaplaceUtils (grille dans son propre bloc).");
        }
    }

    private View buildLogoFrame() {
        logoFrame = new FrameLayout(context);
        logoFrameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, logoHeightPx + extraTop);
        logoFrame.setLayoutParams(logoFrameParams);
        logoFrame.setBackgroundColor(Color.WHITE);

        logoImage = new ImageView(context);
        logoImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoFrame.addView(logoImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Couche des textes : décalée de la hauteur de la barre de statut
        contentLayer = new FrameLayout(context);
        contentLayer.setPadding(0, extraTop, 0, 0);
        logoFrame.addView(contentLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        nameTv = text(shopNameValue, 35, onColor);
        nameTv.setGravity(Gravity.CENTER);
        nameTv.setPadding(dp(26), 0, dp(26), 0);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        contentLayer.addView(nameTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        categoryTv = text(shopCategoryValue, 15, onColor);
        categoryTv.setGravity(Gravity.CENTER);
        contentLayer.addView(categoryTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(32), Gravity.TOP));

        FrameLayout menuBtn = new FrameLayout(context);
        menuBtn.setClickable(true);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenShopMenu();
            }
        });
        hamburger = new HamburgerView(context);
        hamburger.setColor(onColor);
        menuBtn.addView(hamburger, new FrameLayout.LayoutParams(dp(21), dp(15), Gravity.CENTER));
        contentLayer.addView(menuBtn, new FrameLayout.LayoutParams(
                dp(40), dp(32), Gravity.TOP | Gravity.START));

        return logoFrame;
    }

    private void updateLogoExtra() {
        if (logoFrame != null && logoFrameParams != null) {
            logoFrameParams.height = logoHeightPx + extraTop;
            logoFrame.setLayoutParams(logoFrameParams);
        }
        if (contentLayer != null) contentLayer.setPadding(0, extraTop, 0, 0);
    }

    // =========================================================================
    // LOGO DU VENDEUR
    // =========================================================================

    @SimpleFunction(description = "Charge le logo du vendeur dans le header : lien http(s)://, chemin de fichier (file://...) ou nom d'un fichier des Assets. Les couleurs du texte, de l'icône menu et de la barre de statut s'adaptent au logo (blanc sur logo sombre, sombre sur logo clair).")
    public void SetShopLogoSource(final String source) {
        if (source == null || source.trim().isEmpty()) {
            OnError("SetShopLogoSource: source vide.");
            return;
        }
        final String src = source.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int maxW = context.getResources().getDisplayMetrics().widthPixels;
                    Bitmap bmp = loadBitmap(src, maxW);
                    if (bmp == null) {
                        fail("SetShopLogoSource: logo introuvable ou illisible (" + src
                                + "). Utilise un lien https, un fichier de l'appareil ou un fichier des Assets.");
                        return;
                    }
                    final Bitmap result = bmp;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            logoBitmap = result;
                            applyLogoToView();
                            applyScroll();
                            OnShopLogoLoaded();
                        }
                    });
                } catch (Exception e) {
                    fail("SetShopLogoSource: " + e.getMessage());
                }
            }
        }).start();
    }

    private Bitmap loadBitmap(String src, int maxW) throws Exception {
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return downloadBitmap(src, maxW);
        }
        if (src.startsWith("file://")) {
            return decodeFile(src.substring(7), maxW);
        }
        if (src.startsWith("/")) {
            return decodeFile(src, maxW);
        }
        // Nom de fichier simple : Assets de l'application (ex: world-flag.png)
        try {
            InputStream is = context.getAssets().open(src);
            return decodeBytes(readAllBytes(is), maxW);
        } catch (Exception ignored) {
        }
        // Companion : dossier des Assets sur le téléphone
        File f = new File(Environment.getExternalStorageDirectory(), "AppInventor/assets/" + src);
        if (f.exists()) return decodeFile(f.getAbsolutePath(), maxW);
        return null;
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = is.read(buf)) > 0) {
            total += n;
            if (total > 12 * 1024 * 1024) throw new Exception("image trop lourde");
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    }

    private Bitmap decodeBytes(byte[] data, int maxW) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = sampleFor(o.outWidth, maxW);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private Bitmap decodeFile(String path, int maxW) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = sampleFor(o.outWidth, maxW);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, o);
    }

    private Bitmap downloadBitmap(String url, int maxW) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("serveur " + code);
            return decodeBytes(readAllBytes(c.getInputStream()), maxW);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private int sampleFor(int width, int maxW) {
        int s = 1;
        while (width / (s * 2) >= maxW) s *= 2;
        return s;
    }

    // ---- analyse des couleurs (les zones transparentes comptent comme du blanc) ----

    private int overWhite(int c) {
        int a = Color.alpha(c);
        return Color.rgb(
                (Color.red(c) * a + 255 * (255 - a)) / 255,
                (Color.green(c) * a + 255 * (255 - a)) / 255,
                (Color.blue(c) * a + 255 * (255 - a)) / 255);
    }

    private int avgOverWhite(Bitmap src, int w, int h) {
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int x = 0; x < small.getWidth(); x++) {
            for (int y = 0; y < small.getHeight(); y++) {
                int c = overWhite(small.getPixel(x, y));
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
            }
        }
        if (small != src) small.recycle();
        if (n == 0) return Color.WHITE;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    // Bande du haut telle qu'elle est affichée (center-crop)
    private int topStripColor(Bitmap bmp) {
        try {
            int bw = bmp.getWidth();
            int bh = bmp.getHeight();
            int screenW = context.getResources().getDisplayMetrics().widthPixels;
            float scale = Math.max((float) screenW / bw, (float) logoHeightPx / bh);
            float visH = logoHeightPx / scale;
            int y = (int) Math.max(0f, (bh - visH) / 2f);
            int stripH = Math.max(1, (int) (visH * 0.06f));
            stripH = Math.min(stripH, bh - y);
            Bitmap strip = Bitmap.createBitmap(bmp, 0, y, bw, stripH);
            int c = avgOverWhite(strip, 8, 2);
            if (strip != bmp) strip.recycle();
            return c;
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    private void applyLogoToView() {
        if (logoImage == null) return;
        if (logoBitmap == null) {
            topColor = Color.WHITE;
            topLum = 1.0;
            contentLum = 1.0;
        } else {
            logoImage.setImageBitmap(logoBitmap);
            contentLum = luminance(avgOverWhite(logoBitmap, 16, 16));
            topColor = topStripColor(logoBitmap);
            topLum = luminance(topColor);
        }
        // Texte blanc sur logo sombre, texte sombre sur logo clair ou blanc
        onColor = contentLum > 0.6 ? ink(0xFF) : Color.WHITE;
        if (nameTv != null) nameTv.setTextColor(onColor);
        if (categoryTv != null) categoryTv.setTextColor(onColor);
        if (hamburger != null) hamburger.setColor(onColor);
    }

    // =========================================================================
    // BARRE DE STATUT TRANSPARENTE
    // =========================================================================

    private void saveSystemBars() {
        if (barsSaved) return;
        try {
            Window w = activity.getWindow();
            origStatusColor = w.getStatusBarColor();
            origSysUiFlags = w.getDecorView().getSystemUiVisibility();
            barsSaved = true;
        } catch (Exception ignored) {
        }
    }

    private void applySystemBars() {
        try {
            Window w = activity.getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.setStatusBarColor(Color.TRANSPARENT);
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            int nf = f | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (nf != f) d.setSystemUiVisibility(nf);
        } catch (Exception ignored) {
        }
    }

    private void setStatusIcons(boolean dark) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            View d = activity.getWindow().getDecorView();
            int f = d.getSystemUiVisibility();
            int nf = dark ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                    : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            if (nf != f) d.setSystemUiVisibility(nf);
        } catch (Exception ignored) {
        }
    }

    private FrameLayout contentRoot() {
        View v = activity.findViewById(android.R.id.content);
        return v instanceof FrameLayout ? (FrameLayout) v : null;
    }

    private void ensureScrim() {
        if (statusScrim != null || statusBarH <= 0) return;
        FrameLayout root = contentRoot();
        if (root == null) return;
        statusScrim = new View(context);
        statusScrim.setBackgroundColor(Color.WHITE);
        statusScrim.setAlpha(0f);
        statusScrim.setElevation((float) dp(2));
        root.addView(statusScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, statusBarH, Gravity.TOP));
    }

    // Vue posée tout en haut de la fenêtre, derrière les icônes de statut
    private void ensureDecorBackdrop() {
        if (statusBarH <= 0) return;
        try {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            if (decorBackdrop != null && decorBackdrop.getParent() == decor) return;
            decorBackdrop = new View(context);
            decorBackdrop.setBackgroundColor(topColor);
            decorBackdrop.setClickable(false);
            decorBackdrop.setOutlineProvider(null);
            decorBackdrop.setElevation((float) dp(24));
            decor.addView(decorBackdrop, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, statusBarH, Gravity.TOP));
        } catch (Exception e) {
            decorBackdrop = null;
        }
    }

    private void removeDecorBackdrop() {
        if (decorBackdrop == null) return;
        ViewGroup p = (ViewGroup) decorBackdrop.getParent();
        if (p != null) p.removeView(decorBackdrop);
        decorBackdrop = null;
    }

    private void removeScrim() {
        if (statusScrim == null) return;
        ViewGroup p = (ViewGroup) statusScrim.getParent();
        if (p != null) p.removeView(statusScrim);
        statusScrim = null;
    }

    // Le contenu démarre-t-il sous la barre (transparente) ou en dessous d'elle ?
    private void detectEdge() {
        if (scrollView == null || headerHolder == null) return;
        int[] loc = new int[2];
        scrollView.getLocationOnScreen(loc);
        int top = loc[1];
        boolean newEdge;
        int newExtra;
        if (top < statusBarH - dp(2)) {
            newEdge = true;
            newExtra = Math.max(0, statusBarH - top);
        } else {
            newEdge = false;
            newExtra = 0;
        }
        if (newEdge != edgeMode || newExtra != extraTop) {
            edgeMode = newEdge;
            extraTop = newExtra;
            updateLogoExtra();
            if (edgeMode) {
                ensureScrim();
                removeDecorBackdrop();
            } else {
                removeScrim();
            }
        }
        applyScroll();
    }

    private void attachScrollListener() {
        if (scrollView == null) return;
        scrollListener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                applyScroll();
            }
        };
        scrollView.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
    }

    private void detachScrollListener() {
        try {
            if (scrollView != null && scrollListener != null
                    && scrollView.getViewTreeObserver().isAlive()) {
                scrollView.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
            }
        } catch (Exception ignored) {
        }
        scrollListener = null;
    }

    // Quand le logo quitte le haut de l'écran, la zone de la barre passe au blanc de la page
    private void applyScroll() {
        if (scrollView == null) return;
        int y = scrollView.getScrollY();
        float range = Math.max(2f * statusBarH, (float) dp(48));
        float p = clamp01((y - (logoHeightPx - range / 2f)) / range);

        if (edgeMode) {
            if (statusScrim != null) statusScrim.setAlpha(p);
        } else {
            // Le contenu démarre sous la barre : on peint la zone de la barre avec la couleur du logo
            ensureDecorBackdrop();
            if (decorBackdrop != null) {
                decorBackdrop.setBackgroundColor(blend(topColor, Color.WHITE, p));
            }
        }
        double bgLum = topLum * (1f - p) + p;
        setStatusIcons(bgLum > 0.6);
    }

    @SimpleFunction(description = "À appeler en quittant la page boutique : retire le header, ferme le menu et remet la barre de statut d'origine.")
    public void ReleaseShopHome() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                detachScrollListener();
                destroyMenu();
                removeScrim();
                removeDecorBackdrop();
                if (headerHolder != null && headerHolder.getParent() instanceof ViewGroup) {
                    ((ViewGroup) headerHolder.getParent()).removeView(headerHolder);
                }
                headerHolder = null;
                if (barsSaved) {
                    try {
                        Window w = activity.getWindow();
                        w.setStatusBarColor(origStatusColor);
                        w.getDecorView().setSystemUiVisibility(origSysUiFlags);
                    } catch (Exception ignored) {
                    }
                    barsSaved = false;
                }
                edgeMode = false;
                extraTop = 0;
            }
        });
    }

    // =========================================================================
    // COMPTEUR D'ARTICLES
    // =========================================================================

    private String countLabel(int n) {
        return n + (n > 1 ? " Articles" : " Article");
    }

    private void showCount(final int n) {
        articleCount = n;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if (countTv != null) countTv.setText(countLabel(n));
            }
        });
    }

    private int countProducts(String json) throws Exception {
        String t = json == null ? "" : json.trim();
        if (t.isEmpty()) return 0;
        if (t.startsWith("[")) return new JSONArray(t).length();
        JSONObject o = new JSONObject(t);
        String[] keys = {"products", "produits", "items", "articles", "data"};
        for (int i = 0; i < keys.length; i++) {
            Object v = o.opt(keys[i]);
            if (v instanceof JSONArray) return ((JSONArray) v).length();
            if (v instanceof JSONObject) return ((JSONObject) v).length();
        }
        return o.length();
    }

    @SimpleFunction(description = "Compte les produits du JSON de la grille (le même que BuildProductGridFromJson), affiche « N Article(s) » dans la petite carte et retourne N.")
    public int UpdateArticleCountFromJson(String jsonData) {
        try {
            int n = countProducts(jsonData);
            showCount(n);
            return n;
        } catch (Exception e) {
            OnError("UpdateArticleCountFromJson: JSON invalide (" + e.getMessage() + ")");
            return 0;
        }
    }

    @SimpleFunction(description = "Affiche directement un nombre d'articles dans la petite carte.")
    public void SetArticleCount(int count) {
        showCount(Math.max(0, count));
    }

    @SimpleFunction(description = "Retourne le nombre d'articles actuellement affiché.")
    public int GetArticleCount() {
        return articleCount;
    }

    // =========================================================================
    // MENU HAMBURGER
    // =========================================================================

    private TextView menuItem(String label, final Runnable action) {
        TextView t = text(label, 25, Color.BLACK);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(9), 0, dp(9), 0);
        t.setSingleLine(true);
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CloseShopMenu();
                action.run();
            }
        });
        return t;
    }

    private void ensureMenu() {
        if (menuOverlay != null) return;
        FrameLayout root = contentRoot();
        if (root == null) {
            OnError("Menu: racine de l'écran introuvable.");
            return;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        menuPanelWidth = (int) (dm.widthPixels * 0.54);

        menuOverlay = new FrameLayout(context);
        menuOverlay.setVisibility(View.GONE);
        menuOverlay.setElevation((float) dp(3));

        View catcher = new View(context);
        catcher.setClickable(true);
        catcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CloseShopMenu();
            }
        });
        menuOverlay.addView(catcher, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setClickable(true);

        FrameLayout closeBox = new FrameLayout(context);
        closeBox.setClickable(true);
        closeBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CloseShopMenu();
            }
        });
        closeBox.addView(new CloseView(context), new FrameLayout.LayoutParams(
                dp(20), dp(20), Gravity.CENTER));
        panel.addView(closeBox, new LinearLayout.LayoutParams(dp(38), dp(48)));

        boolean first = true;
        // « Édit » : seulement pour le vendeur propriétaire
        if (ownerFlag) {
            TextView edit = menuItem("Édit", new Runnable() {
                @Override
                public void run() {
                    OnEditShopClick(shopUidValue);
                }
            });
            ((LinearLayout.LayoutParams) edit.getLayoutParams()).topMargin = dp(6);
            panel.addView(edit);
            first = false;
        }

        // « Contact » : pour tout le monde
        TextView contact = menuItem("Contact", new Runnable() {
            @Override
            public void run() {
                OnShopContactClick(shopUidValue);
            }
        });
        if (first) ((LinearLayout.LayoutParams) contact.getLayoutParams()).topMargin = dp(6);
        panel.addView(contact);

        menuPanel = panel;
        menuOverlay.addView(panel, new FrameLayout.LayoutParams(
                menuPanelWidth, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));
        root.addView(menuOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void destroyMenu() {
        if (menuOverlay != null) {
            ViewGroup p = (ViewGroup) menuOverlay.getParent();
            if (p != null) p.removeView(menuOverlay);
        }
        menuOverlay = null;
        menuPanel = null;
        menuOpen = false;
    }

    @SimpleFunction(description = "Ouvre le menu latéral (Édit pour le vendeur, Contact pour tout le monde). Appelé aussi au toucher du menu hamburger.")
    public void OpenShopMenu() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureMenu();
                    if (menuOverlay == null || menuOpen) return;

                    // Barre de statut transparente : le menu démarre juste sous les icônes
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menuOverlay.getLayoutParams();
                    lp.topMargin = edgeMode ? statusBarH : 0;
                    menuOverlay.setLayoutParams(lp);

                    menuOpen = true;
                    menuOverlay.setVisibility(View.VISIBLE);
                    menuPanel.setTranslationX(-(float) menuPanelWidth);
                    menuPanel.animate()
                            .translationX(0f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } catch (Exception e) {
                    OnError("OpenShopMenu: " + e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "Ferme le menu latéral.")
    public void CloseShopMenu() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if (menuOverlay == null || menuPanel == null || !menuOpen) return;
                menuOpen = false;
                menuPanel.animate()
                        .translationX(-(float) menuPanelWidth)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (menuOverlay != null && !menuOpen) {
                                    menuOverlay.setVisibility(View.GONE);
                                }
                            }
                        })
                        .start();
            }
        });
    }
}
