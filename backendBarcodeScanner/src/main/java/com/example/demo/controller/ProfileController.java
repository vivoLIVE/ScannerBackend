package com.example.demo.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    /**
     * Get a user profile by email.
     * Example: GET /profile/getProfile?email=user@example.com
     */
    @GetMapping("/getProfile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            DocumentReference userRef = FirestoreClient.getFirestore().collection("users").document(email);
            ApiFuture<com.google.cloud.firestore.DocumentSnapshot> future = userRef.get();
            com.google.cloud.firestore.DocumentSnapshot doc = future.get();
            if (!doc.exists()) {
                response.put("success", false);
                response.put("message", "User not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            response.put("success", true);
            response.put("user", doc.getData());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error retrieving profile: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Update a user profile.
     * Expects JSON:
     * {
     *   "email": "user@example.com",
     *   "newPassword": "optionalNewPassword",
     *   "preferences": ["pref1", "pref2"],
     *   "dietaryRestrictions": ["restriction1", "restriction2"]
     * }
     */
    @PutMapping("/updateProfile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) payload.get("email");
            if (email == null) {
                response.put("success", false);
                response.put("message", "Email is required.");
                return ResponseEntity.badRequest().body(response);
            }
            String newPassword = (String) payload.get("newPassword");
            @SuppressWarnings("unchecked")
            java.util.List<String> preferences = (java.util.List<String>) payload.get("preferences");
            @SuppressWarnings("unchecked")
            java.util.List<String> dietaryRestrictions = (java.util.List<String>) payload.get("dietaryRestrictions");

            DocumentReference userRef = FirestoreClient.getFirestore().collection("users").document(email);
            Map<String, Object> updates = new HashMap<>();
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                
                updates.put("hashedPassword", newPassword.trim());
            }
            if (preferences != null) {
                updates.put("preferences", preferences);
            }
            if (dietaryRestrictions != null) {
                updates.put("dietaryRestrictions", dietaryRestrictions);
            }
            ApiFuture<WriteResult> future = userRef.update(updates);
            future.get();
            response.put("success", true);
            response.put("message", "Profile updated successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Add a product to the user's scanHistory.
     * Expects JSON:
     * {
     *   "email": "user@example.com",
     *   "productName": "Scanned Product Name"
     * }
     */
    @PutMapping("/addScanHistory")
    public ResponseEntity<Map<String, Object>> addScanHistory(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) payload.get("email");
            String productName = (String) payload.get("productName");
            if (email == null || productName == null) {
                response.put("success", false);
                response.put("message", "Email and productName are required.");
                return ResponseEntity.badRequest().body(response);
            }
            DocumentReference userRef = FirestoreClient.getFirestore().collection("users").document(email);
            ApiFuture<WriteResult> future = userRef.update("scanHistory", FieldValue.arrayUnion(productName));
            future.get();
            response.put("success", true);
            response.put("message", "Scan history updated.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error updating scan history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
