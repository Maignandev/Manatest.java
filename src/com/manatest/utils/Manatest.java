package com.manatest.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
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
        description = "Manatest - Fait flotter la zone de saisie directement au-dessus du clavier.",
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

    @SimpleFunction(description = "Attache la zone de saisie au-dessus du clavier de manière flottante.")
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
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                // Si le clavier s'ouvre (dépassant 15% de l'écran)
                if (keypadHeight > screenHeight * 0.15) {
                    containerView.setTranslationY(-keypadHeight);
                } else {
                    containerView.setTranslationY(0);
                }
            }
        });
    }
}

