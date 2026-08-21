    // =========================================================================
    // LISTE DYNAMIQUE DE CATÉGORIES (Design identique à la maquette)
    // =========================================================================

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

                                            LinearLayout itemContainer = new LinearLayout(activity);
                                            itemContainer.setOrientation(LinearLayout.VERTICAL);
                                            LinearLayout.LayoutParams pContainer = new LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            itemContainer.setLayoutParams(pContainer);

                                            RadioButton bouton = new RadioButton(activity);
                                            bouton.setText(subTitle);
                                            bouton.setTextColor(colorItem);
                                            bouton.setTextSize(16);
                                            if (customTypeface != null) bouton.setTypeface(customTypeface);
                                            
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

                                            itemContainer.addView(bouton);

                                            View divider = new View(activity);
                                            LinearLayout.LayoutParams pDivider = new LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    dpToPx(1)
                                            );
                                            divider.setLayoutParams(pDivider);
                                            divider.setBackgroundColor(colorDivider);

                                            itemContainer.addView(divider);

                                            groupeUnique.addView(itemContainer);
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
