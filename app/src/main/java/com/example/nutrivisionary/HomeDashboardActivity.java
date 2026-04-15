package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeDashboardActivity extends AppCompatActivity {

    private ImageView navHome, navScan, navMeals, navAI, navProgress;
    private ImageView settingsIcon, profileIcon;
    private TextView tvUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        tvUserName = findViewById(R.id.tvUserName);

        // Fetch user name from SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        String userName = sharedPref.getString("userName", "User");
        tvUserName.setText(userName);

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
