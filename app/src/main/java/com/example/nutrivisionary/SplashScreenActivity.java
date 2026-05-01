package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashScreenActivity extends AppCompatActivity {

    View logo, appName;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        mAuth = FirebaseAuth.getInstance();
        logo = findViewById(R.id.logo);
        appName = findViewById(R.id.appName);

        // Scale animation for logo
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0.5f, 1f,
                0.5f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(1000);
        logo.startAnimation(scaleAnimation);

        // Fade in text
        appName.animate()
                .alpha(1f)
                .setDuration(1200)
                .setStartDelay(500);

        // Navigate after delay
        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // User is signed in, go to Dashboard
                startActivity(new Intent(SplashScreenActivity.this, HomeDashboardActivity.class));
            } else {
                // No user is signed in, go to Login
                startActivity(new Intent(SplashScreenActivity.this, LoginActivity.class));
            }
            finish();
        }, 2200);
    }
}
