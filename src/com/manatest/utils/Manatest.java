package com.manatest.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

@DesignerComponent(
        version = 13,
        description = "Manatest - Page détails produit construite en code : images défilantes avec points de pagination, titre, prix, cartes Stock et Livraison (texte barré si absent), description avec état / couleur / taille, quantité plus / moins limitée au stock, cercle vendeur cliquable et partage du produit (photo + lien) vers WhatsApp ou toute autre application.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class Manatest extends AndroidNonvisibleComponent {

    private static final String PREFS_NAME = "ManaplaceShop";
    private static final String PREF_CURRENT_UID = "current_uid";

    private final Context context;
    private final Activity activity;

    private Typeface customFont;
    private Typeface shopIconFont;
    private int screenW;
    private int screenH;

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

    private boolean isOwner(String uid) {
        String me = prefs().getString(PREF_CURRENT_UID, "");
        return uid != null && !uid.isEmpty() && uid.equals(me);
    }

    // =========================================================================
    // ÉVÉNEMENT D'ERREUR
    // =========================================================================

    @SimpleEvent(description = "Une erreur s'est produite (construction, logo, serveur, JSON). Les messages [diag] expliquent pourquoi la page ne s'affiche pas correctement.")
    public void OnError(String message) {
        EventDispatcher.dispatchEvent(this, "OnError", message);
    }

    // =========================================================================
    // CONFIGURATION : POLICE, ICÔNES, UTILISATEUR
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

    private void ensureDefaultFont() {
        if (customFont == null) customFont = tryLoadTypeface("Manrope-Medium.ttf");
    }

    @SimpleFunction(description = "Charge la police du texte (ex: Manrope-Medium.ttf dans les Assets). Si tu ne l'appelles pas, Manrope-Medium.ttf est utilisée automatiquement quand elle est dans les Assets. À appeler avant BuildProductPage.")
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

    @SimpleFunction(description = "Définit la police d'icônes (ex: Phosphor-Bold.ttf dans les Assets) utilisée par les icônes de la page produit. À appeler avant BuildProductPage, avec SetProductIcons.")
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
                refreshProductIcons();
            }
        });
    }

    @SimpleFunction(description = "Mémorise l'utilisateur connecté (UID Firebase). À appeler une fois à la connexion : la page produit s'en sert pour savoir si le produit est le sien (IsProductOwner, OnSellerClick).")
    public void SetShopUser(String uid) {
        prefs().edit().putString(PREF_CURRENT_UID, uid == null ? "" : uid.trim()).apply();
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

    // =========================================================================
    // IMAGES ET JSON
    // =========================================================================

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

    private String pick(JSONObject o, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            if (o.has(keys[i]) && !o.isNull(keys[i])) {
                String v = o.optString(keys[i], "").trim();
                if (!v.isEmpty() && !"null".equals(v)) return v;
            }
        }
        return "";
    }

    // =========================================================================
    // PAGE DÉTAILS PRODUIT
    // =========================================================================

    private static final String PRODUCT_TAG = "manatest_product";
    private static final int MAX_QTY = 999999;

    private LinearLayout pHolder;
    private JSONObject pData = new JSONObject();
    private final ArrayList<String> pImageUrls = new ArrayList<String>();
    private final HashMap<String, Bitmap> pBitmaps = new HashMap<String, Bitmap>();
    private HorizontalScrollView pHsv;
    private LinearLayout pImagesRow;
    private LinearLayout pDotsRow;
    private TextView pTitleTv;
    private TextView pPriceCurTv;
    private TextView pPriceAmountTv;
    private TextView pStockText;
    private TextView pStockIconTv;
    private TextView pDeliveryText;
    private TextView pDeliveryIconTv;
    private TextView pQtyText;
    private TextView pQtyMinus;
    private TextView pQtyPlus;
    private LinearLayout pDescBlock;
    private ImageView pSellerImg;
    private int pImageToken = 0;
    private int pLogoToken = 0;
    private int pPage = 0;
    private int pQuantity = 1;
    private boolean pDetailsReceived = false;
    private String pShareUrl = "";
    private String pDefaultSellerLogo = "";
    private String pLoadedLogo = "";
    private String pIconStock = "";
    private String pIconDelivery = "";
    private String pIconMinus = "";
    private String pIconPlus = "";
    private int pTopPct = 11;
    private int pBottomPct = 12;

    // ---- événements ----

    @SimpleEvent(description = "Le cercle du vendeur a été touché. sellerUid : l'UID du vendeur du produit. isMine : vrai si c'est ton propre produit (ton UID défini avec SetShopUser). Branche ici BuildShopHome avec sellerUid pour ouvrir la boutique du vendeur.")
    public void OnSellerClick(String sellerUid, boolean isMine) {
        EventDispatcher.dispatchEvent(this, "OnSellerClick", sellerUid, isMine);
    }

    @SimpleEvent(description = "La quantité a changé avec les boutons plus / moins.")
    public void OnProductQuantityChanged(int quantity) {
        EventDispatcher.dispatchEvent(this, "OnProductQuantityChanged", quantity);
    }

    @SimpleEvent(description = "Le bouton plus a été touché alors que la quantité est déjà au maximum (le stock disponible). maxQuantity : ce maximum (0 = rupture de stock). Sert à afficher un message.")
    public void OnProductQuantityLimit(int maxQuantity) {
        EventDispatcher.dispatchEvent(this, "OnProductQuantityLimit", maxQuantity);
    }

    // ---- configuration ----

    @SimpleFunction(description = "Définit les icônes de la page produit par leur caractère Phosphor. stock : icône de la carte En stock. delivery : icône camion de la carte Livraison. minus : icône moins. plus : icône plus. Texte vide = pas d'icône (pour moins et plus, un symbole simple est utilisé). La police d'icônes se règle avec SetShopIconFont. À appeler avant BuildProductPage.")
    public void SetProductIcons(String stock, String delivery, String minus, String plus) {
        pIconStock = stock == null ? "" : stock;
        pIconDelivery = delivery == null ? "" : delivery;
        pIconMinus = minus == null ? "" : minus;
        pIconPlus = plus == null ? "" : plus;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                refreshProductIcons();
            }
        });
    }

    @SimpleFunction(description = "Réserve de l'espace en haut et en bas de la page produit, en pourcentage de la hauteur de l'écran. topPercent : place pour tes boutons retour et partage au-dessus de l'image (11 par défaut, 0 = aucune). bottomPercent : place pour ta barre Envoyer / Panier en bas (12 par défaut). À appeler avant BuildProductPage.")
    public void SetProductPageSpacing(int topPercent, int bottomPercent) {
        pTopPct = Math.max(0, Math.min(40, topPercent));
        pBottomPct = Math.max(0, Math.min(40, bottomPercent));
    }

    @SimpleFunction(description = "Adresse du produit utilisée pour le partage. Mets {uid} là où l'UID du produit doit apparaître (ex: https://monsite.com/produit/{uid}). Sans {uid} : si l'adresse finit par / l'UID est ajouté à la fin, sinon ?uid=... est ajouté.")
    public void SetProductShareUrl(String urlTemplate) {
        pShareUrl = urlTemplate == null ? "" : urlTemplate.trim();
    }

    @SimpleFunction(description = "Image du cercle vendeur quand le serveur n'envoie pas de logo (nom de fichier des Assets, ex: logo-manaplace.png, ou adresse http).")
    public void SetDefaultSellerLogo(String source) {
        pDefaultSellerLogo = source == null ? "" : source.trim();
    }

    // ---- construction ----

    @SimpleFunction(description = "Construit la page détails produit dans l'arrangement scrollable donné (images défilantes avec points, titre, prix, cartes Stock et Livraison, description, quantité, vendeur). productJson : le JSON reçu par ManaplaceUtils.OnProductCardClick. Les détails complets (description, stock, autres images...) s'ajoutent ensuite avec SetProductDetailsFromJson.")
    public void BuildProductPage(final AndroidViewComponent scrollContainer, final String productJson) {
        if (scrollContainer == null || scrollContainer.getView() == null) {
            OnError("BuildProductPage: conteneur invalide.");
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    buildProductPageInternal(scrollContainer, productJson);
                } catch (Exception e) {
                    OnError("BuildProductPage: " + e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "Reçoit les détails du produit et met la page à jour. detailsJson : par exemple le résultat de ManaplaceUtils.BuildProductDetailsJson (à appeler dans OnProductDetailsReceived), ou la réponse brute du serveur. Clés lues : description, stock (ou quantity), image2 à image5, images, deliveryIncluded, condition, color, size, sellerUid, sellerLogo (ou un objet seller avec uid et logo).")
    public void SetProductDetailsFromJson(final String detailsJson) {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    if (detailsJson == null || detailsJson.trim().isEmpty()) return;
                    mergeProduct(new JSONObject(detailsJson));
                    pDetailsReceived = true;
                    renderProduct();
                } catch (Exception e) {
                    OnError("SetProductDetailsFromJson: " + e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "Ajoute l'état, la couleur, la taille et le vendeur du produit (si ton serveur les envoie séparément). Les champs vides sont ignorés. sellerUid : l'UID du vendeur. sellerLogo : adresse du logo du vendeur. À appeler après BuildProductPage.")
    public void SetProductExtras(final String condition, final String color, final String size,
                                 final String sellerUid, final String sellerLogo) {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                try {
                    putIfNotEmpty("condition", condition);
                    putIfNotEmpty("color", color);
                    putIfNotEmpty("size", size);
                    putIfNotEmpty("sellerUid", sellerUid);
                    putIfNotEmpty("sellerLogo", sellerLogo);
                    pDetailsReceived = true;
                    renderProduct();
                } catch (Exception e) {
                    OnError("SetProductExtras: " + e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "À appeler en quittant la page produit : retire la page et libère les images.")
    public void ReleaseProductPage() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                removeProductHolder();
                pData = new JSONObject();
                pImageUrls.clear();
                pBitmaps.clear();
                pImageToken++;
                pLogoToken++;
            }
        });
    }

    // ---- quantité et lecture ----

    @SimpleFunction(description = "Retourne la quantité choisie par l'acheteur (1 au minimum, le stock au maximum ; 0 si rupture de stock).")
    public int GetProductQuantity() {
        return pQuantity;
    }

    @SimpleFunction(description = "Change la quantité affichée (elle reste entre 1 et le stock disponible).")
    public void SetProductQuantity(final int quantity) {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                pQuantity = quantity;
                clampQuantity();
                updateQuantityView();
            }
        });
    }

    @SimpleFunction(description = "Retourne vrai si le produit affiché appartient à l'utilisateur connecté (UID du vendeur = SetShopUser).")
    public boolean IsProductOwner() {
        return isOwner(pSellerUid());
    }

    @SimpleFunction(description = "Retourne l'UID du produit affiché.")
    public String GetProductUid() {
        return pUid();
    }

    @SimpleFunction(description = "Retourne l'UID du vendeur du produit affiché (vide si le serveur ne l'a pas envoyé).")
    public String GetProductSellerUid() {
        return pSellerUid();
    }

    @SimpleFunction(description = "Retourne le titre du produit affiché.")
    public String GetProductTitle() {
        return pick(pData, "title", "name", "titre");
    }

    @SimpleFunction(description = "Retourne l'adresse de la première image du produit affiché.")
    public String GetProductImage() {
        ArrayList<String> urls = collectImages(pData);
        return urls.isEmpty() ? "" : urls.get(0);
    }

    @SimpleFunction(description = "Retourne le lien de partage du produit (SetProductShareUrl avec l'UID du produit). Vide si SetProductShareUrl n'a pas été appelé.")
    public String GetProductShareLink() {
        return pShareLink();
    }

    @SimpleFunction(description = "Retourne le produit complet en JSON (infos de la carte et détails réunis).")
    public String GetProductJson() {
        return pData.toString();
    }

    // ---- partage ----

    @SimpleFunction(description = "Ouvre le menu de partage Android (WhatsApp ou toute autre application) avec la photo du produit, son titre, son prix et son lien (SetProductShareUrl). Si la photo ne peut pas être préparée, le texte et le lien sont partagés seuls. À brancher sur ton bouton partager.")
    public void ShareProduct() {
        if (pData.length() == 0) {
            OnError("ShareProduct: aucun produit chargé (appelle BuildProductPage d'abord).");
            return;
        }
        final String title = pick(pData, "title", "name", "titre");
        final String uid = pUid();
        final String link = pShareLink();
        ArrayList<String> urls = collectImages(pData);
        final String firstUrl = urls.isEmpty() ? "" : urls.get(0);
        final Bitmap cached = firstUrl.isEmpty() ? null : pBitmaps.get(firstUrl);

        StringBuilder sb = new StringBuilder(title);
        String amount = pPriceAmount();
        if (!amount.isEmpty()) sb.append("\n").append(pPriceCur()).append(" ").append(amount);
        if (!link.isEmpty()) sb.append("\n").append(link);
        final String body = sb.toString();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = cached;
                if (bmp == null && !firstUrl.isEmpty()) {
                    try {
                        bmp = loadBitmap(firstUrl, 1080);
                    } catch (Exception ignored) {
                    }
                }
                final Uri uri = bmp == null ? null
                        : saveBitmapForShare(bmp, "manaplace_" + (uid.isEmpty() ? String.valueOf(System.currentTimeMillis()) : uid));
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        startShare(uri, title, body);
                    }
                });
            }
        }).start();
    }

    private Uri saveBitmapForShare(Bitmap bmp, String name) {
        try {
            android.content.ContentResolver cr = context.getContentResolver();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg");
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put("relative_path", "Pictures/Manaplace");
                v.put("is_pending", 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) return null;
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
                os.close();
                ContentValues done = new ContentValues();
                done.put("is_pending", 0);
                cr.update(uri, done, null, null);
                return uri;
            }
            String p = MediaStore.Images.Media.insertImage(cr, bmp, name, "");
            return p == null ? null : Uri.parse(p);
        } catch (Exception e) {
            return null;
        }
    }

    private void startShare(Uri uri, String title, String body) {
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            if (uri != null) {
                it.setType("image/*");
                it.putExtra(Intent.EXTRA_STREAM, uri);
                it.setClipData(ClipData.newRawUri("", uri));
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                it.setType("text/plain");
            }
            it.putExtra(Intent.EXTRA_SUBJECT, title);
            it.putExtra(Intent.EXTRA_TEXT, body);
            Intent chooser = Intent.createChooser(it, "Partager");
            if (uri != null) {
                chooser.setClipData(ClipData.newRawUri("", uri));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            activity.startActivity(chooser);
        } catch (Exception e) {
            OnError("ShareProduct: " + e.getMessage());
        }
    }

    // ---- données ----

    private void putIfNotEmpty(String key, String value) throws Exception {
        if (value != null && !value.trim().isEmpty()) pData.put(key, value.trim());
    }

    // Fusionne un JSON produit (objet direct, ou enveloppé dans product / data) sans écraser par du vide
    private void mergeProduct(JSONObject src) throws Exception {
        JSONObject o = src;
        JSONObject nested = src.optJSONObject("product");
        if (nested != null) {
            o = nested;
        } else {
            JSONObject d = src.optJSONObject("data");
            if (d != null) o = d;
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL) continue;
            if (v instanceof String && ((String) v).trim().isEmpty()) continue;
            pData.put(k, v);
        }
    }

    private String pUid() {
        return pick(pData, "productUid", "uid", "id");
    }

    private String pSellerUid() {
        String u = pick(pData, "sellerUid", "seller_uid", "ownerUid", "owner_uid", "shopUid", "shop_uid",
                "vendorUid", "vendeurUid", "uidVendeur", "userUid", "user_uid");
        if (u.isEmpty()) {
            JSONObject s = pData.optJSONObject("seller");
            if (s == null) s = pData.optJSONObject("shop");
            if (s != null) u = pick(s, "uid", "id", "sellerUid");
        }
        return u;
    }

    private String pSellerLogo() {
        String l = pick(pData, "sellerLogo", "seller_logo", "shopLogo", "shop_logo", "sellerImage",
                "sellerPhoto", "vendeurLogo");
        if (l.isEmpty()) {
            JSONObject s = pData.optJSONObject("seller");
            if (s == null) s = pData.optJSONObject("shop");
            if (s != null) l = pick(s, "logo", "photo", "image", "avatar", "logoUrl", "logo_url");
        }
        return l;
    }

    private String pPriceRaw() {
        return pData.has("price_display") ? pick(pData, "price_display") : pick(pData, "price");
    }

    private String pPriceCur() {
        String raw = pPriceRaw();
        String cur = pick(pData, "currency", "devise").toUpperCase(Locale.ROOT);
        if (cur.equals("USD") || raw.contains("$")) return "$";
        if (cur.equals("EUR") || raw.contains("€")) return "€";
        if (cur.isEmpty() || cur.equals("HTG")) return "G";
        return cur;
    }

    private String pPriceAmount() {
        String raw = pPriceRaw().replaceAll("^[^0-9]+|[^0-9]+$", "").trim();
        return raw.replaceAll("[.,]0{1,2}$", "");
    }

    private String pShareLink() {
        String t = pShareUrl == null ? "" : pShareUrl.trim();
        if (t.isEmpty()) return "";
        try {
            String enc = URLEncoder.encode(pUid(), "UTF-8");
            if (t.contains("{uid}")) return t.replace("{uid}", enc);
            if (t.endsWith("/")) return t + enc;
            return t + (t.contains("?") ? "&" : "?") + "uid=" + enc;
        } catch (Exception e) {
            return t;
        }
    }

    private boolean pBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase(Locale.ROOT);
            return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("oui");
        }
        return false;
    }

    // {connu ? 1 : 0, valeur}
    private int[] pStock() {
        if (pData.has("stock") && !pData.isNull("stock")) return new int[]{1, pData.optInt("stock", 0)};
        if (pData.has("quantity") && !pData.isNull("quantity")) return new int[]{1, pData.optInt("quantity", 0)};
        return new int[]{0, 0};
    }

    private int pMaxQty() {
        int[] s = pStock();
        return s[0] == 1 ? Math.max(0, s[1]) : MAX_QTY;
    }

    private void clampQuantity() {
        int max = pMaxQty();
        if (max <= 0) {
            pQuantity = 0;
        } else {
            if (pQuantity < 1) pQuantity = 1;
            if (pQuantity > max) pQuantity = max;
        }
    }

    private void addOneImage(ArrayList<String> out, String s) {
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty() || "null".equals(s)) return;
        if (!out.contains(s)) out.add(s);
    }

    private void addImagesFrom(ArrayList<String> out, Object v) {
        if (v == null || v == JSONObject.NULL) return;
        try {
            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                for (int i = 0; i < a.length(); i++) {
                    Object e = a.opt(i);
                    if (e instanceof JSONObject) {
                        addOneImage(out, pick((JSONObject) e, "url", "src", "image", "link"));
                    } else if (e != null && e != JSONObject.NULL) {
                        addOneImage(out, String.valueOf(e));
                    }
                }
            } else if (v instanceof String) {
                String s = ((String) v).trim();
                if (s.startsWith("[")) addImagesFrom(out, new JSONArray(s));
                else addOneImage(out, s);
            }
        } catch (Exception ignored) {
        }
    }

    private ArrayList<String> collectImages(JSONObject o) {
        ArrayList<String> out = new ArrayList<String>();
        addImagesFrom(out, o.opt("images"));
        addImagesFrom(out, o.opt("image"));
        for (int i = 2; i <= 5; i++) addImagesFrom(out, o.opt("image" + i));
        return out;
    }

    // ---- construction de la vue ----

    private int padPx() {
        return (int) (screenW * 0.033f);
    }

    private void buildProductPageInternal(AndroidViewComponent container, String productJson) {
        ensureDefaultFont();

        ScrollView sc = findScrollView(container.getView(), 0);
        if (sc == null) {
            OnError("BuildProductPage: le conteneur doit être un arrangement vertical scrollable.");
            return;
        }
        if (sc.getChildCount() == 0 || !(sc.getChildAt(0) instanceof LinearLayout)
                || ((LinearLayout) sc.getChildAt(0)).getOrientation() != LinearLayout.VERTICAL) {
            OnError("BuildProductPage: contenu de l'arrangement scrollable inattendu.");
            return;
        }
        LinearLayout inner = (LinearLayout) sc.getChildAt(0);

        removeProductHolder();
        for (int i = inner.getChildCount() - 1; i >= 0; i--) {
            if (PRODUCT_TAG.equals(inner.getChildAt(i).getTag())) inner.removeViewAt(i);
        }

        pData = new JSONObject();
        pImageUrls.clear();
        pBitmaps.clear();
        pImageToken++;
        pLogoToken++;
        pPage = 0;
        pQuantity = 1;
        pDetailsReceived = false;
        pLoadedLogo = "";
        try {
            if (productJson != null && !productJson.trim().isEmpty()) {
                mergeProduct(new JSONObject(productJson));
            }
        } catch (Exception e) {
            OnError("BuildProductPage: productJson invalide (" + e.getMessage() + ").");
        }

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        int pad = padPx();
        int black = Color.BLACK;
        int green = Color.argb(0xFF, 0x85, 0xED, 0x82);

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setTag(PRODUCT_TAG);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // place pour les boutons retour / partage (les tiens)
        View top = new View(context);
        top.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (screenH * pTopPct / 100f)));
        holder.addView(top);

        holder.addView(buildProductImages());

        // titre : taille 25, noir
        pTitleTv = text("", 25, black);
        pTitleTv.setPadding(pad, dp(18), pad, 0);
        holder.addView(pTitleTv);

        // prix : devise petite + montant plus grand
        LinearLayout priceRow = new LinearLayout(context);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setPadding(pad, dp(10), pad, dp(14));
        pPriceCurTv = text("", 16, black);
        pPriceCurTv.setPadding(0, 0, dp(6), 0);
        pPriceAmountTv = text("", 30, black);
        priceRow.addView(pPriceCurTv);
        priceRow.addView(pPriceAmountTv);
        holder.addView(priceRow);

        View sep = new View(context);
        sep.setBackgroundColor(Color.parseColor("#EDEDED"));
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        holder.addView(sep);

        // cartes Stock (40% x 10%) et Livraison (50% x 10%)
        int cardH = (int) (screenH * 0.10f);
        int gap = (int) (screenW * 0.03f);
        LinearLayout statRow = new LinearLayout(context);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setPadding(pad, dp(16), 0, 0);

        LinearLayout stockCard = new LinearLayout(context);
        stockCard.setOrientation(LinearLayout.HORIZONTAL);
        stockCard.setGravity(Gravity.CENTER);
        stockCard.setLayoutParams(new LinearLayout.LayoutParams((int) (screenW * 0.40f), cardH));
        GradientDrawable stockBg = new GradientDrawable();
        stockBg.setColor(Color.argb(0xE9, 0x1A, 0x1A, 0x1B));
        stockCard.setBackground(stockBg);
        pStockIconTv = prodIcon(pIconStock, "", 20, green);
        pStockIconTv.setPadding(0, 0, dp(8), 0);
        pStockText = text("En stock", 12, green);
        stockCard.addView(pStockIconTv);
        stockCard.addView(pStockText);
        statRow.addView(stockCard);

        LinearLayout deliveryCard = new LinearLayout(context);
        deliveryCard.setOrientation(LinearLayout.HORIZONTAL);
        deliveryCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (screenW * 0.50f), cardH);
        dlp.setMargins(gap, 0, 0, 0);
        deliveryCard.setLayoutParams(dlp);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.WHITE);
        delBg.setStroke(dp(1), black);
        deliveryCard.setBackground(delBg);
        pDeliveryIconTv = prodIcon(pIconDelivery, "", 20, black);
        pDeliveryIconTv.setPadding(0, 0, dp(8), 0);
        pDeliveryText = text("Livraison inclus", 12, black);
        deliveryCard.addView(pDeliveryIconTv);
        deliveryCard.addView(pDeliveryText);
        statRow.addView(deliveryCard);
        holder.addView(statRow);

        // description (le texte du produit prend la place du mot « Description »)
        pDescBlock = new LinearLayout(context);
        pDescBlock.setOrientation(LinearLayout.VERTICAL);
        pDescBlock.setPadding(pad, dp(18), pad, 0);
        holder.addView(pDescBlock);

        // quantité : carte 40% x 10%, fond F5F5F5, texte 20
        TextView qtyTitle = text("Quantité", 15, black);
        qtyTitle.setPadding(pad, dp(22), pad, 0);
        holder.addView(qtyTitle);

        LinearLayout qtyCard = new LinearLayout(context);
        qtyCard.setOrientation(LinearLayout.HORIZONTAL);
        qtyCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams((int) (screenW * 0.40f), cardH);
        qlp.setMargins(pad, dp(10), 0, 0);
        qtyCard.setLayoutParams(qlp);
        GradientDrawable qBg = new GradientDrawable();
        qBg.setColor(Color.rgb(0xF5, 0xF5, 0xF5));
        qBg.setStroke(dp(1), black);
        qtyCard.setBackground(qBg);

        pQtyMinus = prodIcon(pIconMinus, "−", 22, black);
        pQtyMinus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pQtyMinus.setClickable(true);
        pQtyMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pQuantity > 1) {
                    pQuantity--;
                    updateQuantityView();
                    OnProductQuantityChanged(pQuantity);
                }
            }
        });
        pQtyText = text("1", 20, black);
        pQtyText.setGravity(Gravity.CENTER);
        pQtyText.setMinWidth(dp(40));
        pQtyPlus = prodIcon(pIconPlus, "+", 22, black);
        pQtyPlus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pQtyPlus.setClickable(true);
        pQtyPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int max = pMaxQty();
                if (pQuantity < max) {
                    pQuantity++;
                    updateQuantityView();
                    OnProductQuantityChanged(pQuantity);
                } else {
                    OnProductQuantityLimit(max);
                }
            }
        });
        qtyCard.addView(pQtyMinus);
        qtyCard.addView(pQtyText);
        qtyCard.addView(pQtyPlus);
        holder.addView(qtyCard);

        // vendeur : titre 15 + cercle avec le logo
        TextView sellerTitle = text("Vendeur", 15, black);
        sellerTitle.setPadding(pad, dp(22), pad, 0);
        holder.addView(sellerTitle);

        int sz = (int) (screenW * 0.15f);
        LinearLayout sellerWrap = new LinearLayout(context);
        sellerWrap.setOrientation(LinearLayout.HORIZONTAL);
        sellerWrap.setPadding(pad, dp(10), pad, 0);
        pSellerImg = new ImageView(context);
        pSellerImg.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        pSellerImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor("#F0F0F0"));
        pSellerImg.setBackground(circleBg);
        pSellerImg.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        pSellerImg.setClipToOutline(true);
        pSellerImg.setClickable(true);
        pSellerImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String su = pSellerUid();
                if (su.isEmpty()) {
                    OnError("[diag] uid du vendeur inconnu : le serveur doit envoyer sellerUid avec le produit (ou utilise SetProductExtras).");
                    return;
                }
                OnSellerClick(su, isOwner(su));
            }
        });
        sellerWrap.addView(pSellerImg);
        holder.addView(sellerWrap);

        // place pour ta barre Envoyer / Panier
        View bottom = new View(context);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (screenH * pBottomPct / 100f)));
        holder.addView(bottom);

        inner.addView(holder, 0);
        pHolder = holder;
        renderProduct();
        final ScrollView scf = sc;
        scf.post(new Runnable() {
            @Override
            public void run() {
                scf.scrollTo(0, 0);
            }
        });
    }

    private View buildProductImages() {
        int imageH = (int) (screenW * 0.605f);
        FrameLayout frame = new FrameLayout(context);
        frame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, imageH));

        pHsv = new HorizontalScrollView(context) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                updateDotsFromScroll(l);
            }
        };
        pHsv.setHorizontalScrollBarEnabled(false);
        pHsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pHsv.setFillViewport(false);
        final HorizontalScrollView hsv = pHsv;
        final int[] startPage = {0};
        hsv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int w = screenW;
                int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    startPage[0] = Math.round(hsv.getScrollX() / (float) w);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    float off = hsv.getScrollX() - startPage[0] * w;
                    int page = startPage[0];
                    if (off > w * 0.15f) page++;
                    else if (off < -w * 0.15f) page--;
                    int last = Math.max(0, Math.max(1, pImageUrls.size()) - 1);
                    page = Math.max(0, Math.min(last, page));
                    hsv.smoothScrollTo(page * w, 0);
                    return true;
                }
                return false;
            }
        });
        pImagesRow = new LinearLayout(context);
        pImagesRow.setOrientation(LinearLayout.HORIZONTAL);
        pHsv.addView(pImagesRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, imageH));
        frame.addView(pHsv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // points de pagination (visibles seulement s'il y a plusieurs images)
        pDotsRow = new LinearLayout(context);
        pDotsRow.setOrientation(LinearLayout.HORIZONTAL);
        pDotsRow.setGravity(Gravity.CENTER);
        pDotsRow.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.argb(0x66, 0x1A, 0x1A, 0x1B));
        pill.setCornerRadius(dp(12));
        pDotsRow.setBackground(pill);
        pDotsRow.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(10);
        frame.addView(pDotsRow, dlp);
        return frame;
    }

    private void updateDotsFromScroll(int x) {
        if (screenW <= 0) return;
        int count = pDotsRow == null ? 0 : pDotsRow.getChildCount();
        if (count <= 1) return;
        int page = Math.max(0, Math.min(count - 1, Math.round(x / (float) screenW)));
        if (page != pPage) {
            pPage = page;
            updateDots(page);
        }
    }

    private void updateDots(int page) {
        if (pDotsRow == null) return;
        for (int i = 0; i < pDotsRow.getChildCount(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == page ? Color.WHITE : Color.argb(0x80, 255, 255, 255));
            pDotsRow.getChildAt(i).setBackground(g);
        }
    }

    // Recrée les pages d'images si la liste a changé (les images déjà chargées sont réutilisées)
    private void syncImages() {
        if (pImagesRow == null || pDotsRow == null) return;
        ArrayList<String> urls = collectImages(pData);
        if (urls.equals(pImageUrls) && pImagesRow.getChildCount() > 0) return;
        pImageUrls.clear();
        pImageUrls.addAll(urls);
        final int token = ++pImageToken;
        int imageH = (int) (screenW * 0.605f);

        pImagesRow.removeAllViews();
        pDotsRow.removeAllViews();
        if (urls.isEmpty()) {
            View ph = new View(context);
            ph.setBackgroundColor(Color.parseColor("#F5F5F5"));
            ph.setLayoutParams(new LinearLayout.LayoutParams(screenW, imageH));
            pImagesRow.addView(ph);
        }
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            ImageView iv = new ImageView(context);
            iv.setLayoutParams(new LinearLayout.LayoutParams(screenW, imageH));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Color.parseColor("#F5F5F5"));
            Bitmap cached = pBitmaps.get(url);
            if (cached != null) iv.setImageBitmap(cached);
            else loadProductImage(url, iv, token);
            pImagesRow.addView(iv);

            View dot = new View(context);
            LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(6), dp(6));
            dp1.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(dp1);
            pDotsRow.addView(dot);
        }
        pPage = 0;
        updateDots(0);
        pDotsRow.setVisibility(urls.size() > 1 ? View.VISIBLE : View.GONE);
        if (pHsv != null) pHsv.scrollTo(0, 0);
    }

    private void loadProductImage(final String url, final ImageView iv, final int token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = loadBitmap(url, screenW);
                    if (bmp == null) {
                        fail("Image produit introuvable : " + url);
                        return;
                    }
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            pBitmaps.put(url, bmp);
                            if (token == pImageToken) iv.setImageBitmap(bmp);
                        }
                    });
                } catch (Exception e) {
                    fail("Image produit : " + e.getMessage());
                }
            }
        }).start();
    }

    private void loadSellerLogo(final String src) {
        final int token = ++pLogoToken;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = loadBitmap(src, dp(200));
                    if (bmp == null) return;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (token == pLogoToken && pSellerImg != null) pSellerImg.setImageBitmap(bmp);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // Icône Phosphor si un caractère est donné, sinon symbole simple (ou rien)
    private TextView prodIcon(String ch, String fallback, int sp, int color) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        applyIcon(t, ch, fallback);
        boolean empty = (ch == null || ch.isEmpty()) && fallback.isEmpty();
        t.setVisibility(empty ? View.GONE : View.VISIBLE);
        return t;
    }

    private void refreshProductIcons() {
        if (pStockIconTv != null) {
            applyIcon(pStockIconTv, pIconStock, "");
            pStockIconTv.setVisibility(pIconStock.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (pDeliveryIconTv != null) {
            applyIcon(pDeliveryIconTv, pIconDelivery, "");
            pDeliveryIconTv.setVisibility(pIconDelivery.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (pQtyMinus != null) applyIcon(pQtyMinus, pIconMinus, "−");
        if (pQtyPlus != null) applyIcon(pQtyPlus, pIconPlus, "+");
    }

    private void setStrike(TextView t, boolean on) {
        if (t == null) return;
        int f = t.getPaintFlags();
        t.setPaintFlags(on ? (f | Paint.STRIKE_THRU_TEXT_FLAG) : (f & ~Paint.STRIKE_THRU_TEXT_FLAG));
    }

    private void updateQuantityView() {
        if (pQtyText != null) pQtyText.setText(String.valueOf(pQuantity));
    }

    private void addDescLine(String s) {
        TextView t = text(s, 13, Color.argb(0xBC, 0x01, 0x01, 0x01));
        t.setPadding(0, dp(6), 0, 0);
        pDescBlock.addView(t);
    }

    private void removeProductHolder() {
        if (pHolder != null && pHolder.getParent() instanceof ViewGroup) {
            ((ViewGroup) pHolder.getParent()).removeView(pHolder);
        }
        pHolder = null;
        pHsv = null;
        pImagesRow = null;
        pDotsRow = null;
        pTitleTv = null;
        pPriceCurTv = null;
        pPriceAmountTv = null;
        pStockText = null;
        pStockIconTv = null;
        pDeliveryText = null;
        pDeliveryIconTv = null;
        pQtyText = null;
        pQtyMinus = null;
        pQtyPlus = null;
        pDescBlock = null;
        pSellerImg = null;
    }

    // Met toute la page à jour à partir des données reçues
    private void renderProduct() {
        if (pHolder == null) return;

        pTitleTv.setText(pick(pData, "title", "name", "titre"));
        String amount = pPriceAmount();
        pPriceCurTv.setText(amount.isEmpty() ? "" : pPriceCur());
        pPriceAmountTv.setText(amount);

        syncImages();

        // stock et livraison : texte barré s'il n'y en a pas
        int[] st = pStock();
        setStrike(pStockText, st[0] == 1 && st[1] <= 0);
        boolean dKnown = pData.has("deliveryIncluded") || pData.has("delivery_included");
        boolean dOn = dKnown && pBool(pData.opt(pData.has("deliveryIncluded") ? "deliveryIncluded" : "delivery_included"));
        setStrike(pDeliveryText, dKnown && !dOn);

        // description puis état, couleur, taille
        pDescBlock.removeAllViews();
        String desc = pick(pData, "description", "desc");
        String cond = pick(pData, "condition", "etat", "état", "state");
        String color = pick(pData, "color", "couleur");
        String size = pick(pData, "size", "taille");
        boolean any = false;
        if (!desc.isEmpty()) {
            addDescLine(desc);
            any = true;
        }
        if (!cond.isEmpty()) {
            addDescLine("État : " + cond);
            any = true;
        }
        if (!color.isEmpty()) {
            addDescLine("Couleur : " + color);
            any = true;
        }
        if (!size.isEmpty()) {
            addDescLine("Taille : " + size);
            any = true;
        }
        if (!any && !pDetailsReceived) addDescLine("Description");
        pDescBlock.setVisibility(any || !pDetailsReceived ? View.VISIBLE : View.GONE);

        clampQuantity();
        updateQuantityView();

        // logo du vendeur
        String logo = pSellerLogo();
        if (logo.isEmpty()) logo = pDefaultSellerLogo;
        if (!logo.equals(pLoadedLogo)) {
            pLoadedLogo = logo;
            if (!logo.isEmpty()) loadSellerLogo(logo);
        }
    }
}
