package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
        description = "Extension Manatest pour la gestion des bulles de chat et de la grille produit 2x2.",
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

    // =========================================================================
    // POLICE PERSONNALISÉE
    // =========================================================================

    @SimpleFunction(description = "Charge une police personnalisée (.ttf ou .otf) depuis les Assets ou un chemin du téléphone.")
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
    // 1. BULLE DE CHAT DYNAMIQUE (Message + Heure)
    // =========================================================================

    @SimpleFunction(description = "Ajoute une bulle de chat dynamique qui s'élargit avec le texte et intègre l'heure sous le message.")
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
                    ViewGroup parentLayout = (ViewGroup) chatContainer.getView();

                    // Bulle verticale pour empiler message et heure
                    LinearLayout bubble = new LinearLayout(context);
                    bubble.setOrientation(LinearLayout.VERTICAL);
                    bubble.setPadding(28, 20, 28, 16);

                    // Arrière-plan arrondi de la bulle
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setColor(bubbleColor);
                    bg.setCornerRadius(32f);
                    bubble.setBackground(bg);

                    // Texte du message
                    TextView msgTv = new TextView(context);
                    msgTv.setText(messageText);
                    msgTv.setTextColor(textColor);
                    msgTv.setTextSize(15);
                    if (customTypeface != null) {
                        msgTv.setTypeface(customTypeface);
                    }

                    // Limite la largeur maximale à 78% de l'écran
                    int maxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.78);
                    msgTv.setMaxWidth(maxWidth);
                    bubble.addView(msgTv);

                    // Texte de l'heure (affiché en bas sous le message)
                    TextView timeTv = new TextView(context);
                    timeTv.setText(timeText);
                    timeTv.setTextColor(Color.argb(170, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
                    timeTv.setTextSize(10);
                    timeTv.setGravity(Gravity.END);
                    timeTv.setPadding(0, 6, 0, 0);
                    if (customTypeface != null) {
                        timeTv.setTypeface(customTypeface);
                    }
                    bubble.addView(timeTv);

                    // Configuration de l'alignement (Moi = Droite, Autre = Gauche)
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.gravity = isMe ? Gravity.END : Gravity.START;
                    params.setMargins(16, 10, 16, 10);
                    bubble.setLayoutParams(params);

                    parentLayout.addView(bubble);

                    // Défilement automatique vers le bas si le conteneur est un ScrollView
                    if (parentLayout.getParent() instanceof ScrollView) {
                        final ScrollView scrollView = (ScrollView) parentLayout.getParent();
                        scrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                scrollView.fullScroll(View.FOCUS_DOWN);
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
    // 2. GRILLE PRODUIT 2x2 (30% Hauteur / ~50% Largeur)
    // =========================================================================

    @SimpleFunction(description = "Génère une grille 2x2 dynamique basée sur les critères visuels (30% hauteur, 50% largeur).")
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
                                ViewGroup mainLayout = (ViewGroup) scrollContainer.getView();
                                mainLayout.removeAllViews();

                                int cardWidth = (int) (screenWidth * 0.46);  // ~50% largeur avec marges
                                int cardHeight = (int) (screenHeight * 0.30); // 30% hauteur de l'écran

                                LinearLayout currentRow = null;

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    final String uid = item.optString("uid", "");
                                    String titleStr = item.optString("title", "");
                                    String priceStr = item.optString("price", "");

                                    // Ligne de 2 éléments
                                    if (i % 2 == 0) {
                                        currentRow = new LinearLayout(context);
                                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                                        currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        );
                                        rowParams.setMargins(0, 6, 0, 6);
                                        currentRow.setLayoutParams(rowParams);
                                        mainLayout.addView(currentRow);
                                    }

                                    // Carte Produit (30% x 50%)
                                    CardView card = new CardView(context);
                                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
                                    cardParams.setMargins(8, 8, 8, 8);
                                    card.setLayoutParams(cardParams);
                                    card.setRadius(16f);
                                    card.setCardElevation(4f);

                                    // Conteneur interne
                                    LinearLayout inner = new LinearLayout(context);
                                    inner.setOrientation(LinearLayout.VERTICAL);
                                    inner.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT
                                    ));

                                    // 1. Image
                                    ImageView img = new ImageView(context);
                                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            0, 1.0f
                                    );
                                    img.setLayoutParams(imgParams);
                                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    img.setBackgroundColor(Color.parseColor("#EEEEEE"));
                                    inner.addView(img);

                                    // 2. Titre produit
                                    TextView titleTv = new TextView(context);
                                    titleTv.setText(titleStr);
                                    titleTv.setTextColor(Color.BLACK);
                                    titleTv.setTextSize(14);
                                    titleTv.setPadding(12, 6, 12, 0);
                                    if (customTypeface != null) {
                                        titleTv.setTypeface(customTypeface);
                                    }
                                    inner.addView(titleTv);

                                    // 3. Prix produit
                                    TextView priceTv = new TextView(context);
                                    priceTv.setText(priceStr);
                                    priceTv.setTextColor(Color.BLACK);
                                    priceTv.setTextSize(13);
                                    priceTv.setTypeface(null, Typeface.BOLD);
                                    priceTv.setPadding(12, 2, 12, 10);
                                    if (customTypeface != null) {
                                        priceTv.setTypeface(customTypeface, Typeface.BOLD);
                                    }
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

