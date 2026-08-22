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
        version = 1,
        description = "Manatest - Ajuste la hauteur de la CardView en fonction du texte et de l'état du clavier.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true
)
@SimpleObject(external = true)
public class Manatest extends AndroidNonvisibleComponent {

    private final Activity activity;
    private int baseCardHeight = -1;

    public Manatest(ComponentContainer container) {
        super(container.$form());
        this.activity = (Activity) container.$context();
    }

    @SimpleFunction(description = "Attache la zone de saisie au-dessus du clavier et met à jour sa hauteur selon le texte.")
    public void AttachFloatingInputWithDynamicHeight(
            final Object inputContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(inputContainer instanceof AndroidViewComponent) || !(editTextComponent instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) inputContainer).getView();
        final View editView = ((AndroidViewComponent) editTextComponent).getView();

        if (containerView == null || editView == null) return;

        final View rootView = activity.getWindow().getDecorView().getRootView();

        // 1. Écouteur pour ajuster la hauteur de la CardView quand le texte change
        if (editView instanceof EditText) {
            final EditText editText = (EditText) editView;
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (baseCardHeight <= 0) {
                        baseCardHeight = containerView.getHeight(); // Garde la hauteur des 8%
                    }

                    containerView.post(new Runnable() {
                        @Override
                        public void run() {
                            int lineCount = editText.getLineCount();
                            if (lineCount <= 1) {
                                // Revenir à la hauteur de base si 1 seule ligne
                                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                                params.height = baseCardHeight;
                                containerView.setLayoutParams(params);
                            } else {
                                // Agrandir dynamiquement si multi-lignes
                                ViewGroup.LayoutParams params = containerView.getLayoutParams();
                                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                containerView.setLayoutParams(params);

                                containerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (maxHeightPx > 0 && containerView.getHeight() > maxHeightPx) {
                                            ViewGroup.LayoutParams p = containerView.getLayoutParams();
                                            p.height = maxHeightPx;
                                            containerView.setLayoutParams(p);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // 2. Gestion du décalage par rapport au clavier virtuel
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (baseCardHeight <= 0 && containerView.getHeight() > 0) {
                    baseCardHeight = containerView.getHeight();
                }

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
}
