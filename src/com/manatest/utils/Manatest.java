package com.manatest.utils;

import android.app.Activity;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.view.View;
import android.widget.ImageView;

import com.caverock.androidsvg.SVG;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

import java.io.InputStream;

@DesignerComponent(
        version = 6,
        description = "Manatest - Test de rendu SVG vectoriel + rendu haute qualité des icônes bitmap.",
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
    // TEST — RENDU SVG VECTORIEL
    // =========================================================================
    //
    // Charge un fichier .svg présent dans les Media/assets du projet
    // et l'affiche dans un composant Image avec un rendu vectoriel
    // (net à n'importe quelle taille, contrairement à un bitmap).
    //
    // =========================================================================

    @SimpleFunction(description = "TEST — Charge un fichier SVG (depuis les Media du projet) et l'affiche dans un composant Image avec un rendu vectoriel net à toute taille.")
    public void SetIconFromSvg(
            final AndroidViewComponent imageComponent,
            final String svgAssetName) {

        if (imageComponent == null) {
            OnSvgError("SetIconFromSvg: composant invalide.");
            return;
        }

        final View view = imageComponent.getView();

        if (!(view instanceof ImageView)) {
            OnSvgError("SetIconFromSvg: le composant n'est pas une Image.");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                InputStream input = null;

                try {

                    input = activity.getAssets().open(svgAssetName);

                    SVG svg = SVG.getFromInputStream(input);

                    Picture picture = svg.renderToPicture();

                    PictureDrawable drawable = new PictureDrawable(picture);

                    ImageView imageView = (ImageView) view;

                    // Obligatoire : un PictureDrawable ne s'affiche pas
                    // correctement avec l'accélération matérielle activée.
                    imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                    imageView.setImageDrawable(drawable);

                    OnSvgLoaded(svgAssetName);

                } catch (Exception e) {

                    OnSvgError("SetIconFromSvg: " + e.getMessage());

                } finally {

                    if (input != null) {
                        try {
                            input.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
    }

    // =========================================================================
    // ÉVÉNEMENTS DE TEST
    // =========================================================================

    @com.google.appinventor.components.annotations.SimpleEvent(description = "TEST — Déclenché quand le SVG a été chargé et affiché avec succès.")
    public void OnSvgLoaded(String svgAssetName) {
        com.google.appinventor.components.runtime.EventDispatcher.dispatchEvent(
                this, "OnSvgLoaded", svgAssetName
        );
    }

    @com.google.appinventor.components.annotations.SimpleEvent(description = "TEST — Déclenché en cas d'erreur de chargement du SVG.")
    public void OnSvgError(String message) {
        com.google.appinventor.components.runtime.EventDispatcher.dispatchEvent(
                this, "OnSvgError", message
        );
    }

    // =========================================================================
    // 3. IMAGE / ICÔNE HAUTE QUALITÉ (bitmap classique — conservé pour comparaison)
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

    @SimpleFunction(description = "Active le rendu haute qualité sur un composant Image (bitmap classique).")
    public void SetImageHighQuality(
            final AndroidViewComponent imageComponent) {

        if (imageComponent == null) return;

        final View view = imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyHighQualityRendering((ImageView) view);
            }
        });
    }

    @SimpleFunction(description = "Configure une image pour qu'elle reste proportionnelle sans être étirée (bitmap classique).")
    public void SetIconNoDeformation(
            final AndroidViewComponent imageComponent) {

        if (imageComponent == null) return;

        final View view = imageComponent.getView();

        if (!(view instanceof ImageView)) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ImageView imageView = (ImageView) view;

                applyHighQualityRendering(imageView);

                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }
        });
    }
}

