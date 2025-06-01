package com.example.demo.service;

import com.example.demo.model.RecipeMatch;
import com.example.demo.util.CacheKeyUtil;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class FirestoreService {

    private Firestore db;

    // Ingredient weights mapping.
    private static final Map<String, Double> INGREDIENT_WEIGHTS = new HashMap<>();
    static {
        INGREDIENT_WEIGHTS.put("chicken", 2.0);
        INGREDIENT_WEIGHTS.put("beef", 2.0);
        INGREDIENT_WEIGHTS.put("pork", 2.0);
        INGREDIENT_WEIGHTS.put("salt", 0.5);
        INGREDIENT_WEIGHTS.put("sugar", 0.5);
        INGREDIENT_WEIGHTS.put("bacon", 2.0);
    }

    // Normalized synonyms mapping.
    private static final Map<String, Set<String>> NORMALIZED_SYNONYMS = new HashMap<>();
    static {
        NORMALIZED_SYNONYMS.put("basil", new HashSet<>(Arrays.asList("fresh basil", "dried basil")));
        NORMALIZED_SYNONYMS.put("garlic", new HashSet<>(Arrays.asList("garlic powder", "minced garlic")));
        NORMALIZED_SYNONYMS.put("chicken", new HashSet<>(Arrays.asList("roasted chicken", "grilled chicken", "chicken thighs")));
        NORMALIZED_SYNONYMS.put("lettuce", new HashSet<>(Arrays.asList("romaine lettuce")));
        NORMALIZED_SYNONYMS.put("sesame oil", new HashSet<>(Arrays.asList("toasted sesame oil")));
        NORMALIZED_SYNONYMS.put("pepper", new HashSet<>(Arrays.asList("black pepper")));
        NORMALIZED_SYNONYMS.put("cheese", new HashSet<>(Arrays.asList("parmesan cheese", "cheddar", "fresh mozzarella", "blue cheese crumbles")));
        NORMALIZED_SYNONYMS.put("olive oil", new HashSet<>(Arrays.asList("extra virgin olive oil")));
        NORMALIZED_SYNONYMS.put("bacon", new HashSet<>(Arrays.asList("unsmoked back bacon", "unsmoked bacon")));
        NORMALIZED_SYNONYMS.put("feta", new HashSet<>(Arrays.asList("greek feta", "avocado feta")));
        NORMALIZED_SYNONYMS.put("beef", new HashSet<>(Arrays.asList("ground beef")));
    }

    @PostConstruct
    public void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            // Get the path from environment variable
            String keyPath = System.getenv("FIREBASE_CONFIG_PATH");

            if (keyPath == null || keyPath.isEmpty()) {
                throw new FileNotFoundException("Environment variable FIREBASE_CONFIG_PATH is not set.");
            }

            FileInputStream serviceAccount = new FileInputStream(keyPath);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
        }

        db = FirestoreClient.getFirestore();
    }


    public DocumentSnapshot getProductByBarcode(String barcode) throws Exception {
        ApiFuture<DocumentSnapshot> future = db.collection("products").document(barcode).get();
        return future.get();
    }

    public List<QueryDocumentSnapshot> getRecipesByIngredients(List<String> ingredients) throws Exception {
        if (ingredients == null || ingredients.isEmpty()) {
            return new ArrayList<>();
        }
        ApiFuture<QuerySnapshot> future = db.collection("recipes")
                .whereArrayContainsAny("ingredients", ingredients)
                .get();
        return future.get().getDocuments();
    }

    //Fuzzy Matching Helpers

    private int levenshteinDistance(String s, String t) {
        int[][] d = new int[s.length() + 1][t.length() + 1];
        for (int i = 0; i <= s.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= t.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= s.length(); i++) {
            for (int j = 1; j <= t.length(); j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(
                    Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                );
            }
        }
        return d[s.length()][t.length()];
    }

    private boolean isFuzzyMatch(String a, String b, int threshold) {
        return levenshteinDistance(a, b) <= threshold;
    }

    /**
     * Determines if a recipe ingredient is matched by the user's ingredients.
     * Uses direct, fuzzy and synonym matching.
     */
    private boolean isIngredientMatched(String recipeIngredient, Set<String> normalizedUserIngredients) {
        String normRecipe = recipeIngredient.toLowerCase().trim();
        // Direct and fuzzy match.
        for (String userIng : normalizedUserIngredients) {
            if (userIng.equals(normRecipe) || isFuzzyMatch(normRecipe, userIng, 2)) {
                return true;
            }
        }
        // Synonym matching.
        for (Map.Entry<String, Set<String>> entry : NORMALIZED_SYNONYMS.entrySet()) {
            String canonical = entry.getKey().toLowerCase().trim();
            Set<String> synSet = entry.getValue();
            if (normRecipe.equals(canonical) || synSet.contains(normRecipe)) {
                for (String userIng : normalizedUserIngredients) {
                    if (userIng.equals(canonical) || synSet.contains(userIng) || isFuzzyMatch(userIng, canonical, 2)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Multi dimensional scoring for recipe suggestions.
     * The results are cached to avoid recalculating frequent queries.
     * 
     * @param userIngredients   the user's scanned ingredients.
     * @param bannedIngredients ingredients to filter out.
     * @param maxTime           maximum allowed total time (prep + cook) in minutes (or null).
     * @param maxCalories       maximum allowed calories (or null).
     * @return List of RecipeMatch objects sorted by final score.
     */
    @Cacheable(value = "recipeSuggestions", key = "T(com.example.demo.util.CacheKeyUtil).generateKey(#userIngredients, #bannedIngredients, #maxTime, #maxCalories)")
    public List<RecipeMatch> getRecipeSuggestions(
            List<String> userIngredients,
            List<String> bannedIngredients,
            Long maxTime,
            Long maxCalories
    ) throws Exception {
        if (userIngredients == null || userIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        // Pre-compute normalized user ingredients.
        Set<String> normalizedUserIngredients = new HashSet<>();
        for (String ing : userIngredients) {
            normalizedUserIngredients.add(ing.toLowerCase().trim());
        }

        List<QueryDocumentSnapshot> docs = getRecipesByIngredients(userIngredients);
        List<RecipeMatch> matches = new ArrayList<>();

        // Define weight constants.
        final double penaltyFactor = 0.5;
        final double matchRatioWeight = 2.0;
        final double timeWeight = 0.1;
        final double calorieWeight = 0.01;
        final double minMatchRatio = 0.3;

        // Process each candidate recipe.
        for (QueryDocumentSnapshot doc : docs) {
            List<String> recipeIngredients = (List<String>) doc.get("ingredients");
            if (recipeIngredients == null) {
                continue;
            }

            // Early check: banned ingredients.
            boolean containsBanned = recipeIngredients.stream()
                    .anyMatch(ing -> bannedIngredients.stream()
                            .anyMatch(b -> ing.toLowerCase().contains(b.toLowerCase().trim())));
            if (containsBanned) continue;

            // Check time and calorie constraints.
            long totalTime = Optional.ofNullable(doc.getLong("preparationTime")).orElse(0L)
                    + Optional.ofNullable(doc.getLong("cookingTime")).orElse(0L);
            if (maxTime != null && totalTime > maxTime) continue;
            long recipeCalories = Optional.ofNullable(doc.getLong("calories")).orElse(0L);
            if (maxCalories != null && recipeCalories > maxCalories) continue;

            int matchedCount = 0;
            double weightedMatchedScore = 0.0;
            double weightedMissingScore = 0.0;
            List<String> missingIngredients = new ArrayList<>();

            // Single pass: compute match counts and collect missing ingredients.
            for (String ing : recipeIngredients) {
                double weight = INGREDIENT_WEIGHTS.getOrDefault(ing.toLowerCase(), 1.0);
                if (normalizedUserIngredients.contains(ing.toLowerCase().trim()) ||
                    isIngredientMatched(ing, normalizedUserIngredients)) {
                    matchedCount++;
                    weightedMatchedScore += weight;
                } else {
                    weightedMissingScore += weight;
                    missingIngredients.add(ing);
                }
            }
            if (recipeIngredients.isEmpty()) continue;
            double matchRatio = (double) matchedCount / recipeIngredients.size();
            if (matchRatio < minMatchRatio) continue;

            double baseScore = weightedMatchedScore - penaltyFactor * weightedMissingScore;
            double ratioBonus = matchRatio * matchRatioWeight;
            double finalScore = baseScore + ratioBonus;
            if (maxTime != null) {
                long timeSaved = maxTime - totalTime;
                if (timeSaved > 0) {
                    finalScore += timeSaved * timeWeight;
                }
            }
            if (maxCalories != null) {
                long calorieSaved = maxCalories - recipeCalories;
                if (calorieSaved > 0) {
                    finalScore += calorieSaved * calorieWeight;
                }
            }

            // Determine match category.
            Set<String> normalizedRecipeSet = new HashSet<>();
            for (String ing : recipeIngredients) {
                normalizedRecipeSet.add(ing.toLowerCase().trim());
            }
            int matchCategory = 3;
            if (normalizedUserIngredients.containsAll(normalizedRecipeSet)) {
                matchCategory = 1;
            } else if (normalizedRecipeSet.containsAll(normalizedUserIngredients)) {
                matchCategory = 2;
            }

            matches.add(new RecipeMatch(doc, matchedCount, recipeIngredients.size(), missingIngredients, finalScore, matchCategory));
        }
        matches.sort((a, b) -> Double.compare(b.getWeightedScore(), a.getWeightedScore()));
        return matches;
    }
}
