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
            Toast.makeText(this, getString(R.string.fill_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, getString(R.string.password_length), Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText(isLoginMode ? getString(R.string.logging_in) : getString(R.string.creating_account));

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
                        btnSubmit.setText(getString(R.string.login2));
                        Toast.makeText(this, getString(R.string.auth_failed) + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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
                        btnSubmit.setText(getString(R.string.create_account));
                        Toast.makeText(this, getString(R.string.reg_failed) + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, getString(R.string.acc_created), Toast.LENGTH_SHORT).show();
                    saveToPrefs(name, user.getEmail(), false);
                    startActivity(new Intent(this, ProfileSetupActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(getString(R.string.create_account));
                    Toast.makeText(this, getString(R.string.error_saving), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserDataAndProceed(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        Boolean isProfileComplete = documentSnapshot.getBoolean("isProfileComplete");
                        saveToPrefs(name, user.getEmail(), true);
                        
                        Toast.makeText(this, getString(R.string.welcome_back, (name != null ? name : getString(R.string.user_name))), Toast.LENGTH_SHORT).show();

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
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(getString(R.string.login2));
                    goToDashboard();
                });
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
        Intent intent = new Intent(this, HomeDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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
            btnSubmit.setText(getString(R.string.login2));
        } else {
            tabSignup.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.royalBlue));
            tabSignup.setTextColor(ContextCompat.getColor(this, R.color.white));
            tabLogin.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            tabLogin.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
            tilName.setVisibility(View.VISIBLE);
            btnSubmit.setText(getString(R.string.create_account));
        }
    }
}
