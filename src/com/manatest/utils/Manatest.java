package com.manatest.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

@DesignerComponent(
        version = 1,
        description = "Manatest - Décale la zone de saisie au-dessus du clavier et conserve le design initial.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent {

    private final Activity activity;
    private int initialHeightPx = -1;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.activity = (Activity) container.$context();
    }

    @SimpleFunction(description = "Attache la zone de saisie au-dessus du clavier et gère la hauteur dynamique.")
    public void AttachFloatingInputWithDynamicHeight(
            final Object inputContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(inputContainer instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) inputContainer).getView();
        if (containerView == null) return;

        final View rootView = activity.getWindow().getDecorView().getRootView();

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Capture la hauteur exacte de ton design (les 8%) la toute première fois
                if (initialHeightPx == -1 && containerView.getHeight() > 0) {
                    initialHeightPx = containerView.getHeight();
                    containerView.setMinimumHeight(initialHeightPx);
                }

                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                if (params != null && initialHeightPx > 0) {
                    // Hauteur flexible mais qui ne descend JAMAIS en dessous de ton design initial (8%)
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;

                    // Limite maximale si le texte devient trop long
                    if (maxHeightPx > 0 && containerView.getHeight() > maxHeightPx) {
                        params.height = maxHeightPx;
                    }
                    containerView.setLayoutParams(params);
                }

                if (keypadHeight > screenHeight * 0.15) {
                    containerView.setTranslationY(-keypadHeight);
                } else {
                    containerView.setTranslationY(0);
                }
            }
        });
    }
}
