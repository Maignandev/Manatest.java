package com.manatest.utils;

import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
        version = 10,
        description = "Manatest - Page d'accueil boutique : header avec le logo du vendeur, bouton Ajouter un produit, compteur d'articles, menu hamburger et barre de statut dynamique. Le header (arrangement non scrollable) défile avec la grille (arrangement scrollable).",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class Manatest extends AndroidNonvisibleComponent {

    private static final int PAGE_COLOR = Color.WHITE;
    private static final int DEFAULT_HEADER_COLOR = Color.parseColor("#1A1A1B");

    private final Context context;
    private final Activity activity;

    private Typeface customFont;

    // Données
    private String shopNameValue = "";
    private String shopCategoryValue = "";
    private int articleCount = 0;
    private Bitmap logoBitmap;
    private int headerColor = DEFAULT_HEADER_COLOR;

    // Dimensions (en pixels)
    private int screenW;
    private int screenH;
    private int logoHeightPx;
    private int headerTotalPx;

    // Vues
    private View headerRoot;
    private View scrollOuter;
    private ScrollView scrollView;
    private ImageView logoImage;
    private View scrimView;
    private TextView nameTv;
    private TextView categoryTv;
    private TextView countTv;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;

    // Ajustement du conteneur scrollable (fait une seule fois par vue)
    private View adjustedScrollView;
    private int origScrollHeight;

    // Barre de statut d'origine
    private boolean statusSaved = false;
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

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (customFont != null) t.setTypeface(customFont);
        return t;
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

    // =========================================================================
    // ÉVÉNEMENTS
    // =========================================================================

    @SimpleEvent(description = "L'utilisateur a touché le bouton « Ajouter un produit ».")
    public void OnAddProductClick() {
        EventDispatcher.dispatchEvent(this, "OnAddProductClick");
    }

    @SimpleEvent(description = "L'utilisateur a touché « Mettre à jour les infos » dans le menu. Ouvre ici la page correspondante.")
    public void OnUpdateInfosClick() {
        EventDispatcher.dispatchEvent(this, "OnUpdateInfosClick");
    }

    @SimpleEvent(description = "L'utilisateur a touché « Détails de la boutique » dans le menu. Ouvre ici la page correspondante.")
    public void OnShopDetailsClick() {
        EventDispatcher.dispatchEvent(this, "OnShopDetailsClick");
    }

    @SimpleEvent(description = "Le logo du header est chargé et affiché.")
    public void OnShopLogoLoaded() {
        EventDispatcher.dispatchEvent(this, "OnShopLogoLoaded");
    }

    @SimpleEvent(description = "Une erreur s'est produite (construction, logo, JSON).")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    @SimpleFunction(description = "Charge la police du texte (ex: Manrope-Medium.ttf dans les Assets). À appeler avant BuildShopHome.")
    public void LoadCustomFont(String fontPath) {
        if (fontPath == null || fontPath.trim().isEmpty()) {
            customFont = null;
            return;
        }
        try {
            if (fontPath.startsWith("/")) {
                customFont = Typeface.createFromFile(new File(fontPath));
            } else {
                customFont = Typeface.createFromAsset(context.getAssets(), fontPath);
            }
        } catch (Exception e) {
            OnError("LoadCustomFont: police introuvable (" + fontPath + ").");
        }
    }

    @SimpleFunction(description = "Définit le nom de la boutique (grand texte blanc du header) et sa catégorie (texte blanc centré en haut).")
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
    // CONSTRUCTION DE LA PAGE
    // =========================================================================

    private class HamburgerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HamburgerView(Context c) {
            super(c);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth((float) dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
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

    @SimpleFunction(description = "Construit la page boutique. headerContainer : arrangement NON scrollable, vide, placé juste au-dessus de scrollContainer dans le même arrangement vertical. scrollContainer : arrangement scrollable où ManaplaceUtils construit la grille (BuildProductGridFromJson). Le header et la grille défilent ensemble. Le contenu de headerContainer est remplacé.")
    public void BuildShopHome(final AndroidViewComponent headerContainer,
                              final AndroidViewComponent scrollContainer) {
        if (headerContainer == null || headerContainer.getView() == null
                || scrollContainer == null || scrollContainer.getView() == null) {
            OnError("BuildShopHome: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    buildShopHomeInternal(headerContainer, scrollContainer);
                } catch (Exception e) {
                    OnError("BuildShopHome: " + e.getMessage());
                }
            }
        });
    }

    private void buildShopHomeInternal(AndroidViewComponent headerContainer,
                                       AndroidViewComponent scrollContainer) {
        View hv = headerContainer.getView();
        View sv = scrollContainer.getView();
        ViewGroup content = realLayout(headerContainer);
        ScrollView sc = findScrollView(sv, 0);

        if (content == null) {
            OnError("BuildShopHome: headerContainer doit être un arrangement.");
            return;
        }
        if (sc == null) {
            OnError("BuildShopHome: scrollContainer doit être un arrangement vertical scrollable.");
            return;
        }
        ViewParent p = hv.getParent();
        if (!(p instanceof LinearLayout) || sv.getParent() != p
                || ((LinearLayout) p).getOrientation() != LinearLayout.VERTICAL) {
            OnError("BuildShopHome: place les deux arrangements l'un sous l'autre dans le même arrangement vertical.");
            return;
        }
        LinearLayout parent = (LinearLayout) p;
        if (parent.indexOfChild(hv) >= parent.indexOfChild(sv)) {
            OnError("BuildShopHome: le header doit être placé au-dessus de l'arrangement scrollable.");
            return;
        }

        detachScrollListener();

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        logoHeightPx = (int) (screenH * 0.35);
        int btnH = (int) (screenH * 0.08);
        int btnW = (int) (screenW * 0.90);
        int cardH = (int) (screenH * 0.05);
        int cardW = (int) (screenW * 0.30);
        headerTotalPx = logoHeightPx + dp(10) + btnH + dp(6) + cardH + dp(10);

        headerRoot = hv;
        scrollOuter = sv;
        scrollView = sc;

        // ---- contenu du header ----
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, headerTotalPx));

        wrapper.addView(buildLogoFrame());

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
        wrapper.addView(btn);

        countTv = text(countLabel(articleCount), 12, ink(0x7E));
        countTv.setGravity(Gravity.CENTER);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F5F5F5"));
        cardBg.setCornerRadius(dp(8));
        countTv.setBackground(cardBg);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cardW, cardH);
        cp.gravity = Gravity.END;
        cp.rightMargin = dp(15);
        cp.topMargin = dp(6);
        cp.bottomMargin = dp(10);
        countTv.setLayoutParams(cp);
        wrapper.addView(countTv);

        content.removeAllViews();
        content.addView(wrapper);

        ViewGroup.LayoutParams hl = hv.getLayoutParams();
        if (hl != null) {
            hl.height = headerTotalPx;
            hv.setLayoutParams(hl);
        }

        // ---- le header passe au-dessus de la grille et défile avec elle ----
        hv.setOutlineProvider(null);
        hv.setTranslationZ((float) dp(2));

        ViewGroup.LayoutParams sl = sv.getLayoutParams();
        if (!(sl instanceof ViewGroup.MarginLayoutParams)) {
            OnError("BuildShopHome: arrangement scrollable incompatible.");
            return;
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) sl;
        if (adjustedScrollView != sv) {
            adjustedScrollView = sv;
            origScrollHeight = mlp.height;
        }
        mlp.topMargin = -headerTotalPx;
        if (origScrollHeight > 0) mlp.height = origScrollHeight + headerTotalPx;
        sv.setLayoutParams(mlp);

        sc.setClipToPadding(false);
        sc.setPadding(sc.getPaddingLeft(), headerTotalPx, sc.getPaddingRight(), sc.getPaddingBottom());

        saveStatusBar();
        attachScrollListener();
        applyLogoToView();
        applyScroll();
    }

    private View buildLogoFrame() {
        FrameLayout frame = new FrameLayout(context);
        frame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, logoHeightPx));
        frame.setBackgroundColor(DEFAULT_HEADER_COLOR);

        logoImage = new ImageView(context);
        logoImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(logoImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scrimView = new View(context);
        scrimView.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(scrimView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        nameTv = text(shopNameValue, 35, Color.WHITE);
        nameTv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        nameTv.setPadding(dp(26), 0, dp(26), 0);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        frame.addView(nameTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        categoryTv = text(shopCategoryValue, 15, Color.WHITE);
        categoryTv.setGravity(Gravity.CENTER);
        frame.addView(categoryTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(32), Gravity.TOP));

        FrameLayout menuBtn = new FrameLayout(context);
        menuBtn.setClickable(true);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenShopMenu();
            }
        });
        menuBtn.addView(new HamburgerView(context), new FrameLayout.LayoutParams(
                dp(21), dp(15), Gravity.CENTER));
        frame.addView(menuBtn, new FrameLayout.LayoutParams(
                dp(40), dp(32), Gravity.TOP | Gravity.START));

        return frame;
    }

    // =========================================================================
    // LOGO DU VENDEUR
    // =========================================================================

    @SimpleFunction(description = "Charge le logo du vendeur dans le header : lien http(s):// ou chemin de fichier (file://...). La barre de statut prend automatiquement la couleur du haut du logo.")
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
                    Bitmap bmp;
                    if (src.startsWith("http://") || src.startsWith("https://")) {
                        bmp = downloadBitmap(src, maxW);
                    } else {
                        bmp = decodeFile(src.startsWith("file://") ? src.substring(7) : src, maxW);
                    }
                    if (bmp == null) {
                        fail("SetShopLogoSource: image illisible.");
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
            InputStream is = c.getInputStream();
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
            byte[] data = bos.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return null;
            o.inSampleSize = sampleFor(o.outWidth, maxW);
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private int sampleFor(int width, int maxW) {
        int s = 1;
        while (width / (s * 2) >= maxW) s *= 2;
        return s;
    }

    // Couleur moyenne de la bande du haut de l'image, telle qu'elle est affichée (center-crop)
    private int topColorOf(Bitmap bmp) {
        try {
            int bw = bmp.getWidth();
            int bh = bmp.getHeight();
            float scale = Math.max((float) screenW / bw, (float) logoHeightPx / bh);
            float visH = logoHeightPx / scale;
            int y = (int) Math.max(0f, (bh - visH) / 2f);
            int stripH = Math.max(1, (int) (visH * 0.06f));
            stripH = Math.min(stripH, bh - y);
            Bitmap strip = Bitmap.createBitmap(bmp, 0, y, bw, stripH);
            Bitmap small = Bitmap.createScaledBitmap(strip, 8, 2, true);
            long r = 0, g = 0, b = 0;
            int count = 0;
            for (int i = 0; i < small.getWidth(); i++) {
                for (int j = 0; j < small.getHeight(); j++) {
                    int c = small.getPixel(i, j);
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    count++;
                }
            }
            if (small != strip) small.recycle();
            if (strip != bmp) strip.recycle();
            if (count == 0) return DEFAULT_HEADER_COLOR;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (Exception e) {
            return DEFAULT_HEADER_COLOR;
        }
    }

    private void applyLogoToView() {
        if (logoImage == null) return;
        if (logoBitmap == null) {
            headerColor = DEFAULT_HEADER_COLOR;
            if (scrimView != null) scrimView.setBackgroundColor(Color.TRANSPARENT);
            return;
        }
        logoImage.setImageBitmap(logoBitmap);
        int avg = topColorOf(logoBitmap);
        // Logo très clair : léger voile sombre pour garder le texte blanc lisible
        int scrim = luminance(avg) > 0.6 ? 0x59 : 0;
        if (scrimView != null) scrimView.setBackgroundColor(Color.argb(scrim, 0, 0, 0));
        int k = 255 - scrim;
        headerColor = Color.rgb(
                Color.red(avg) * k / 255,
                Color.green(avg) * k / 255,
                Color.blue(avg) * k / 255);
    }

    // =========================================================================
    // DÉFILEMENT COMMUN ET BARRE DE STATUT DYNAMIQUE
    // =========================================================================

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

    private void applyScroll() {
        if (scrollView == null || headerRoot == null) return;
        int y = scrollView.getScrollY();
        int t = Math.min(Math.max(y, 0), headerTotalPx);
        headerRoot.setTranslationY(-(float) t);

        float p = logoHeightPx > 0 ? clamp01((float) y / (float) logoHeightPx) : 1f;
        int color = (Integer) new ArgbEvaluator().evaluate(p, headerColor, PAGE_COLOR);
        setStatusBar(color);
    }

    private void saveStatusBar() {
        if (statusSaved) return;
        try {
            Window w = activity.getWindow();
            origStatusColor = w.getStatusBarColor();
            origSysUiFlags = w.getDecorView().getSystemUiVisibility();
            statusSaved = true;
        } catch (Exception ignored) {
        }
    }

    private void setStatusBar(int color) {
        try {
            Window w = activity.getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(color);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View d = w.getDecorView();
                int f = d.getSystemUiVisibility();
                int nf = luminance(color) > 0.55
                        ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                        : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (nf != f) d.setSystemUiVisibility(nf);
            }
        } catch (Exception ignored) {
        }
    }

    @SimpleFunction(description = "À appeler en quittant la page boutique : arrête le défilement commun, ferme le menu et remet la barre de statut d'origine.")
    public void ReleaseShopHome() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                detachScrollListener();
                if (headerRoot != null) headerRoot.setTranslationY(0f);
                if (menuOverlay != null) {
                    ViewParent p = menuOverlay.getParent();
                    if (p instanceof ViewGroup) ((ViewGroup) p).removeView(menuOverlay);
                    menuOverlay = null;
                    menuPanel = null;
                    menuOpen = false;
                }
                if (statusSaved) {
                    try {
                        Window w = activity.getWindow();
                        w.setStatusBarColor(origStatusColor);
                        w.getDecorView().setSystemUiVisibility(origSysUiFlags);
                    } catch (Exception ignored) {
                    }
                }
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
        TextView t = text(label, 15, Color.BLACK);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(9), 0, dp(9), 0);
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45)));
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
        FrameLayout root = (FrameLayout) activity.findViewById(android.R.id.content);
        if (root == null) {
            OnError("Menu: racine de l'écran introuvable.");
            return;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        menuPanelWidth = (int) (dm.widthPixels * 0.54);

        menuOverlay = new FrameLayout(context);
        menuOverlay.setVisibility(View.GONE);
        menuOverlay.setElevation((float) dp(1));

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

        TextView item1 = menuItem("Mettre à jour les infos", new Runnable() {
            @Override
            public void run() {
                OnUpdateInfosClick();
            }
        });
        ((LinearLayout.LayoutParams) item1.getLayoutParams()).topMargin = dp(6);
        panel.addView(item1);

        panel.addView(menuItem("Détails de la boutique", new Runnable() {
            @Override
            public void run() {
                OnShopDetailsClick();
            }
        }));

        menuPanel = panel;
        menuOverlay.addView(panel, new FrameLayout.LayoutParams(
                menuPanelWidth, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));
        root.addView(menuOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @SimpleFunction(description = "Ouvre le menu latéral (Mettre à jour les infos / Détails de la boutique). Appelé aussi au toucher du menu hamburger.")
    public void OpenShopMenu() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureMenu();
                    if (menuOverlay == null || menuOpen) return;

                    // Le menu démarre à la hauteur du haut de la page (sous la barre de statut)
                    FrameLayout root = (FrameLayout) activity.findViewById(android.R.id.content);
                    int top = 0;
                    if (headerRoot != null && root != null) {
                        int[] hl = new int[2];
                        int[] rl = new int[2];
                        headerRoot.getLocationOnScreen(hl);
                        root.getLocationOnScreen(rl);
                        top = hl[1] - (int) headerRoot.getTranslationY() - rl[1];
                        if (top < 0) top = 0;
                    }
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menuOverlay.getLayoutParams();
                    lp.topMargin = top;
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
