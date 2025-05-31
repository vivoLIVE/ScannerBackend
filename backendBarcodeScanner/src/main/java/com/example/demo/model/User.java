package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private int userId;                     // Numeric user ID.
    private String email;                   // User email.
    private String hashedPassword;          // BCrypt-hashed password.
    private List<String> preferences;       // Array of user preferences.
    private List<String> dietaryRestrictions; // Array of dietary restrictions.
    private List<String> scanHistory;       // History of scanned product names.

    public User() {
        // Firestore requires a no-arg constructor.
    }

    public User(int userId, String email, String hashedPassword, List<String> preferences, List<String> dietaryRestrictions, List<String> scanHistory) {
        this.userId = userId;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.preferences = preferences;
        this.dietaryRestrictions = dietaryRestrictions;
        this.scanHistory = scanHistory;
    }

    // Getters and setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }

    public List<String> getPreferences() { return preferences == null ? new ArrayList<>() : preferences; }
    public void setPreferences(List<String> preferences) { this.preferences = preferences; }

    public List<String> getDietaryRestrictions() { return dietaryRestrictions == null ? new ArrayList<>() : dietaryRestrictions; }
    public void setDietaryRestrictions(List<String> dietaryRestrictions) { this.dietaryRestrictions = dietaryRestrictions; }

    public List<String> getScanHistory() { return scanHistory == null ? new ArrayList<>() : scanHistory; }
    public void setScanHistory(List<String> scanHistory) { this.scanHistory = scanHistory; }
}
