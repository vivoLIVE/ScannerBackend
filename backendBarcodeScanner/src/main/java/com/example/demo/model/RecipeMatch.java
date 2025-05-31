package com.example.demo.model;

import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.List;

/**
 * Model representing a matched recipe along with various computed metrics.
 */
public class RecipeMatch {
    private final QueryDocumentSnapshot recipeDoc;
    private final int matchedCount;
    private final int totalRecipeIngredients;
    private final List<String> missingIngredients;
    private final double weightedScore;
    //matchCategory indicates:
    // 1 Exact match (recipe ingredients user ingredients)
    // 2 Full match with extras (user ingredients recipe ingredients)
    // 3 Partial match (some but not all user ingredients)
    private final int matchCategory;

    public RecipeMatch(QueryDocumentSnapshot recipeDoc,
                       int matchedCount,
                       int totalRecipeIngredients,
                       List<String> missingIngredients,
                       double weightedScore,
                       int matchCategory) {
        this.recipeDoc = recipeDoc;
        this.matchedCount = matchedCount;
        this.totalRecipeIngredients = totalRecipeIngredients;
        this.missingIngredients = missingIngredients;
        this.weightedScore = weightedScore;
        this.matchCategory = matchCategory;
    }

    public QueryDocumentSnapshot getRecipeDoc() {
        return recipeDoc;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getTotalRecipeIngredients() {
        return totalRecipeIngredients;
    }

    public List<String> getMissingIngredients() {
        return missingIngredients;
    }
    
    public double getWeightedScore() {
        return weightedScore;
    }
    
    public int getMatchCategory() {
        return matchCategory;
    }
    
    public double getMatchRatio() {
        if (totalRecipeIngredients == 0) return 0;
        return (double) matchedCount / totalRecipeIngredients;
    }
    
    public double getPenalizedScore() {
        double penaltyFactor = 0.5;
        int missingCount = totalRecipeIngredients - matchedCount;
        return matchedCount - penaltyFactor * missingCount;
    }
}
