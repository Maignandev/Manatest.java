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
    // 1. CHAT BULLE DYNAMIQUE (Duplication, Scroll, Heure Fixe)
    // =========================================================================

    @SimpleFunction(description = "Ajoute une nouvelle bulle de chat indépendante avec heure fixe en bas à droite.")
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
                    View view = chatContainer.getView();
                    ViewGroup targetLayout = null;

                    // Supporte ScrollArrangement et VerticalArrangement
                    if (view instanceof ScrollView) {
                        ScrollView sv = (ScrollView) view;
                        if (sv.getChildCount() > 0) {
                            targetLayout = (ViewGroup) sv.getChildAt(0);
                        } else {
                            LinearLayout content = new LinearLayout(context);
                            content.setOrientation(LinearLayout.VERTICAL);
                            sv.addView(content, new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            ));
                            targetLayout = content;
                        }
                    } else if (view instanceof ViewGroup) {
                        targetLayout = (ViewGroup) view;
                    }

                    if (targetLayout == null) return;

                    // Bulle de chat principale (Conteneur du message + heure)
                    LinearLayout bubble = new LinearLayout(context);
                    bubble.setOrientation(LinearLayout.VERTICAL);
                    bubble.setPadding(28, 20, 28, 16);

                    // Arrière-plan de la bulle avec coins arrondis
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

                    // Limite la largeur maximale à 75% de l'écran
                    int maxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.75);
                    msgTv.setMaxWidth(maxWidth);
                    bubble.addView(msgTv);

                    // Texte de l'heure (Fixé en bas à droite de la bulle)
                    TextView timeTv = new TextView(context);
                    timeTv.setText(timeText);
                    timeTv.setTextColor(Color.argb(170, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
                    timeTv.setTextSize(10);
                    
                    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    timeParams.gravity = Gravity.END;
                    timeParams.setMargins(0, 6, 0, 0);
                    timeTv.setLayoutParams(timeParams);

                    if (customTypeface != null) {
                        timeTv.setTypeface(customTypeface);
                    }
                    bubble.addView(timeTv);

                    // Positionnement de la bulle sur la ligne (Gauche / Droite)
                    LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    bubbleParams.gravity = isMe ? Gravity.END : Gravity.START;
                    bubbleParams.setMargins(16, 8, 16, 8);
                    bubble.setLayoutParams(bubbleParams);

                    // Ajout de la bulle au conteneur principal
                    targetLayout.addView(bubble);

                    // Auto-Scroll vers le bas si le parent est un ScrollView
                    if (view instanceof ScrollView) {
                        final ScrollView sv = (ScrollView) view;
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
    // 2. GRILLE PRODUIT 2x2 INFINIE (Correction de l'affichage complet)
    // =========================================================================

    @SimpleFunction(description = "Génère une grille 2x2 dynamique pour l'ensemble des produits du JSON.")
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
                                View view = scrollContainer.getView();
                                ViewGroup targetLayout = null;

                                if (view instanceof ScrollView) {
                                    ScrollView sv = (ScrollView) view;
                                    sv.removeAllViews();
                                    LinearLayout verticalContent = new LinearLayout(context);
                                    verticalContent.setOrientation(LinearLayout.VERTICAL);
                                    sv.addView(verticalContent, new ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                    ));
                                    targetLayout = verticalContent;
                                } else if (view instanceof ViewGroup) {
                                    targetLayout = (ViewGroup) view;
                                    targetLayout.removeAllViews();
                                }

                                if (targetLayout == null) return;

                                int cardWidth = (int) (screenWidth * 0.45);   // ~50% de la largeur
                                int cardHeight = (int) (screenHeight * 0.30);  // 30% de la hauteur de l'écran

                                LinearLayout currentRow = null;

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    final String uid = item.optString("uid", "");
                                    String titleStr = item.optString("title", "");
                                    String priceStr = item.optString("price", "");

                                    // Création d'une nouvelle ligne horizontale tous les 2 produits
                                    if (i % 2 == 0) {
                                        currentRow = new LinearLayout(context);
                                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                                        currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        );
                                        rowParams.setMargins(0, 4, 0, 4);
                                        currentRow.setLayoutParams(rowParams);
                                        targetLayout.addView(currentRow);
                                    }

                                    // Carte du produit
                                    CardView card = new CardView(context);
                                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
                                    cardParams.setMargins(8, 8, 8, 8);
                                    card.setLayoutParams(cardParams);
                                    card.setRadius(16f);
                                    card.setCardElevation(4f);

                                    // Conteneur interne de la carte
                                    LinearLayout inner = new LinearLayout(context);
                                    inner.setOrientation(LinearLayout.VERTICAL);
                                    inner.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT
                                    ));

                                    // Image
                                    ImageView img = new ImageView(context);
                                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            0, 1.0f
                                    );
                                    img.setLayoutParams(imgParams);
                                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    img.setBackgroundColor(Color.parseColor("#EEEEEE"));
                                    inner.addView(img);

                                    // Titre
                                    TextView titleTv = new TextView(context);
                                    titleTv.setText(titleStr);
                                    titleTv.setTextColor(Color.BLACK);
                                    titleTv.setTextSize(14);
                                    titleTv.setPadding(12, 6, 12, 0);
                                    if (customTypeface != null) {
                                        titleTv.setTypeface(customTypeface);
                                    }
                                    inner.addView(titleTv);

                                    // Prix
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

                                    // Gestion du clic
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

