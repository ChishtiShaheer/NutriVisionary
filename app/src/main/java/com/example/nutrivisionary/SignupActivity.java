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

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
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
                Toast.makeText(SignupActivity.this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(SignupActivity.this, "Invalid email format", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(SignupActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            signupBtn.setEnabled(false);
            signupBtn.setText("Creating account...");

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(SignupActivity.this, authResult -> {
                        // FIX: addOnSuccessListener(activity, ...) binds to Activity lifecycle
                        // so callback is guaranteed on main thread
                        String uid = authResult.getUser().getUid();
                        saveUserToFirestore(uid, name, email);
                    })
                    .addOnFailureListener(SignupActivity.this, e -> {
                        Toast.makeText(SignupActivity.this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        signupBtn.setEnabled(true);
                        signupBtn.setText(getString(R.string.signUpBtn));
                    });
        });
    }

    private void saveUserToFirestore(String uid, String name, String email) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("uid", uid);
        userMap.put("isProfileComplete", false);

        db.collection("users").document(uid)
                .set(userMap)
                .addOnSuccessListener(SignupActivity.this, unused -> {
                    // FIX: use Activity context (SignupActivity.this) for Toast, never getApplicationContext()
                    Toast.makeText(SignupActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                    signupBtn.setEnabled(true);
                    signupBtn.setText(getString(R.string.signUpBtn));

                    nameField.setText("");
                    emailField.setText("");
                    passwordField.setText("");

                    SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
                    sharedPref.edit()
                            .putString("userName", name)
                            .putBoolean("isLoggedIn", true)
                            .apply();

                    // Navigate to profile setup — do NOT call mAuth.signOut() here
                    Intent intent = new Intent(SignupActivity.this, ProfileSetupActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(SignupActivity.this, e -> {
                    Log.e(TAG, "Firestore error: " + e.getMessage());
                    Toast.makeText(SignupActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    signupBtn.setEnabled(true);
                    signupBtn.setText(getString(R.string.signUpBtn));
                });
    }
}