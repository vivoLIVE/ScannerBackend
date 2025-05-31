package com.example.demo.util;

import java.util.List;
import java.util.stream.Collectors;

public class CacheKeyUtil {
    public static String generateKey(List<String> userIngredients, List<String> bannedIngredients, Long maxTime, Long maxCalories) {
        // Sort lists to ensure the key is order-independent.
        String userKey = userIngredients.stream()
                            .map(String::toLowerCase)
                            .sorted()
                            .collect(Collectors.joining(","));
        String bannedKey = bannedIngredients.stream()
                            .map(String::toLowerCase)
                            .sorted()
                            .collect(Collectors.joining(","));
        String timeKey = maxTime == null ? "null" : maxTime.toString();
        String calKey = maxCalories == null ? "null" : maxCalories.toString();
        return userKey + "_" + bannedKey + "_" + timeKey + "_" + calKey;
    }
}
