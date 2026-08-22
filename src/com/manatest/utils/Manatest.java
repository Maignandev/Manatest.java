package com.manatest.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

@DesignerComponent(
        version = 3,
        description = "Manatest - Clavier flottant fluide et ajustement de hauteur.",
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

    @SimpleFunction(description = "Attache le conteneur au-dessus du clavier.")
    public void AttachFloatingInputWithDynamicHeight(
            final Object inputContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(inputContainer instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) inputContainer).getView();
        if (containerView == null) return;

        final View rootView = activity.getWindow().getDecorView();

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);

                int screenHeight = rootView.getHeight();
                int keypadHeight = screenHeight - r.bottom;

                // Vérification si la hauteur du clavier est significative (supérieure à 15% de l'écran)
                if (keypadHeight > screenHeight * 0.15) {
                    containerView.setTranslationY(-keypadHeight);
                } else {
                    containerView.setTranslationY(0);
                }
            }
        });
    }

    @SimpleFunction(description = "Agrandit le conteneur dynamiquement avec le texte.")
    public void EnableAutoGrowWithText(
            final Object cardContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(cardContainer instanceof AndroidViewComponent) || !(editTextComponent instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) cardContainer).getView();
        View editView = ((AndroidViewComponent) editTextComponent).getView();

        if (!(editView instanceof EditText) || containerView == null) return;

        ((EditText) editView).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                containerView.requestLayout();
                if (containerView.getParent() instanceof View) {
                    ((View) containerView.getParent()).requestLayout();
                }

                if (maxHeightPx > 0) {
                    containerView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (containerView.getHeight() > maxHeightPx) {
                                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                                params.height = maxHeightPx;
                                containerView.setLayoutParams(params);
                            }
                        }
                    });
                }
            }
        });
    }
}

