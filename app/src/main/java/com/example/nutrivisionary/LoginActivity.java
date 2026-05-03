package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilName;
    private TextInputEditText etName, etEmail, etPassword;
    private MaterialButton tabLogin, tabSignup, btnSubmit;
    private boolean isLoginMode = true;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        tilName = findViewById(R.id.tilName);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tabLogin = findViewById(R.id.tabLogin);
        tabSignup = findViewById(R.id.tabSignup);
        btnSubmit = findViewById(R.id.btnSubmit);
        
        tabLogin.setOnClickListener(v -> switchMode(true));
        tabSignup.setOnClickListener(v -> switchMode(false));
        btnSubmit.setOnClickListener(v -> handleAuth());
    }

    private void handleAuth() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String name = etName.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || (!isLoginMode && name.isEmpty())) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);

        if (isLoginMode) {
            loginUser(email, password);
        } else {
            signupUser(email, password, name);
        }
    }

    private void loginUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        fetchUserDataAndProceed(mAuth.getCurrentUser());
                    } else {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signupUser(String email, String password, String name) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        saveUserToFirestore(mAuth.getCurrentUser(), name);
                    } else {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String name) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", user.getEmail());
        userMap.put("uid", user.getUid());
        userMap.put("isProfileComplete", false);

        db.collection("users").document(user.getUid())
                .set(userMap)
                .addOnSuccessListener(aVoid -> {
                    saveToPrefs(name, user.getEmail(), false);
                    startActivity(new Intent(this, ProfileSetupActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this, "Error saving user data", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserDataAndProceed(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        Boolean isProfileComplete = documentSnapshot.getBoolean("isProfileComplete");
                        saveToPrefs(name, user.getEmail(), true);
                        
                        if (isProfileComplete != null && isProfileComplete) {
                            goToDashboard();
                        } else {
                            startActivity(new Intent(this, ProfileSetupActivity.class));
                            finish();
                        }
                    } else {
                        goToDashboard();
                    }
                })
                .addOnFailureListener(e -> goToDashboard());
    }

    private void saveToPrefs(String name, String email, boolean loggedIn) {
        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        sharedPref.edit()
                .putString("userName", name)
                .putString("userEmail", email)
                .putBoolean("isLoggedIn", loggedIn)
                .apply();
    }

    private void goToDashboard() {
        startActivity(new Intent(this, HomeDashboardActivity.class));
        finish();
    }

    private void switchMode(boolean login) {
        isLoginMode = login;
        if (login) {
            tabLogin.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.royalBlue));
            tabLogin.setTextColor(ContextCompat.getColor(this, R.color.white));
            tabSignup.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            tabSignup.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
            tilName.setVisibility(View.GONE);
            btnSubmit.setText("Login");
        } else {
            tabSignup.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.royalBlue));
            tabSignup.setTextColor(ContextCompat.getColor(this, R.color.white));
            tabLogin.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            tabLogin.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
            tilName.setVisibility(View.VISIBLE);
            btnSubmit.setText("Create Account");
        }
    }
}
