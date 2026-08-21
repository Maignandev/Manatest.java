    // =========================================================================
    // 3B. LISTE DYNAMIQUE DE CATÉGORIES (Design identique à la maquette)
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
                                View containerView = listContainer.getView();
                                if (!(containerView instanceof ViewGroup)) return;

                                ViewGroup layout = (ViewGroup) containerView;
                                layout.removeAllViews();

                                // RadioGroup unique pour garantir qu'un seul bouton est sélectionné sur toute la liste
                                final RadioGroup groupeUnique = new RadioGroup(activity);
                                groupeUnique.setOrientation(LinearLayout.VERTICAL);

                                // Couleurs extraites du design de la maquette
                                int colorTitle = Color.parseColor("#1F1F1F");      // Titres principaux (ex: Mode, Électronique)
                                int colorItem = Color.parseColor("#4A4A4A");       // Textes des sous-catégories
                                int colorDivider = Color.parseColor("#F0F0F0");    // Lignes de séparation fines

                                for (int i = 0; i < mainArray.length(); i++) {
                                    JSONObject categoryObj = mainArray.getJSONObject(i);
                                    String categoryName = categoryObj.optString("title", "");
                                    JSONArray subCategories = categoryObj.optJSONArray("subcategories");

                                    // --- 1. Titre de la catégorie principale ---
                                    TextView header = new TextView(activity);
                                    header.setText(">  " + categoryName);
                                    header.setTextColor(colorTitle);
                                    header.setTextSize(20);
                                    header.setTypeface(null, Typeface.BOLD);
                                    int padHeaderTop = (int) dpToPx(16);
                                    int padHeaderBottom = (int) dpToPx(8);
                                    header.setPadding(0, padHeaderTop, 0, padHeaderBottom);
                                    
                                    groupeUnique.addView(header);

                                    // --- 2. Sous-catégories ---
                                    if (subCategories != null) {
                                        for (int j = 0; j < subCategories.length(); j++) {
                                            JSONObject subObj = subCategories.getJSONObject(j);
                                            final String subId = subObj.optString("id", "");
                                            final String subTitle = subObj.optString("title", "");

                                            // Conteneur de l'élément (RadioButton + Ligne de séparation)
                                            LinearLayout itemContainer = new LinearLayout(activity);
                                            itemContainer.setOrientation(LinearLayout.VERTICAL);
                                            LinearLayout.LayoutParams pContainer = new LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            itemContainer.setLayoutParams(pContainer);

                                            // Création du RadioButton
                                            RadioButton bouton = new RadioButton(activity);
                                            bouton.setText(subTitle);
                                            bouton.setTextColor(colorItem);
                                            bouton.setTextSize(16);
                                            
                                            // Espacement interne exact
                                            int padVertical = (int) dpToPx(14);
                                            int padHorizontal = (int) dpToPx(8);
                                            bouton.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);
                                            bouton.setTag(subId);

                                            // Événement au clic de la sous-catégorie
                                            bouton.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    OnCategorySelected(subId, subTitle);
                                                }
                                            });

                                            itemContainer.addView(bouton);

                                            // --- Ligne de séparation sous chaque sous-catégorie ---
                                            View divider = new View(activity);
                                            LinearLayout.LayoutParams pDivider = new LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    (int) dpToPx(1)
                                            );
                                            divider.setLayoutParams(pDivider);
                                            divider.setBackgroundColor(colorDivider);

                                            itemContainer.addView(divider);

                                            // Ajout au groupe global
                                            groupeUnique.addView(itemContainer);
                                        }
                                    }
                                }

                                layout.addView(groupeUnique);

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

