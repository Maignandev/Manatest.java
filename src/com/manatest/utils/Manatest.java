package com.manatest.utils;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

@DesignerComponent(
        version = 5,
        description = "Manatest - Rendu haute qualité des icônes.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent {

    private final Activity activity;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.activity = (Activity) container.$context();
    }

    // =========================================================================
    // UTILITAIRE INTERNE : applique le filtrage/anti-alias/dithering
    // sur le Drawable réel de l'ImageView (BitmapDrawable uniquement).
    // =========================================================================

    private void applyHighQualityRendering(ImageView imageView) {

        Drawable d = imageView.getDrawable();

        if (d instanceof BitmapDrawable) {

            BitmapDrawable bd = (BitmapDrawable) d;

            bd.setFilterBitmap(true);
            bd.setAntiAlias(true);
            bd.setDither(true);

            imageView.invalidate();
        }
    }

    // =========================================================================
    // 3. IMAGE / ICÔNE HAUTE QUALITÉ
    // =========================================================================

    @SimpleFunction(description = "Active le rendu haute qualité sur un composant Image.")
    public void SetImageHighQuality(
            final AndroidViewComponent imageComponent) {

        if (imageComponent == null) return;

        final View view = imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        applyHighQualityRendering((ImageView) view);
                    }
                }
        );
    }

    // =========================================================================
    // 4. ICÔNE AVEC TAILLE CONTRÔLÉE
    // =========================================================================

    @SimpleFunction(description = "Configure une icône avec un rendu propre et conserve ses proportions.")
    public void SetIconRendering(
            final AndroidViewComponent imageComponent,
            final boolean keepRatio) {

        if (imageComponent == null) return;

        final View view =
                imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        ImageView imageView =
                                (ImageView) view;

                        applyHighQualityRendering(imageView);

                        if (keepRatio) {

                            imageView.setScaleType(
                                    ImageView.ScaleType.CENTER_INSIDE
                            );

                        } else {

                            imageView.setScaleType(
                                    ImageView.ScaleType.FIT_CENTER
                            );
                        }
                    }
                }
        );
    }

    // =========================================================================
    // 5. RENDU ICÔNE SANS DÉFORMATION
    // =========================================================================

    @SimpleFunction(description = "Configure une image pour qu'elle reste proportionnelle sans être étirée.")
    public void SetIconNoDeformation(
            final AndroidViewComponent imageComponent) {

        if (imageComponent == null) return;

        final View view =
                imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        ImageView imageView =
                                (ImageView) view;

                        applyHighQualityRendering(imageView);

                        imageView.setScaleType(
                                ImageView.ScaleType.CENTER_INSIDE
                        );
                    }
                }
        );
    }

    // =========================================================================
    // 6. RENDU ICÔNE POUR REMPLIR LA ZONE SANS DÉFORMATION
    // =========================================================================

    @SimpleFunction(description = "Agrandit une icône pour remplir son ImageView sans déformer ses proportions.")
    public void SetIconCenterCrop(
            final AndroidViewComponent imageComponent) {

        if (imageComponent == null) return;

        final View view =
                imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        ImageView imageView =
                                (ImageView) view;

                        applyHighQualityRendering(imageView);

                        imageView.setScaleType(
                                ImageView.ScaleType.CENTER_CROP
                        );
                    }
                }
        );
    }
}

