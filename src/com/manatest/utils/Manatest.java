package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.util.AsynchUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

@DesignerComponent(
        version = 1,
        description = "Extension Manatest pour la gestion des bulles de chat dynamiques et de la grille produit 2x2 infinie.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent {

    private final Context context;
    private final Activity activity;
    private Typeface customTypeface = Typeface.DEFAULT;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = (Activity) container.$context();
    }

    // Helper pour récupérer le vrai conteneur de disposition dans Kodular
    private ViewGroup getRealLayout(AndroidViewComponent component) {
        View view = component.getView();
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            // Si c'est un ScrollView / FrameLayout Kodular, prendre son premier enfant
            if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof ViewGroup) {
                return (ViewGroup) vg.getChildAt(0);
            }
            return vg;
        }
        return null;
    }

    // =========================================================================
    // POLICE PERSONNALISÉE
    // =========================================================================

    @SimpleFunction(description = "Charge une police personnalisée (.ttf ou .otf).")
    public void LoadCustomFont(String fontPath) {
        try {
            if (fontPath.startsWith("/")) {
                customTypeface = Typeface.createFromFile(new File(fontPath));
            } else {
                customTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            customTypeface = Typeface.DEFAULT;
        }
    }

    // =========================================================================
    // 1. BULLE DE CHAT DYNAMIQUE (Fixe l'accumulation et l'alignement)
    // =========================================================================

    @SimpleFunction(description = "Ajoute une nouvelle bulle de chat indépendante.")
    public void AddChatBubble(
            final AndroidViewComponent chatContainer,
            final String messageText,
            final String timeText,
            final boolean isMe,
            final int bubbleColor,
            final int textColor) {

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup targetLayout = getRealLayout(chatContainer);
                    if (targetLayout == null) return;

                    // Ligne externe complète (Largeur écran) pour aligner la bulle à droite ou à gauche
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(isMe ? Gravity.END : Gravity.START);
                    
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    rowParams.setMargins(12, 8, 12, 8);
                    row.setLayoutParams(rowParams);

                    // Bulle verticale
                    LinearLayout bubble = new LinearLayout(context);
                    bubble.setOrientation(LinearLayout.VERTICAL);
                    bubble.setPadding(30, 20, 30, 16);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setColor(bubbleColor);
                    bg.setCornerRadius(32f);
                    bubble.setBackground(bg);

                    // Texte du Message
                    TextView msgTv = new TextView(context);
                    msgTv.setText(messageText);
                    msgTv.setTextColor(textColor);
                    msgTv.setTextSize(15);
                    if (customTypeface != null) msgTv.setTypeface(customTypeface);

                    int maxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.72);
                    msgTv.setMaxWidth(maxWidth);
                    bubble.addView(msgTv);

                    // Texte de l'Heure (Ancré à droite dans la bulle)
                    TextView timeTv = new TextView(context);
                    timeTv.setText(timeText);
                    timeTv.setTextColor(Color.argb(180, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
                    timeTv.setTextSize(10);
                    
                    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    timeParams.gravity = Gravity.END;
                    timeParams.setMargins(0, 8, 0, 0);
                    timeTv.setLayoutParams(timeParams);

                    if (customTypeface != null) timeTv.setTypeface(customTypeface);
                    bubble.addView(timeTv);

                    row.addView(bubble);
                    targetLayout.addView(row);

                    // Auto-Scroll vers le bas
                    View parentView = chatContainer.getView();
                    if (parentView instanceof ScrollView) {
                        final ScrollView sv = (ScrollView) parentView;
                        sv.post(new Runnable() {
                            @Override
                            public void run() {
                                sv.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // =========================================================================
    // 2. GRILLE PRODUIT 2x2 (Supporte un nombre infini de produits)
    // =========================================================================

    @SimpleFunction(description = "Génère une grille 2x2 dynamique pour TOUS les produits du JSON.")
    public void BuildProductGridFromJson(
            final AndroidViewComponent scrollContainer,
            final String jsonData) {

        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray array = new JSONArray(jsonData);
                    final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                    final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ViewGroup targetLayout = getRealLayout(scrollContainer);
                                if (targetLayout == null) return;

                                targetLayout.removeAllViews();

                                int cardWidth = (int) (screenWidth * 0.44);   // ~44% de largeur
                                int cardHeight = (int) (screenHeight * 0.28); // 28% de la hauteur de l'écran

                                LinearLayout currentRow = null;

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    final String uid = item.optString("uid", "");
                                    String titleStr = item.optString("title", "");
                                    String priceStr = item.optString("price", "");

                                    // Créer une nouvelle ligne tous les 2 produits
                                    if (i % 2 == 0) {
                                        currentRow = new LinearLayout(context);
                                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                                        currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        );
                                        rowParams.setMargins(0, 8, 0, 8);
                                        currentRow.setLayoutParams(rowParams);
                                        targetLayout.addView(currentRow);
                                    }

                                    // Carte du Produit
                                    CardView card = new CardView(context);
                                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
                                    cardParams.setMargins(10, 8, 10, 8);
                                    card.setLayoutParams(cardParams);
                                    card.setRadius(20f);
                                    card.setCardElevation(6f);

                                    // Layout Interne
                                    LinearLayout inner = new LinearLayout(context);
                                    inner.setOrientation(LinearLayout.VERTICAL);
                                    inner.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT
                                    ));

                                    // 1. Image Placeholder
                                    ImageView img = new ImageView(context);
                                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            0, 1.0f
                                    );
                                    img.setLayoutParams(imgParams);
                                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    img.setBackgroundColor(Color.parseColor("#E0E0E0"));
                                    inner.addView(img);

                                    // 2. Titre
                                    TextView titleTv = new TextView(context);
                                    titleTv.setText(titleStr);
                                    titleTv.setTextColor(Color.BLACK);
                                    titleTv.setTextSize(13);
                                    titleTv.setMaxLines(2);
                                    titleTv.setPadding(12, 8, 12, 0);
                                    if (customTypeface != null) titleTv.setTypeface(customTypeface);
                                    inner.addView(titleTv);

                                    // 3. Prix
                                    TextView priceTv = new TextView(context);
                                    priceTv.setText(priceStr);
                                    priceTv.setTextColor(Color.parseColor("#1B5E20"));
                                    priceTv.setTextSize(14);
                                    priceTv.setTypeface(null, Typeface.BOLD);
                                    priceTv.setPadding(12, 2, 12, 12);
                                    if (customTypeface != null) priceTv.setTypeface(customTypeface, Typeface.BOLD);
                                    inner.addView(priceTv);

                                    card.addView(inner);

                                    // Clic sur la carte
                                    card.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            OnProductCardClick(uid);
                                        }
                                    });

                                    if (currentRow != null) {
                                        currentRow.addView(card);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // =========================================================================
    // ÉVÉNEMENTS (EVENTS) KODULAR
    // =========================================================================

    @SimpleEvent(description = "Déclenché lors du clic sur une carte produit.")
    public void OnProductCardClick(String productUid) {
        EventDispatcher.dispatchEvent(this, "OnProductCardClick", productUid);
    }
}

