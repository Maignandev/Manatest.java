package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@DesignerComponent(
        version = 6,
        description = "Manatest - Formulaire « Ajouter un produit » construit en code : lignes cliquables, valeurs, validation, brouillon, JSON pour le serveur.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent {

    private static final String PREFS_NAME = "ManatestProductForm";
    private static final String PREF_DRAFT = "draft";

    private static final int MAX_TITLE = 80;
    private static final int MAX_DESCRIPTION = 1000;
    private static final int MAX_SHORT = 30;

    private final Context context;
    private final Activity activity;

    // Vraies valeurs (séparées de l'affichage) : clé du champ -> valeur
    private final Map<String, String> values = new LinkedHashMap<String, String>();

    private ViewGroup formContainer;
    private Typeface iconFont;
    private String iconPlus = "";
    private String iconStore = "";
    private String iconTruck = "";
    private String invalidKey = "";

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = (Activity) container.$context();
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

    @SimpleEvent(description = "L'utilisateur a touché une ligne du formulaire. fieldKey : images, title, description, category, price, variants, condition, color, size, stock, address ou delivery. Ouvre le panneau de saisie correspondant.")
    public void OnProductFormFieldClick(String fieldKey) {
        EventDispatcher.dispatchEvent(this, "OnProductFormFieldClick", fieldKey);
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

    @SimpleFunction(description = "Construit le formulaire « Ajouter un produit » dans l'arrangement donné. fontPath : police Phosphor (ex: Phosphor-Bold.ttf). plusChar : icône « + » des lignes vides. storeChar : icône de l'adresse. truckChar : icône de la livraison. Laisse un caractère vide pour ne pas afficher cette icône.")
    public void BuildProductForm(
            final AndroidViewComponent container,
            final String fontPath,
            final String plusChar,
            final String storeChar,
            final String truckChar) {
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
                    iconPlus = plusChar == null ? "" : plusChar;
                    iconStore = storeChar == null ? "" : storeChar;
                    iconTruck = truckChar == null ? "" : truckChar;
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

                    addImagesBlock();
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
        t.setTextSize(sp);
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
        return has(key) ? col("#1A1A1B") : col("#6E6E73");
    }

    // ---- bloc photo ----

    private int imageCount() {
        try {
            return new JSONArray(get("images")).length();
        } catch (Exception e) {
            return 0;
        }
    }

    private void addImagesBlock() {
        LinearLayout block = rowBase(16, 16, 16, 16, true);

        LinearLayout tile = new LinearLayout(context);
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(150), dp(150)));
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setColor(Color.WHITE);
        tileBg.setStroke(dp(1), col("#EEEEEE"));
        tileBg.setCornerRadius(dp(20));
        tile.setBackground(tileBg);
        block.addView(tile);

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, dp(150), 1f));

        TextView circle = new TextView(context);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        circle.setGravity(Gravity.CENTER);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(col("#F5F5F5"));
        circle.setBackground(circleBg);
        if (iconFont != null && !iconPlus.isEmpty()) {
            circle.setText(iconPlus);
            circle.setTypeface(iconFont);
            circle.setTextSize(24);
            circle.setTextColor(col("#1A1A1B"));
            circle.setIncludeFontPadding(false);
        }
        right.addView(circle);

        int n = imageCount();
        String label = n == 0 ? "Ajouter des images" : (n + (n > 1 ? " images" : " image"));
        TextView t = text(label, 16, col("#6E6E73"), false, 2);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, dp(10), 0, 0);
        t.setLayoutParams(tp);
        right.addView(t);

        block.addView(right);
        clickable(block, "images");
        formContainer.addView(block);
    }

    // ---- lignes ----

    private void addTitleRow() {
        LinearLayout row = rowBase(22, 22, 16, 22, true);
        boolean filled = has("title");
        TextView t = text(filled ? get("title") : "Titre du produit", 26,
                valueColor("title"), false, 1);
        weight(t);
        row.addView(t);
        clickable(row, "title");
        formContainer.addView(row);
    }

    private void addIconTextRow(String key, String iconChar, String placeholder, int maxLines) {
        LinearLayout row = rowBase(16, 22, 16, 22, true);
        TextView ic = icon(iconChar, 22, col("#1A1A1B"), 14);
        if (ic != null) {
            row.addView(ic);
        } else {
            row.setPadding(dp(22), dp(22), dp(16), dp(22));
        }
        TextView t = text(has(key) ? get(key) : placeholder, 20, valueColor(key), false, maxLines);
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
        head.addView(text("État et variantes", 24, col("#1A1A1B"), true, 1));
        formContainer.addView(head);

        boolean any = has("condition") || has("color") || has("size");
        if (!any) {
            LinearLayout row = rowBase(16, 16, 16, 24, true);
            TextView ic = icon(iconPlus, 22, col("#1A1A1B"), 14);
            if (ic != null) {
                row.addView(ic);
            } else {
                row.setPadding(dp(22), dp(16), dp(16), dp(24));
            }
            TextView t = text("Plus d'options (neuf , couleur...", 20, col("#6E6E73"), false, 1);
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
            TextView label = text(lines[i][1], 20, col("#1A1A1B"), false, 1);
            weight(label);
            row.addView(label);
            TextView value = text(has(key) ? get(key) : "—", 20, valueColor(key), false, 1);
            value.setGravity(Gravity.END);
            value.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(value);
            clickable(row, key);
            formContainer.addView(row);
        }
    }

    private void addStockSection() {
        LinearLayout head = rowBase(22, 18, 22, 6, true);
        TextView title = text("Stock", 24, col("#1A1A1B"), true, 1);
        weight(title);
        head.addView(title);
        head.addView(text("Modifier", 20, col("#3949AB"), false, 1));
        clickable(head, "stock");
        formContainer.addView(head);

        LinearLayout row = rowBase(22, 6, 22, 18, true);
        TextView label = text("Disponible", 22, col("#4A4A4F"), false, 1);
        weight(label);
        row.addView(label);

        TextView box = text(has("stock") ? get("stock") : "0", 22, valueColor("stock"), false, 1);
        box.setGravity(Gravity.CENTER);
        box.setMinWidth(dp(110));
        box.setMinHeight(dp(42));
        box.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(col("#F5F5F5"));
        boxBg.setCornerRadius(dp(12));
        box.setBackground(boxBg);
        row.addView(box);
        clickable(row, "stock");
        formContainer.addView(row);
    }

    private void addAddressSection() {
        LinearLayout row = rowBase(22, 18, 22, 18, false);
        row.setGravity(Gravity.START);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic = icon(iconStore, 26, col("#1A1A1B"), 14);
        if (ic != null) {
            head.addView(ic);
        }
        head.addView(text("Adresse", 26, col("#1A1A1B"), true, 1));
        row.addView(head);

        TextView value = text(has("address") ? get("address") : "Ajouter une adresse", 20,
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
        TextView ic = icon(iconTruck, 26, col("#1A1A1B"), 14);
        if (ic != null) {
            row.addView(ic);
        }
        TextView label = text("Livraison", 24, col("#1A1A1B"), false, 1);
        weight(label);
        row.addView(label);
        if (has("delivery")) {
            TextView v = text(get("delivery"), 18, valueColor("delivery"), false, 1);
            row.addView(v);
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
        saveDraft();
        render();
    }

    @SimpleFunction(description = "Retourne la vraie valeur entière d'un champ (pas le texte coupé de l'écran). Sert à préremplir un panneau de saisie.")
    public String GetProductFormValue(String fieldKey) {
        return fieldKey == null ? "" : get(fieldKey.trim());
    }

    @SimpleFunction(description = "Retourne le produit complet en JSON, prêt à envoyer au serveur : title, description, category, category_id, price (nombre), currency (HTG), condition, color, size, stock, address, delivery, deliveryIncluded, image, image2 à image5.")
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
            o.put("stock", has("stock") ? Integer.parseInt(get("stock")) : 0);
            o.put("address", get("address"));
            o.put("delivery", get("delivery"));
            o.put("deliveryIncluded", isTruthy(get("delivery")));
            if (has("images")) {
                JSONArray a = new JSONArray(get("images"));
                String[] names = {"image", "image2", "image3", "image4", "image5"};
                for (int i = 0; i < a.length() && i < names.length; i++) {
                    o.put(names[i], a.optString(i, ""));
                }
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

    @SimpleFunction(description = "Remet dans le formulaire le brouillon sauvegardé dans le téléphone (chaque SetProductFormValue le met à jour). Retourne vrai si un brouillon a été restauré. À appeler après BuildProductForm.")
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
            render();
            return !values.isEmpty();
        } catch (Exception e) {
            OnError("RestoreProductFormDraft: " + e.getMessage());
            return false;
        }
    }

    @SimpleFunction(description = "Efface toutes les valeurs et le brouillon, et remet le formulaire à zéro (pour un nouveau produit).")
    public void ClearProductForm() {
        values.clear();
        invalidKey = "";
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(PREF_DRAFT).apply();
        } catch (Exception ignored) {
        }
        render();
    }
}
