package com.manatest.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.google.appinventor.components.runtime.util.MediaUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@DesignerComponent(
        version = 1,
        description = "Extension Manatest pour la gestion des bulles de chat dynamiques avec avatar et grille produit 2x2 avec support URL.",
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

    private ViewGroup getRealLayout(AndroidViewComponent component) {
        View view = component.getView();
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof ViewGroup) {
                return (ViewGroup) vg.getChildAt(0);
            }
            return vg;
        }
        return null;
    }

    // Charger les images (URLs Serveur http/https ou Assets locaux)
    private void loadImageAsync(final ImageView imageView, final String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                        URL url = new URL(imagePath);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setDoInput(true);
                        conn.connect();
                        InputStream input = conn.getInputStream();
                        bmp = BitmapFactory.decodeStream(input);
                    } else {
                        try {
                            InputStream is = context.getAssets().open(imagePath);
                            bmp = BitmapFactory.decodeStream(is);
                        } catch (Exception e) {
                            bmp = MediaUtil.getBitmapDrawable(form, imagePath).getBitmap();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final Bitmap finalBmp = bmp;
                if (finalBmp != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(finalBmp);
                        }
                    });
                }
            }
        });
    }

    // =========================================================================
    // POLICE PERSONNALISÉE
    // =========================================================================

    @SimpleFunction(description = "Charge une police personnalisée (.ttf ou .otf).")
    public void LoadCustomFont(String fontPath) {
        try {
            if (fontPath.startsWith("/")) {
                customTypeface = Typeface.createFromFile(new java.io.File(fontPath));
            } else {
                customTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            customTypeface = Typeface.DEFAULT;
        }
    }

    // =========================================================================
    // 1. BULLE DE CHAT DYNAMIQUE (Largeur fixe corrigée + Avatar 6%/15% cliquable)
    // =========================================================================

    @SimpleFunction(description = "Ajoute une bulle de chat avec avatar cliquable et horodatage.")
    public void AddChatBubble(
            final AndroidViewComponent chatContainer,
            final String messageText,
            final String timeText,
            final String avatarUrl,
            final boolean isMe,
            final int bubbleColor,
            final int textColor) {

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup targetLayout = getRealLayout(chatContainer);
                    if (targetLayout == null) return;

                    int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

                    // Rangee principale (Ligne d'un message)
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(isMe ? Gravity.END : Gravity.START);
                    
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    rowParams.setMargins(12, 10, 12, 10);
                    row.setLayoutParams(rowParams);

                    // --- AVATAR (Hauteur 6%, Largeur 15%, Radius 30) ---
                    CardView avatarCard = new CardView(context);
                    int avatarWidth = (int) (screenWidth * 0.15);
                    int avatarHeight = (int) (screenHeight * 0.06);

                    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarWidth, avatarHeight);
                    avatarParams.gravity = Gravity.BOTTOM;
                    avatarParams.setMargins(8, 0, 8, 0);
                    avatarCard.setLayoutParams(avatarParams);
                    avatarCard.setRadius(30f);
                    avatarCard.setCardElevation(2f);

                    ImageView avatarImg = new ImageView(context);
                    avatarImg.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    ));
                    avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    avatarImg.setBackgroundColor(Color.parseColor("#CCCCCC"));

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        loadImageAsync(avatarImg, avatarUrl);
                    }
                    avatarCard.addView(avatarImg);

                    // Clic sur l'Avatar
                    avatarCard.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            OnAvatarClick(isMe);
                        }
                    });

                    // --- BULLE DE TEXTE ---
                    LinearLayout bubble = new LinearLayout(context);
                    bubble.setOrientation(LinearLayout.VERTICAL);
                    bubble.setPadding(32, 20, 32, 16);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setColor(bubbleColor);
                    bg.setCornerRadius(30f); // Radius 30
                    bubble.setBackground(bg);

                    // Fixation de la largeur max (70% de l'écran pour éviter que le texte soit écrasé)
                    int maxBubbleWidth = (int) (screenWidth * 0.70);
                    
                    LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    bubble.setLayoutParams(bubbleParams);

                    // Message
                    TextView msgTv = new TextView(context);
                    msgTv.setText(messageText);
                    msgTv.setTextColor(textColor);
                    msgTv.setTextSize(15);
                    msgTv.setMaxWidth(maxBubbleWidth);
                    if (customTypeface != null) msgTv.setTypeface(customTypeface);
                    bubble.addView(msgTv);

                    // Heure / Horodatage
                    TextView timeTv = new TextView(context);
                    timeTv.setText(timeText);
                    timeTv.setTextColor(Color.argb(180, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
                    timeTv.setTextSize(10);
                    
                    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    timeParams.gravity = Gravity.END;
                    timeParams.setMargins(0, 6, 0, 0);
                    timeTv.setLayoutParams(timeParams);

                    if (customTypeface != null) timeTv.setTypeface(customTypeface);
                    bubble.addView(timeTv);

                    // Assemblage selon l'expéditeur (Autre = Avatar a gauche, Moi = Avatar a droite)
                    if (isMe) {
                        row.addView(bubble);
                        row.addView(avatarCard);
                    } else {
                        row.addView(avatarCard);
                        row.addView(bubble);
                    }

                    targetLayout.addView(row);

                    // Auto-scroll bas de page
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
    // 2. GRILLE PRODUIT 2x2 (Design Épuré + Fond Blanc + Textes Noirs)
    // =========================================================================

    @SimpleFunction(description = "Génère la grille 2x2 épurée avec fond blanc et chargement d'images serveur.")
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

                                int cardWidth = (int) (screenWidth * 0.44);
                                int cardHeight = (int) (screenHeight * 0.28);

                                LinearLayout currentRow = null;

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    
                                    // UID dynamique provenant de la base de données
                                    final String uid = item.optString("uid", String.valueOf(i));
                                    String imageStr = item.optString("image", "");
                                    String titleStr = item.optString("title", "");
                                    String priceStr = item.optString("price", "");

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

                                    // Carte du produit avec Fond Blanc
                                    CardView card = new CardView(context);
                                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
                                    cardParams.setMargins(10, 8, 10, 8);
                                    card.setLayoutParams(cardParams);
                                    card.setRadius(20f);
                                    card.setCardBackgroundColor(Color.WHITE); // Suppression du fond gris
                                    card.setCardElevation(4f);

                                    // Conteneur interne
                                    LinearLayout inner = new LinearLayout(context);
                                    inner.setOrientation(LinearLayout.VERTICAL);
                                    inner.setBackgroundColor(Color.WHITE); // Fond blanc pur
                                    inner.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT
                                    ));

                                    // 1. Image du produit
                                    ImageView img = new ImageView(context);
                                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            0, 1.0f
                                    );
                                    img.setLayoutParams(imgParams);
                                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    img.setBackgroundColor(Color.parseColor("#F5F5F5"));
                                    
                                    loadImageAsync(img, imageStr);
                                    inner.addView(img);

                                    // 2. Titre (Texte Noir Uniquement)
                                    TextView titleTv = new TextView(context);
                                    titleTv.setText(titleStr);
                                    titleTv.setTextColor(Color.BLACK);
                                    titleTv.setTextSize(13);
                                    titleTv.setMaxLines(2);
                                    titleTv.setPadding(14, 8, 14, 0);
                                    if (customTypeface != null) titleTv.setTypeface(customTypeface);
                                    inner.addView(titleTv);

                                    // 3. Prix (Texte Noir et GRAS)
                                    TextView priceTv = new TextView(context);
                                    priceTv.setText(priceStr);
                                    priceTv.setTextColor(Color.BLACK); // Noir pur
                                    priceTv.setTextSize(14);
                                    priceTv.setTypeface(null, Typeface.BOLD); // En Gras
                                    priceTv.setPadding(14, 2, 14, 12);
                                    if (customTypeface != null) priceTv.setTypeface(customTypeface, Typeface.BOLD);
                                    inner.addView(priceTv);

                                    card.addView(inner);

                                    // Événement Clic sur la Carte
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

    @SimpleEvent(description = "Déclenché lors du clic sur l'avatar du message.")
    public void OnAvatarClick(boolean isMe) {
        EventDispatcher.dispatchEvent(this, "OnAvatarClick", isMe);
    }
}
