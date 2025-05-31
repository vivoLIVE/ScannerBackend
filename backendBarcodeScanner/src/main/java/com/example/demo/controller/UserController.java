package com.example.demo.controller;

import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) payload.get("email");
            String password = (String) payload.get("password");
            @SuppressWarnings("unchecked")
            java.util.ArrayList<String> preferences = payload.get("preferences") != null ? 
                    new java.util.ArrayList<>((java.util.ArrayList<String>) payload.get("preferences")) : new java.util.ArrayList<>();
            @SuppressWarnings("unchecked")
            java.util.ArrayList<String> dietaryRestrictions = payload.get("dietaryRestrictions") != null ?
                    new java.util.ArrayList<>((java.util.ArrayList<String>) payload.get("dietaryRestrictions")) : new java.util.ArrayList<>();

            if (email == null || password == null) {
                response.put("success", false);
                response.put("message", "Email and password are required.");
                return ResponseEntity.badRequest().body(response);
            }
            boolean registered = userService.registerUser(email, password, preferences, dietaryRestrictions);
            if (!registered) {
                response.put("success", false);
                response.put("message", "User already exists.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            response.put("success", true);
            response.put("message", "Registration successful.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error during registration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Endpoint for user login.
     * Expects JSON example:
     * {
     *   "email": "user@example.com",
     *   "password": "password"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) payload.get("email");
            String password = (String) payload.get("password");

            if (email == null || password == null) {
                response.put("success", false);
                response.put("message", "Email and password are required.");
                return ResponseEntity.badRequest().body(response);
            }

            boolean loggedIn = userService.loginUser(email, password);
            if (!loggedIn) {
                response.put("success", false);
                response.put("message", "Invalid credentials.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            response.put("success", true);
            response.put("message", "Login successful.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error during login: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
