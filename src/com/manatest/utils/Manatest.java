package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import java.net.URLEncoder;
import java.util.Iterator;

@DesignerComponent(
        version = 12,
        description = "Manatest - Page boutique unique pour le vendeur et les visiteurs : header avec le logo, barre de statut transparente, boutons Édit / Retour et Personne, menu déroulant des infos (adresse, téléphone, signaler), bouton Ajouter un produit (vendeur), compteur d'articles, et chargement des infos et des produits depuis le serveur.",
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
    private Typeface shopIconFont;

    // Données de la boutique affichée
    private String shopUidValue = "";
    private boolean ownerFlag = false;
    private String shopNameValue = "";
    private String shopCategoryValue = "";
    private String shopAddressValue = "";
    private String shopPhoneValue = "";
    private String logoSourceValue = "";
    private String productsTitleOverride = "";
    private int articleCount = 0;
    private Bitmap logoBitmap;

    // Icônes (caractères Phosphor donnés par les blocs)
    private String iconBack = "";
    private String iconEdit = "";
    private String iconPerson = "";
    private String iconPhone = "";
    private String iconAddress = "";
    private String iconReport = "";

    // Couleurs déduites du logo (calculées sur fond blanc)
    private int topColor = Color.WHITE;
    private double topLum = 1.0;
    private double contentLum = 1.0;
    private int onColor = Color.parseColor("#1A1A1B");

    // Dimensions
    private int screenW;
    private int screenH;
    private int statusBarH;
    private int logoHeightPx;
    private int topBtnW;
    private int topBtnH;
    private int extraTop = 0;         // partie du logo qui passe sous la barre de statut
    private boolean edgeMode = false; // vrai : le contenu démarre sous la barre (transparente)

    // Vues
    private LinearLayout headerHolder;
    private ScrollView scrollView;
    private FrameLayout logoFrame;
    private LinearLayout.LayoutParams logoFrameParams;
    private FrameLayout contentLayer;
    private ImageView logoImage;
    private TextView nameTv;
    private TextView categoryTv;
    private TextView leftIconTv;
    private TextView personIconTv;
    private TextView titleTv;
    private TextView countTv;
    private View statusScrim;
    private View decorBackdrop;
    private PopupWindow infoPopup;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;

    // Barre de statut d'origine
    private boolean barsSaved = false;
    private int origStatusColor;
    private int origSysUiFlags;

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

    @SimpleEvent(description = "Le vendeur propriétaire a touché le bouton Édit (en haut à gauche). Ouvre ici la page d'édition de la boutique. shopUid : l'UID de la boutique.")
    public void OnEditShopClick(String shopUid) {
        EventDispatcher.dispatchEvent(this, "OnEditShopClick", shopUid);
    }

    @SimpleEvent(description = "Un visiteur a touché le bouton Retour (en haut à gauche). Ferme ici l'écran.")
    public void OnBackClick() {
        EventDispatcher.dispatchEvent(this, "OnBackClick");
    }

    @SimpleEvent(description = "L'utilisateur a touché le numéro de téléphone dans le menu déroulant. Lance ici l'appel avec ton composant PhoneCall.")
    public void OnShopPhoneClick(String phone) {
        EventDispatcher.dispatchEvent(this, "OnShopPhoneClick", phone);
    }

    @SimpleEvent(description = "L'utilisateur a touché l'adresse dans le menu déroulant. Ouvre ici la carte ou copie l'adresse.")
    public void OnShopAddressClick(String address) {
        EventDispatcher.dispatchEvent(this, "OnShopAddressClick", address);
    }

    @SimpleEvent(description = "Un visiteur a touché « Signaler la boutique ». shopUid : l'UID de la boutique signalée. Cette ligne n'existe pas pour le vendeur propriétaire.")
    public void OnReportShopClick(String shopUid) {
        EventDispatcher.dispatchEvent(this, "OnReportShopClick", shopUid);
    }

    @SimpleEvent(description = "Le serveur a supprimé le produit (ou il n'existait déjà plus). productUid : l'uid du produit supprimé. Ferme la page d'édition et reviens à la page boutique : sa grille et son compteur se rechargent avec LoadShopFromServer.")
    public void OnProductDeleted(String productUid) {
        EventDispatcher.dispatchEvent(this, "OnProductDeleted", productUid);
    }

    @SimpleEvent(description = "La suppression du produit a échoué. responseCode : code HTTP du serveur (0 si refusée par l'application ou erreur réseau). message : la réponse du serveur ou la raison.")
    public void OnProductDeleteFailed(int responseCode, String message) {
        EventDispatcher.dispatchEvent(this, "OnProductDeleteFailed", responseCode, message);
    }

    @SimpleEvent(description = "Les infos de la boutique sont arrivées du serveur et affichées (nom, catégorie, logo, adresse, téléphone).")
    public void OnShopInfoLoaded(String name, String category, String logo, String address, String phone) {
        EventDispatcher.dispatchEvent(this, "OnShopInfoLoaded", name, category, logo, address, phone);
    }

    @SimpleEvent(description = "Les produits de la boutique sont arrivés du serveur (tableau JSON). Branche productsJson sur ManaplaceUtils.BuildProductGridFromJson. Le compteur d'articles est déjà mis à jour.")
    public void OnShopProductsLoaded(String productsJson) {
        EventDispatcher.dispatchEvent(this, "OnShopProductsLoaded", productsJson);
    }

    @SimpleEvent(description = "Le logo du header est chargé et affiché.")
    public void OnShopLogoLoaded() {
        EventDispatcher.dispatchEvent(this, "OnShopLogoLoaded");
    }

    @SimpleEvent(description = "Une erreur s'est produite (construction, logo, serveur, JSON). Les messages [diag] expliquent pourquoi la page ne s'affiche pas correctement.")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONFIGURATION : POLICE, ICÔNES, UTILISATEUR, INFOS
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

    @SimpleFunction(description = "Définit la police d'icônes (ex: Phosphor-Bold.ttf dans les Assets) utilisée par les icônes de la page. À appeler avant BuildShopHome, avec SetShopIcons.")
    public void SetShopIconFont(String fontPath) {
        if (fontPath == null || fontPath.trim().isEmpty()) {
            shopIconFont = null;
            return;
        }
        Typeface t = tryLoadTypeface(fontPath.trim());
        if (t == null) {
            OnError("SetShopIconFont: police introuvable (" + fontPath + "). Mets le fichier dans les Assets.");
        } else {
            shopIconFont = t;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                refreshIcons();
            }
        });
    }

    @SimpleFunction(description = "Définit les icônes de la page par leur caractère Phosphor. back : flèche retour (visiteur). edit : crayon (vendeur). person : personne (menu des infos). phone : téléphone. address : localisation. report : signaler. Laisse un texte vide pour utiliser le symbole par défaut (haut de page) ou aucune icône (lignes du menu).")
    public void SetShopIcons(String back, String edit, String person,
                             String phone, String address, String report) {
        iconBack = back == null ? "" : back;
        iconEdit = edit == null ? "" : edit;
        iconPerson = person == null ? "" : person;
        iconPhone = phone == null ? "" : phone;
        iconAddress = address == null ? "" : address;
        iconReport = report == null ? "" : report;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                refreshIcons();
            }
        });
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

    @SimpleFunction(description = "Définit l'adresse et le numéro de téléphone affichés dans le menu déroulant de l'icône personne.")
    public void SetShopContact(String address, String phone) {
        shopAddressValue = address == null ? "" : address.trim();
        shopPhoneValue = phone == null ? "" : phone.trim();
    }

    @SimpleFunction(description = "Change le titre au-dessus de la grille. Par défaut : « Mes Produits » pour le vendeur, « Produits » pour un visiteur.")
    public void SetProductsTitle(final String title) {
        productsTitleOverride = title == null ? "" : title.trim();
        runOnUi(new Runnable() {
            @Override
            public void run() {
                if (titleTv != null) titleTv.setText(productsTitle());
            }
        });
    }

    private String productsTitle() {
        if (!productsTitleOverride.isEmpty()) return productsTitleOverride;
        return ownerFlag ? "Mes Produits" : "Produits";
    }

    @SimpleFunction(description = "Retourne le nom de la boutique chargé.")
    public String GetShopName() {
        return shopNameValue;
    }

    @SimpleFunction(description = "Retourne la catégorie de la boutique chargée.")
    public String GetShopCategory() {
        return shopCategoryValue;
    }

    @SimpleFunction(description = "Retourne le lien ou le chemin du logo de la boutique chargé.")
    public String GetShopLogo() {
        return logoSourceValue;
    }

    @SimpleFunction(description = "Retourne l'adresse de la boutique chargée.")
    public String GetShopAddress() {
        return shopAddressValue;
    }

    @SimpleFunction(description = "Retourne le numéro de téléphone de la boutique chargé.")
    public String GetShopPhone() {
        return shopPhoneValue;
    }

    // =========================================================================
    // CONSTRUCTION DE LA PAGE
    // =========================================================================

    @SimpleFunction(description = "Construit la page boutique dans l'arrangement scrollable donné : le header (logo, nom, boutons, bouton Ajouter un produit, titre et compteur) se place tout en haut, la grille de ManaplaceUtils (BuildProductGridFromJson) se place dessous, et tout défile ensemble. shopUid : l'UID de la boutique à afficher (vide = ta propre boutique). Si c'est celui de l'utilisateur connecté (SetShopUser), la page est celle du vendeur : bouton Édit, bouton Ajouter un produit, pas de ligne Signaler. Sinon c'est un visiteur : bouton Retour et ligne Signaler la boutique.")
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
        dismissInfoMenu();

        String me = prefs().getString(PREF_CURRENT_UID, "");
        shopUidValue = uid == null ? "" : uid.trim();
        if (shopUidValue.isEmpty()) shopUidValue = me; // pas d'uid donné : sa propre boutique
        ownerFlag = isOwner(shopUidValue);
        if (me.isEmpty()) {
            OnError("[diag] utilisateur inconnu : appelle SetShopUser avec un uid non vide avant BuildShopHome. "
                    + "La page est en mode visiteur (Édit et Ajouter un produit masqués).");
        }

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        screenH = dm.heightPixels;
        screenW = dm.widthPixels;
        statusBarH = statusBarHeight();
        logoHeightPx = (int) (screenH * 0.35);
        topBtnW = (int) (screenW * 0.12);   // cercle : largeur = hauteur
        topBtnH = topBtnW;
        int btnH = (int) (screenH * 0.06);
        int btnW = (int) (screenW * 0.95);
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
            btnBg.setCornerRadius(dp(10));
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

        // Ligne : titre à gauche, carte compteur à droite
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = ownerFlag ? dp(6) : dp(10);
        rp.bottomMargin = dp(10);
        row.setLayoutParams(rp);
        row.setPadding(dp(20), 0, dp(15), 0);

        titleTv = text(productsTitle(), 24, ink(0xFF));
        titleTv.setTypeface(customFont != null
                ? Typeface.create(customFont, Typeface.BOLD) : Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(titleTv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        countTv = text(countLabel(articleCount), 12, ink(0x7E));
        countTv.setGravity(Gravity.CENTER);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F5F5F5"));
        cardBg.setCornerRadius(dp(8));
        countTv.setBackground(cardBg);
        row.addView(countTv, new LinearLayout.LayoutParams(cardW, cardH));

        holder.addView(row);

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
            OnError("[diag] le header a été supprimé du conteneur : BuildProductGridFromJson vide encore tout le conteneur. Donne-lui un arrangement séparé (non scrollable) à l'intérieur de l'arrangement scrollable.");
        }
    }

    // ---- boutons ronds du haut (Édit ou Retour à gauche, Personne à droite) ----

    private FrameLayout makeTopButton() {
        FrameLayout b = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(0x66, 0xD5, 0xD5, 0xD5));
        bg.setShape(GradientDrawable.OVAL);
        b.setBackground(bg);
        b.setClickable(true);
        return b;
    }

    // Icône Phosphor si un caractère est donné, sinon un symbole simple par défaut
    private TextView makeIconText(String icon, String fallback) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, topBtnW * 0.5f);
        t.setTextColor(ink(0xFF));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        applyIcon(t, icon, fallback);
        return t;
    }

    private void applyIcon(TextView t, String icon, String fallback) {
        if (icon != null && !icon.isEmpty()) {
            t.setText(icon);
            if (shopIconFont != null) t.setTypeface(shopIconFont);
        } else {
            t.setText(fallback);
            t.setTypeface(Typeface.DEFAULT);
        }
    }

    private void refreshIcons() {
        if (leftIconTv != null) {
            if (ownerFlag) applyIcon(leftIconTv, iconEdit, "✎");
            else applyIcon(leftIconTv, iconBack, "←");
        }
        if (personIconTv != null) applyIcon(personIconTv, iconPerson, "●");
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

        // Catégorie : centrée en haut, à la hauteur des boutons
        categoryTv = text(shopCategoryValue, 15, onColor);
        categoryTv.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cat = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, topBtnH, Gravity.TOP);
        cat.topMargin = dp(4);
        contentLayer.addView(categoryTv, cat);

        // Bouton de gauche : Édit (vendeur) ou Retour (visiteur)
        FrameLayout left = makeTopButton();
        leftIconTv = makeIconText(ownerFlag ? iconEdit : iconBack, ownerFlag ? "✎" : "←");
        left.addView(leftIconTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ownerFlag) OnEditShopClick(shopUidValue);
                else OnBackClick();
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                topBtnW, topBtnH, Gravity.TOP | Gravity.START);
        lp.leftMargin = dp(8);
        lp.topMargin = dp(4);
        contentLayer.addView(left, lp);

        // Bouton de droite : Personne (ouvre le menu des infos), pour tout le monde
        final FrameLayout right = makeTopButton();
        personIconTv = makeIconText(iconPerson, "●");
        right.addView(personIconTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfoMenu(right);
            }
        });
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                topBtnW, topBtnH, Gravity.TOP | Gravity.END);
        rp.rightMargin = dp(8);
        rp.topMargin = dp(4);
        contentLayer.addView(right, rp);

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
    // MENU DÉROULANT DES INFOS (icône personne) : adresse, téléphone, signaler
    // =========================================================================

    private LinearLayout infoRow(String icon, String label, int textColor, final Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(20), dp(12));

        if (icon != null && !icon.isEmpty()) {
            TextView iv = new TextView(context);
            iv.setText(icon);
            iv.setTextSize(18);
            iv.setTextColor(Color.parseColor("#1A1A1B"));
            if (shopIconFont != null) iv.setTypeface(shopIconFont);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.setMargins(0, 0, dp(14), 0);
            row.addView(iv, ip);
        }

        TextView tv = text(label, 15, textColor);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setMaxWidth((int) (screenW * 0.62));
        row.addView(tv);

        if (action != null) {
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissInfoMenu();
                    action.run();
                }
            });
        }
        return row;
    }

    private void showInfoMenu(View anchor) {
        try {
            dismissInfoMenu();

            LinearLayout menu = new LinearLayout(context);
            menu.setOrientation(LinearLayout.VERTICAL);
            menu.setPadding(dp(4), dp(4), dp(4), dp(4));
            menu.setMinimumWidth((int) (screenW * 0.60));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(14));
            menu.setBackground(bg);
            menu.setElevation((float) dp(4));

            // Adresse
            if (!shopAddressValue.isEmpty()) {
                final String address = shopAddressValue;
                menu.addView(infoRow(iconAddress, address, Color.parseColor("#1A1A1B"), new Runnable() {
                    @Override
                    public void run() {
                        OnShopAddressClick(address);
                    }
                }));
            } else {
                menu.addView(infoRow(iconAddress, "Adresse non renseignée", ink(0x7E), null));
            }

            // Téléphone
            if (!shopPhoneValue.isEmpty()) {
                final String phone = shopPhoneValue;
                menu.addView(infoRow(iconPhone, phone, Color.parseColor("#1A1A1B"), new Runnable() {
                    @Override
                    public void run() {
                        OnShopPhoneClick(phone);
                    }
                }));
            } else {
                menu.addView(infoRow(iconPhone, "Numéro non renseigné", ink(0x7E), null));
            }

            // Signaler : jamais sur sa propre boutique
            if (!ownerFlag) {
                View sep = new View(context);
                sep.setBackgroundColor(Color.parseColor("#EDEDED"));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                sp.setMargins(dp(10), dp(2), dp(10), dp(2));
                menu.addView(sep, sp);
                menu.addView(infoRow(iconReport, "Signaler la boutique",
                        Color.parseColor("#1A1A1B"), new Runnable() {
                            @Override
                            public void run() {
                                OnReportShopClick(shopUidValue);
                            }
                        }));
            }

            PopupWindow popup = new PopupWindow(menu,
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setElevation((float) dp(4));
            infoPopup = popup;
            popup.showAsDropDown(anchor, 0, dp(4), Gravity.END);
        } catch (Exception e) {
            OnError("Menu des infos: " + e.getMessage());
        }
    }

    private void dismissInfoMenu() {
        try {
            if (infoPopup != null && infoPopup.isShowing()) infoPopup.dismiss();
        } catch (Exception ignored) {
        }
        infoPopup = null;
    }

    // =========================================================================
    // SERVEUR : INFOS ET PRODUITS DE LA BOUTIQUE
    // =========================================================================

    private String authHeader(String a) {
        String t = a == null ? "" : a.trim();
        if (t.isEmpty()) return "";
        if (t.indexOf(' ') >= 0) return t;   // déjà complet (ex: « Bearer xxx »)
        return "Bearer " + t;                // jeton seul
    }

    private String withUid(String url, String uid) throws Exception {
        String enc = URLEncoder.encode(uid, "UTF-8");
        String u = url.trim();
        if (u.contains("{uid}")) return u.replace("{uid}", enc);
        return u + (u.contains("?") ? "&" : "?") + "uid=" + enc;
    }

    private String httpGetText(String target, String auth) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(target).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            if (auth != null && !auth.isEmpty()) {
                c.setRequestProperty("Authorization", auth);
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("serveur " + code);
            return new String(readAllBytes(c.getInputStream()), "UTF-8");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String pick(JSONObject o, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            if (o.has(keys[i]) && !o.isNull(keys[i])) {
                String v = o.optString(keys[i], "").trim();
                if (!v.isEmpty() && !"null".equals(v)) return v;
            }
        }
        return "";
    }

    // Le serveur peut répondre directement l'objet, ou l'envelopper dans data / shop / boutique
    private JSONObject shopObject(String body) throws Exception {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty() || "null".equals(t)) return null;
        JSONObject o = new JSONObject(t);
        if (!pick(o, "name", "nom", "shopName", "boutique").isEmpty()) return o;
        String[] wrap = {"data", "shop", "boutique", "infos", "info"};
        for (int i = 0; i < wrap.length; i++) {
            JSONObject n = o.optJSONObject(wrap[i]);
            if (n != null) return n;
        }
        return o;
    }

    // Retourne toujours un tableau JSON de produits (gère aussi la forme { id: produit } de Firebase)
    private String normalizeProducts(String body) throws Exception {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty() || "null".equals(t)) return "[]";
        if (t.startsWith("[")) return t;
        JSONObject o = new JSONObject(t);
        String[] keys = {"products", "produits", "items", "articles", "data"};
        for (int i = 0; i < keys.length; i++) {
            Object v = o.opt(keys[i]);
            if (v instanceof JSONArray) return v.toString();
            if (v instanceof JSONObject) {
                o = (JSONObject) v;
                break;
            }
        }
        JSONArray out = new JSONArray();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v instanceof JSONObject) {
                JSONObject p = (JSONObject) v;
                if (!p.has("uid")) p.put("uid", k);
                out.put(p);
            }
        }
        return out.toString();
    }

    @SimpleFunction(description = "Charge la boutique depuis le serveur. Appelle urlInfos et urlProduits avec ?uid= (ou à la place de {uid} dans l'adresse) : l'UID de la boutique donné à BuildShopHome. Infos attendues en JSON : name, category, logo, address, phone. Les infos remplissent la page (nom, catégorie, logo, menu) et OnShopInfoLoaded se déclenche. Les produits mettent à jour le compteur et OnShopProductsLoaded donne le JSON à brancher sur BuildProductGridFromJson. authorization : jeton (avec ou sans « Bearer »), vide si inutile. Laisse une adresse vide pour ne pas l'appeler. À appeler après BuildShopHome.")
    public void LoadShopFromServer(final String urlInfos, final String urlProduits,
                                   final String authorization) {
        String uid = shopUidValue;
        if (uid.isEmpty()) uid = prefs().getString(PREF_CURRENT_UID, "");
        if (uid.isEmpty()) {
            OnError("LoadShopFromServer: uid inconnu. Appelle SetShopUser puis BuildShopHome avant.");
            return;
        }
        final String shopUid = uid;
        final String auth = authHeader(authorization);

        if (urlInfos != null && !urlInfos.trim().isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject o = shopObject(httpGetText(withUid(urlInfos, shopUid), auth));
                        if (o == null) throw new Exception("infos de la boutique vides");
                        final String name = pick(o, "name", "nom", "shopName", "boutique", "title");
                        final String category = pick(o, "category", "categorie", "catégorie", "cat");
                        final String logo = pick(o, "logo", "photo", "image", "logoUrl", "logo_url", "avatar");
                        final String address = pick(o, "address", "adresse", "location");
                        final String phone = pick(o, "phone", "telephone", "téléphone", "tel", "phoneNumber", "numero");
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                applyShopInfo(name, category, logo, address, phone);
                            }
                        });
                    } catch (Exception e) {
                        fail("LoadShopFromServer (infos): " + e.getMessage());
                    }
                }
            }).start();
        }

        if (urlProduits != null && !urlProduits.trim().isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final String json = normalizeProducts(
                                httpGetText(withUid(urlProduits, shopUid), auth));
                        final int n = new JSONArray(json).length();
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                showCount(n);
                                OnShopProductsLoaded(json);
                            }
                        });
                    } catch (Exception e) {
                        fail("LoadShopFromServer (produits): " + e.getMessage());
                    }
                }
            }).start();
        }
    }

    private void applyShopInfo(String name, String category, String logo,
                               String address, String phone) {
        if (!name.isEmpty()) shopNameValue = name;
        if (!category.isEmpty()) shopCategoryValue = category;
        if (!address.isEmpty()) shopAddressValue = address;
        if (!phone.isEmpty()) shopPhoneValue = phone;
        if (nameTv != null) nameTv.setText(shopNameValue);
        if (categoryTv != null) categoryTv.setText(shopCategoryValue);
        if (!logo.isEmpty()) SetShopLogoSource(logo);
        OnShopInfoLoaded(shopNameValue, shopCategoryValue, logo, shopAddressValue, shopPhoneValue);
    }

    // =========================================================================
    // LOGO DU VENDEUR
    // =========================================================================

    @SimpleFunction(description = "Charge le logo du vendeur dans le header : lien http(s)://, chemin de fichier (file://...) ou nom d'un fichier des Assets. Les couleurs du texte et de la barre de statut s'adaptent au logo (blanc sur logo sombre, sombre sur logo clair).")
    public void SetShopLogoSource(final String source) {
        if (source == null || source.trim().isEmpty()) {
            OnError("SetShopLogoSource: source vide.");
            return;
        }
        final String src = source.trim();
        logoSourceValue = src;
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
            if (total > 12 * 1024 * 1024) throw new Exception("réponse trop lourde");
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
            int sw = context.getResources().getDisplayMetrics().widthPixels;
            float scale = Math.max((float) sw / bw, (float) logoHeightPx / bh);
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
                dismissInfoMenu();
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
    // SUPPRESSION D'UN PRODUIT
    // =========================================================================

    private String deleteUrlValue = "";
    private String deleteAuthValue = "";
    private boolean deleting = false;

    @SimpleFunction(description = "Définit une fois l'adresse du serveur qui supprime un produit, et le jeton (avec ou sans « Bearer »). L'adresse peut contenir {uid} (uid du vendeur) et {productUid} (uid du produit), par exemple https://serveur/{uid}/products/{productUid}. Sans ces repères, ?uid=...&productUid=... est ajouté à la fin. La suppression est envoyée avec la méthode DELETE.")
    public void SetProductDeleteUrl(String url, String authorization) {
        deleteUrlValue = url == null ? "" : url.trim();
        deleteAuthValue = authHeader(authorization);
    }

    private boolean deleteBodyOk(String body) {
        try {
            JSONObject o = new JSONObject(body.trim());
            if (o.has("success")) return o.optBoolean("success");
            if (o.has("error")) return false;
            return true;
        } catch (Exception e) {
            return true; // réponse sans JSON : le code HTTP décide
        }
    }

    @SimpleFunction(description = "Supprime un produit sur le serveur. productUid : l'uid du produit (donné par ManaplaceUtils.OnProductCardClick). sellerUid : l'uid Firebase du vendeur, sous lequel se trouve le produit. Refusé si sellerUid n'est pas l'utilisateur connecté (SetShopUser). Résultat : OnProductDeleted ou OnProductDeleteFailed. Demande toujours une confirmation avant, la suppression est définitive. Appelle SetProductDeleteUrl avant.")
    public void DeleteProduct(final String productUid, final String sellerUid) {
        final String pUid = productUid == null ? "" : productUid.trim();
        final String sUid = sellerUid == null ? "" : sellerUid.trim();
        if (deleteUrlValue.isEmpty()) {
            OnError("DeleteProduct: appelle SetProductDeleteUrl avant.");
            return;
        }
        if (pUid.isEmpty() || sUid.isEmpty()) {
            OnError("DeleteProduct: l'uid du produit ou du vendeur est vide.");
            return;
        }
        if (!isOwner(sUid)) {
            OnProductDeleteFailed(0, "Suppression refusée : ce vendeur n'est pas l'utilisateur connecté (appelle SetShopUser).");
            return;
        }
        if (deleting) return;
        deleting = true;

        final String baseUrl = deleteUrlValue;
        final String auth = deleteAuthValue;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    String encSeller = URLEncoder.encode(sUid, "UTF-8");
                    String encProduct = URLEncoder.encode(pUid, "UTF-8");
                    String target;
                    if (baseUrl.contains("{uid}") || baseUrl.contains("{productUid}")) {
                        target = baseUrl.replace("{uid}", encSeller).replace("{productUid}", encProduct);
                    } else {
                        target = baseUrl + (baseUrl.contains("?") ? "&" : "?")
                                + "uid=" + encSeller + "&productUid=" + encProduct;
                    }
                    c = (HttpURLConnection) new URL(target).openConnection();
                    c.setRequestMethod("DELETE");
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("Accept", "application/json");
                    if (!auth.isEmpty()) c.setRequestProperty("Authorization", auth);

                    final int code = c.getResponseCode();
                    InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    final String body = is == null ? "" : new String(readAllBytes(is), "UTF-8");
                    // 404 : le produit n'existe déjà plus, le résultat voulu est atteint
                    final boolean ok = (code >= 200 && code < 300 && deleteBodyOk(body)) || code == 404;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            deleting = false;
                            if (ok) {
                                showCount(Math.max(0, articleCount - 1));
                                OnProductDeleted(pUid);
                            } else {
                                OnProductDeleteFailed(code, body);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            deleting = false;
                            OnProductDeleteFailed(0, "Réseau : " + e.getMessage());
                        }
                    });
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
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
}
