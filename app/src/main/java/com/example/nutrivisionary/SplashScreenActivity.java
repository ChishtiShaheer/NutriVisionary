package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashScreenActivity extends AppCompatActivity {

    View appName;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        mAuth = FirebaseAuth.getInstance();
        appName = findViewById(R.id.appName);

        appName.animate()
                .alpha(1f)
                .setDuration(1200)
                .setStartDelay(500);

        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                startActivity(new Intent(SplashScreenActivity.this, HomeDashboardActivity.class));
            } else {
                startActivity(new Intent(SplashScreenActivity.this, LoginActivity.class));
            }
            finish();
        }, 2200);
    }
}
