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
        version = 4,
        description = "Manatest - Gestion avancée du clavier flottant et auto-growth.",
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

    @SimpleFunction(description = "Fait flotter le conteneur au-dessus du clavier virtuel.")
    public void AttachFloatingInputWithDynamicHeight(
            final Object inputContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(inputContainer instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) inputContainer).getView();
        if (containerView == null) return;

        final View contentView = activity.findViewById(android.R.id.content);
        if (contentView == null) return;

        contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                contentView.getWindowVisibleDisplayFrame(r);

                int screenHeight = contentView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                // Si le clavier dépasse 15% de l'écran, décaler le conteneur
                if (keypadHeight > screenHeight * 0.15) {
                    containerView.setTranslationY(-keypadHeight);
                } else {
                    containerView.setTranslationY(0);
                }
            }
        });
    }

    @SimpleFunction(description = "Permet l'agrandissement automatique du conteneur lors de la saisie.")
    public void EnableAutoGrowWithText(
            final Object cardContainer,
            final Object editTextComponent,
            final int maxHeightPx) {

        if (!(cardContainer instanceof AndroidViewComponent) || !(editTextComponent instanceof AndroidViewComponent)) return;

        final View containerView = ((AndroidViewComponent) cardContainer).getView();
        View editView = ((AndroidViewComponent) editTextComponent).getView();

        if (!(editView instanceof EditText) || containerView == null) return;

        final EditText editText = (EditText) editView;

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                containerView.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup.LayoutParams params = containerView.getLayoutParams();
                        if (params != null) {
                            // Autorise le redimensionnement automatique selon le texte
                            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;

                            // Si une hauteur maximale est définie et dépassée
                            if (maxHeightPx > 0 && containerView.getHeight() > maxHeightPx) {
                                params.height = maxHeightPx;
                            }
                            containerView.setLayoutParams(params);
                            containerView.requestLayout();
                        }
                    }
                });
            }
        });
    }
}

