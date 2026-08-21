import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.AsynchUtil;

import org.json.JSONArray;
import org.json.JSONObject;

// --- VARIABLES PRIVÉES ET MÉTHODES UTILITAIRES ---

private Typeface customTypeface = null;

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

private int dpToPx(int dp) {
    return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
    );
}

// --- ÉVÉNEMENT KODULAR ---

@SimpleEvent(description = "Déclenché lors du choix d'une catégorie.")
public void OnCategorySelected(String categoryId, String categoryTitle) {
    EventDispatcher.dispatchEvent(this, "OnCategorySelected", categoryId, categoryTitle);
}

// --- FONCTION PRINCIPALE ---

@SimpleFunction(description = "Génère la liste dynamique des catégories et sous-catégories à partir d'un JSON.")
public void BuildCategoryListFromJson(
        final AndroidViewComponent listContainer,
        final String categoriesJson) {

    AsynchUtil.runAsynchronously(new Runnable() {
        @Override
        public void run() {
            try {
                final JSONArray mainArray = new JSONArray(categoriesJson);

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ViewGroup targetLayout = getRealLayout(listContainer);
                            if (targetLayout == null) return;

                            targetLayout.removeAllViews();

                            final RadioGroup groupeUnique = new RadioGroup(activity);
                            groupeUnique.setOrientation(LinearLayout.VERTICAL);
                            groupeUnique.setLayoutParams(new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            ));

                            int colorTitle = Color.parseColor("#1F1F1F");
                            int colorItem = Color.parseColor("#4A4A4A");
                            int colorDivider = Color.parseColor("#F0F0F0");

                            for (int i = 0; i < mainArray.length(); i++) {
                                JSONObject categoryObj = mainArray.getJSONObject(i);
                                String categoryName = categoryObj.optString("title", "");
                                JSONArray subCategories = categoryObj.optJSONArray("subcategories");

                                TextView header = new TextView(activity);
                                header.setText(">  " + categoryName);
                                header.setTextColor(colorTitle);
                                header.setTextSize(20);
                                header.setTypeface(null, Typeface.BOLD);
                                if (customTypeface != null) header.setTypeface(customTypeface, Typeface.BOLD);
                                header.setPadding(0, dpToPx(16), 0, dpToPx(8));
                                
                                groupeUnique.addView(header);

                                if (subCategories != null) {
                                    for (int j = 0; j < subCategories.length(); j++) {
                                        JSONObject subObj = subCategories.getJSONObject(j);
                                        final String subId = subObj.optString("id", "");
                                        final String subTitle = subObj.optString("title", "");

                                        RadioButton bouton = new RadioButton(activity);
                                        bouton.setId(View.generateViewId());
                                        bouton.setText(subTitle);
                                        bouton.setTextColor(colorItem);
                                        bouton.setTextSize(16);
                                        if (customTypeface != null) bouton.setTypeface(customTypeface);
                                        
                                        LinearLayout.LayoutParams pBouton = new LinearLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                        );
                                        bouton.setLayoutParams(pBouton);

                                        int padVertical = dpToPx(14);
                                        int padHorizontal = dpToPx(8);
                                        bouton.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);
                                        bouton.setTag(subId);

                                        bouton.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                OnCategorySelected(subId, subTitle);
                                            }
                                        });

                                        groupeUnique.addView(bouton);

                                        View divider = new View(activity);
                                        LinearLayout.LayoutParams pDivider = new LinearLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                dpToPx(1)
                                        );
                                        divider.setLayoutParams(pDivider);
                                        divider.setBackgroundColor(colorDivider);

                                        groupeUnique.addView(divider);
                                    }
                                }
                            }

                            targetLayout.addView(groupeUnique);

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
