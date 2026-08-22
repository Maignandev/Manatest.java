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
        version = 2,
        description = "Manatest - Clavier flottant et agrandissement automatique du conteneur avec TextBox multiligne.",
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

                if (keypadHeight > screenHeight * 0.15) {
                    containerView.setTranslationY(-keypadHeight);
                } else {
                    containerView.setTranslationY(0);
                }
            }
        });
    }

    @SimpleFunction(description = "Force le conteneur (CardView en hauteur automatique) à s'agrandir dynamiquement selon le texte tout en respectant une limite de hauteur.")
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
                View parent = (View) containerView.getParent();
                if (parent != null) {
                    parent.requestLayout();
                }

                // Applique la limite maximale de hauteur si spécifiée
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
