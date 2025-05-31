package com.example.demo.service;

import com.example.demo.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
public class UserService {

    private Firestore db = FirestoreClient.getFirestore();
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registers a new user.
     * @return true if registration succeeded; false if user already exists.
     */
    public boolean registerUser(String email, String password, ArrayList<String> preferences, ArrayList<String> dietaryRestrictions) throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(email);
        ApiFuture<DocumentSnapshot> future = userRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return false;
        }
        // Generate a random userId.
        int userId = new Random().nextInt(1000000);
        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(userId, email, hashedPassword, preferences, dietaryRestrictions, new ArrayList<>());
        userRef.set(user);
        return true;
    }

    /**
     * Logs in a user by verifying the hashed password.
     */
    public boolean loginUser(String email, String password) throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(email);
        DocumentSnapshot document = userRef.get().get();
        if (!document.exists()) {
            return false;
        }
        String storedHash = document.getString("hashedPassword");
        return passwordEncoder.matches(password, storedHash);
    }
    
    /**
     * Retrieves a user by email.
     */
    public User getUserByEmail(String email) throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(email);
        DocumentSnapshot doc = userRef.get().get();
        if (!doc.exists()) {
            return null;
        }
        return doc.toObject(User.class);
    }
    
    /**
     * Updates a user's profile.
     * If newPassword is provided, updates the hashed password.
     */
    public boolean updateUserProfile(String email, String newPassword, ArrayList<String> preferences, ArrayList<String> dietaryRestrictions)
            throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(email);
        DocumentSnapshot doc = userRef.get().get();
        if (!doc.exists()) {
            return false;
        }
        // Prepare update fields.
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            String hashedPassword = passwordEncoder.encode(newPassword.trim());
            updates.put("hashedPassword", hashedPassword);
        }
        if (preferences != null) {
            updates.put("preferences", preferences);
        }
        if (dietaryRestrictions != null) {
            updates.put("dietaryRestrictions", dietaryRestrictions);
        }
        // Update the document.
        userRef.update(updates);
        return true;
    }
}
