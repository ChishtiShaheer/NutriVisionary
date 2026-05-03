package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private EditText nameField, emailField, passwordField;
    private Button signupBtn;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameField = findViewById(R.id.name);
        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        signupBtn = findViewById(R.id.signUpBtn);

        signupBtn.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
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

            // Set state to Saving
            signupBtn.setEnabled(false);
            signupBtn.setText("Saving...");
            Toast.makeText(this, "Creating your account...", Toast.LENGTH_SHORT).show();

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                saveUserToFirestore(user, name);
                            } else {
                                signupBtn.setEnabled(true);
                                signupBtn.setText("Sign Up");
                                Toast.makeText(SignupActivity.this, "Authentication succeeded but user is null", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            signupBtn.setEnabled(true);
                            signupBtn.setText("Sign Up");
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                            Toast.makeText(SignupActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void saveUserToFirestore(@NonNull FirebaseUser user, String name) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", user.getEmail());
        userMap.put("uid", user.getUid());
        userMap.put("isProfileComplete", false);

        db.collection("users").document(user.getUid())
                .set(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getApplicationContext(), "Account created successfully!", Toast.LENGTH_SHORT).show();
                        
                        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
                        sharedPref.edit()
                                .putString("userName", name)
                                .putBoolean("isLoggedIn", true)
                                .apply();
                        
                        Intent intent = new Intent(SignupActivity.this, ProfileSetupActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        signupBtn.setEnabled(true);
                        signupBtn.setText("Sign Up");
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Error saving user data";
                        Log.e(TAG, "Firestore error: " + errorMsg);
                        Toast.makeText(SignupActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
