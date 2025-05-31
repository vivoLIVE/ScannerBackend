package com.example.demo.controller;

import com.example.demo.model.RecipeMatch;
import com.example.demo.service.FirestoreService;
import com.example.demo.util.CustomBarcodeProcessor;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;

@RestController
public class BarcodeController {

    @Autowired
    private FirestoreService firestoreService;
    
    // Substitution map for missing ingredients.
    private static final Map<String, String> SUBSTITUTION_MAP = new HashMap<>();
    static {
        SUBSTITUTION_MAP.put("butter", "margarine");
        SUBSTITUTION_MAP.put("sour cream", "plain yogurt");
        SUBSTITUTION_MAP.put("egg", "flax egg (1 tbsp ground flaxseed + 3 tbsp water)");
    }
    
    @PostMapping({"/scanBarcode", "/continuousScan"})
    public ResponseEntity<Map<String, Object>> scanBarcode(@RequestParam("image") MultipartFile imageFile) {
        Map<String, Object> response = new HashMap<>();
        try {
            BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());
            if (originalImage == null) {
                response.put("success", false);
                response.put("message", "Invalid image file.");
                return ResponseEntity.badRequest().body(response);
            }
            String barcode = CustomBarcodeProcessor.detectBarcode(originalImage);
            if (barcode == null || barcode.isEmpty()) {
                response.put("success", false);
                response.put("message", "Barcode not detected.");
                return ResponseEntity.ok(response);
            }
            DocumentSnapshot productDoc = firestoreService.getProductByBarcode(barcode);
            if (!productDoc.exists()) {
                response.put("success", false);
                response.put("message", "Product not found for barcode: " + barcode);
                response.put("barcode", barcode);
                return ResponseEntity.ok(response);
            }
            String productName = productDoc.getString("name");
            List<String> productIngredients = (List<String>) productDoc.get("ingredients");
            if (productIngredients == null || productIngredients.isEmpty()) {
                response.put("success", false);
                response.put("message", "No ingredients found for product: " + productName);
                response.put("barcode", barcode);
                return ResponseEntity.ok(response);
            }
            List<QueryDocumentSnapshot> recipeDocs = firestoreService.getRecipesByIngredients(productIngredients);
            if (recipeDocs.isEmpty()) {
                response.put("success", false);
                response.put("message", "No recipes found for product ingredients: " + productIngredients);
                response.put("barcode", barcode);
                return ResponseEntity.ok(response);
            }
            StringBuilder receiptBuilder = new StringBuilder();
            for (QueryDocumentSnapshot doc : recipeDocs) {
                String title = doc.getString("title");
                String instructions = doc.getString("instructions");
                Long prepTime = getLongValue(doc, "preparationTime");
                Long cookTime = getLongValue(doc, "cookingTime");
                Long servings = getLongValue(doc, "servings");
                String imageUrl = doc.getString("imageUrl");
                receiptBuilder.append("Title: ").append(title).append("\n")
                              .append("Instructions: ").append(instructions).append("\n")
                              .append("Prep Time: ").append(prepTime).append(" mins, ")
                              .append("Cooking Time: ").append(cookTime).append(" mins, ")
                              .append("Servings: ").append(servings).append("\n")
                              .append("Image URL: ").append(imageUrl).append("\n\n");
            }
            response.put("success", true);
            response.put("message", "Barcode scanned successfully.");
            response.put("barcode", barcode);
            response.put("ingredientName", productName);
            response.put("productIngredients", productIngredients);
            response.put("receipt", receiptBuilder.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error processing image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/suggestRecipes")
    public ResponseEntity<Map<String, Object>> suggestRecipes(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Object ingredientsObj = payload.get("ingredients");
            if (!(ingredientsObj instanceof List)) {
                response.put("success", false);
                response.put("message", "Ingredients not provided or invalid format.");
                return ResponseEntity.badRequest().body(response);
            }
            List<String> userIngredients = (List<String>) ingredientsObj;
            
            List<String> bannedIngredients = new ArrayList<>();
            Object bannedObj = payload.get("bannedIngredients");
            if (bannedObj instanceof List) {
                bannedIngredients = (List<String>) bannedObj;
            }
            
            Long maxTime = null;
            if (payload.containsKey("maxTime")) {
                Object maxTimeObj = payload.get("maxTime");
                if (maxTimeObj instanceof Number) {
                    maxTime = ((Number) maxTimeObj).longValue();
                } else if (maxTimeObj instanceof String) {
                    try {
                        maxTime = Long.parseLong((String) maxTimeObj);
                    } catch (NumberFormatException e) {
                        maxTime = null;
                    }
                }
            }
            
            Long maxCalories = null;
            if (payload.containsKey("maxCalories")) {
                Object maxCalsObj = payload.get("maxCalories");
                if (maxCalsObj instanceof Number) {
                    maxCalories = ((Number) maxCalsObj).longValue();
                } else if (maxCalsObj instanceof String) {
                    try {
                        maxCalories = Long.parseLong((String) maxCalsObj);
                    } catch (NumberFormatException e) {
                        maxCalories = null;
                    }
                }
            }
            
            List<RecipeMatch> suggestions = firestoreService.getRecipeSuggestions(
                    userIngredients,
                    bannedIngredients,
                    maxTime,
                    maxCalories
            );
            
            if (suggestions.isEmpty()) {
                response.put("success", true);
                response.put("recipes", new ArrayList<>());
                response.put("message", "No recipes found matching your criteria.");
                return ResponseEntity.ok(response);
            }
            
            List<Map<String, Object>> recipeResults = new ArrayList<>();
            for (RecipeMatch match : suggestions) {
                QueryDocumentSnapshot doc = match.getRecipeDoc();
                @SuppressWarnings("unchecked")
                List<String> recipeIngredients = (List<String>) doc.get("ingredients");
                if (recipeIngredients == null) {
                    recipeIngredients = new ArrayList<>();
                }
                List<String> currentIngredients = new ArrayList<>();
                for (String ing : recipeIngredients) {
                    if (userIngredients.contains(ing) || isIngredientMatchedForCurrent(ing, userIngredients)) {
                        currentIngredients.add(ing);
                    }
                }
                
                List<String> missingSuggestions = new ArrayList<>();
                List<String> missingIngredients = match.getMissingIngredients();
                for (String missing : missingIngredients) {
                    for (Map.Entry<String, String> entry : SUBSTITUTION_MAP.entrySet()) {
                        if (missing.toLowerCase().contains(entry.getKey().toLowerCase().trim())) {
                            missingSuggestions.add("For \"" + missing + "\", consider using \"" + entry.getValue() + "\".");
                            break;
                        }
                    }
                }
                
                Map<String, Object> recipeMap = new HashMap<>();
                recipeMap.put("title", doc.getString("title"));
                recipeMap.put("instructions", doc.getString("instructions"));
                recipeMap.put("imageUrl", doc.getString("imageUrl"));
                recipeMap.put("matchedCount", match.getMatchedCount());
                recipeMap.put("totalIngredients", match.getTotalRecipeIngredients());
                recipeMap.put("missingIngredients", match.getMissingIngredients());
                recipeMap.put("weightedScore", match.getWeightedScore());
                recipeMap.put("currentIngredients", currentIngredients);
                recipeMap.put("missingSuggestions", missingSuggestions);
                recipeMap.put("matchCategory", match.getMatchCategory());
                
                Long prepTime = doc.getLong("preparationTime");
                Long cookTime = doc.getLong("cookingTime");
                Long recipeCalories = doc.getLong("calories");
                recipeMap.put("preparationTime", prepTime);
                recipeMap.put("cookingTime", cookTime);
                recipeMap.put("calories", recipeCalories);
                
                recipeResults.add(recipeMap);
            }
            response.put("success", true);
            response.put("recipes", recipeResults);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    private Long getLongValue(QueryDocumentSnapshot doc, String fieldName) {
        Object value = doc.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
    
    /**
     * Helper method for fuzzy and synonym matching for current ingredients.
     */
    private boolean isIngredientMatchedForCurrent(String recipeIngredient, List<String> userIngredients) {
        String normalizedRecipe = recipeIngredient.toLowerCase().trim();
        for (String userIng : userIngredients) {
            if (userIng.toLowerCase().trim().equals(normalizedRecipe)) {
                return true;
            }
        }
        for (String userIng : userIngredients) {
            if (levenshteinDistance(normalizedRecipe, userIng.toLowerCase().trim()) <= 2) {
                return true;
            }
        }
        Map<String, Set<String>> normalizedSynonymMap = new HashMap<>();
        normalizedSynonymMap.put("lettuce", new HashSet<>(Arrays.asList("romaine lettuce")));
        for (Map.Entry<String, Set<String>> entry : normalizedSynonymMap.entrySet()) {
            String canonical = entry.getKey().toLowerCase().trim();
            Set<String> synSet = entry.getValue();
            if (normalizedRecipe.equals(canonical) || synSet.contains(normalizedRecipe)) {
                for (String userIng : userIngredients) {
                    String normalizedUser = userIng.toLowerCase().trim();
                    if (normalizedUser.equals(canonical) || synSet.contains(normalizedUser)) {
                        return true;
                    }
                    if (levenshteinDistance(normalizedUser, canonical) <= 2) {
                        return true;
                    }
                    for (String syn : synSet) {
                        if (levenshteinDistance(normalizedUser, syn) <= 2) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
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
}
