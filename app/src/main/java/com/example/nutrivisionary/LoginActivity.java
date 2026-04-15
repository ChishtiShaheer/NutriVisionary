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

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilName;
    private TextInputEditText etName, etEmail, etPassword;
    private MaterialButton tabLogin, tabSignup, btnSubmit;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        if (sharedPref.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(this, HomeDashboardActivity.class));
            finish();
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

        btnSubmit.setOnClickListener(v -> {
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

            if (password.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            // Extract name from email if not provided (for login) or use provided name (for signup)
            String displayName;
            if (isLoginMode) {
                displayName = email.split("@")[0];
            } else {
                displayName = name;
            }

            // Success
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("isLoggedIn", true);
            editor.putString("userName", displayName);
            editor.apply();

            startActivity(new Intent(this, HomeDashboardActivity.class));
            finish();
        });
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
