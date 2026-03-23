package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeDashboardActivity extends AppCompatActivity {

    ImageView navHome, navScan, navMeals, navAI, navProgress;
    ImageView settingsIcon, profileIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        // Top icons
        settingsIcon = findViewById(R.id.settingsIcon);
        profileIcon = findViewById(R.id.profileIcon);

        // Bottom nav
        navHome = findViewById(R.id.navHome);
        navScan = findViewById(R.id.navScan);
        navMeals = findViewById(R.id.navMeals);
        navAI = findViewById(R.id.navAI);
        navProgress = findViewById(R.id.navProgress);

        // Navigation logic
        navScan.setOnClickListener(v ->
                startActivity(new Intent(this, ScanFoodActivity.class)));

        navMeals.setOnClickListener(v ->
                startActivity(new Intent(this, MealPlannerActivity.class)));

        navAI.setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));

        navProgress.setOnClickListener(v ->
                startActivity(new Intent(this, ProgressAnalyticsActivity.class)));

        settingsIcon.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        profileIcon.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileSetupActivity.class)));
    }
}