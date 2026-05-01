package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeDashboardActivity extends AppCompatActivity {

    private TextView tvUserName, tvCaloriesLeft, tvDailyGoal, tvEaten;
    private TextView tvProteinValue, tvCarbsValue, tvFatValue;
    private ProgressBar calorieProgress, pbProtein, pbCarbs, pbFat;
    private CheckBox cbBreakfast, cbWater;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration userListener, logListener;
    private String todayDate;

    // Targets from Profile
    private int goalKcal = 2000;
    private int goalProtein = 100, goalCarbs = 200, goalFat = 70;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        initViews();
        setupRealtimeListeners();
        setupClickListeners();
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvCaloriesLeft = findViewById(R.id.calories_left);
        tvDailyGoal = findViewById(R.id.daily_goal_value);
        tvEaten = findViewById(R.id.eaten_label);
        calorieProgress = findViewById(R.id.calorieProgress);
        
        tvProteinValue = findViewById(R.id.tvProteinValue);
        tvCarbsValue = findViewById(R.id.tvCarbsValue);
        tvFatValue = findViewById(R.id.tvFatValue);
        
        pbProtein = findViewById(R.id.pbProtein);
        pbCarbs = findViewById(R.id.pbCarbs);
        pbFat = findViewById(R.id.pbFat);
        
        cbBreakfast = findViewById(R.id.cbBreakfast);
        cbWater = findViewById(R.id.cbWater);

        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        tvUserName.setText(sharedPref.getString("userName", "User"));

        // Navigation
        findViewById(R.id.navScan).setOnClickListener(v -> startActivity(new Intent(this, ScanFoodActivity.class)));
        findViewById(R.id.navMeals).setOnClickListener(v -> startActivity(new Intent(this, MealPlannerActivity.class)));
        findViewById(R.id.navAI).setOnClickListener(v -> startActivity(new Intent(this, ChatbotActivity.class)));
        findViewById(R.id.navProgress).setOnClickListener(v -> startActivity(new Intent(this, ProgressAnalyticsActivity.class)));
        findViewById(R.id.settingsIcon).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.profileIcon).setOnClickListener(v -> startActivity(new Intent(this, ProfileSetupActivity.class)));
    }

    private void setupClickListeners() {
        cbWater.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mAuth.getCurrentUser() == null) return;
            Map<String, Object> update = new HashMap<>();
            update.put("waterTaskCompleted", isChecked);
            db.collection("users").document(mAuth.getCurrentUser().getUid())
                    .collection("logs").document(todayDate).set(update, SetOptions.merge());
        });
    }

    private void setupRealtimeListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // 1. Listen to User Profile for Targets
        userListener = db.collection("users").document(currentUser.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String name = snapshot.getString("name");
                        if (name != null) tvUserName.setText(name);
                        
                        Long tKcal = snapshot.getLong("targetKcal");
                        Long tP = snapshot.getLong("targetProtein");
                        Long tC = snapshot.getLong("targetCarbs");
                        Long tF = snapshot.getLong("targetFat");

                        if (tKcal != null) goalKcal = tKcal.intValue();
                        if (tP != null) goalProtein = tP.intValue();
                        if (tC != null) goalCarbs = tC.intValue();
                        if (tF != null) goalFat = tF.intValue();

                        updateMacroUI(0, 0, 0, 0); // Trigger UI update with current targets
                    }
                });

        // 2. Listen to Today's Progress
        logListener = db.collection("users").document(currentUser.getUid())
                .collection("logs").document(todayDate)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Long consumed = snapshot.getLong("consumedKcal");
                        Long p = snapshot.getLong("consumedProtein");
                        Long c = snapshot.getLong("consumedCarbs");
                        Long f = snapshot.getLong("consumedFat");
                        
                        int cons = (consumed != null) ? consumed.intValue() : 0;
                        int protein = (p != null) ? p.intValue() : 0;
                        int carbs = (c != null) ? c.intValue() : 0;
                        int fat = (f != null) ? f.intValue() : 0;

                        updateMacroUI(cons, protein, carbs, fat);

                        List<String> breakfastList = (List<String>) snapshot.get("foods_Breakfast");
                        cbBreakfast.setChecked(breakfastList != null && !breakfastList.isEmpty());
                        
                        Boolean waterDone = snapshot.getBoolean("waterTaskCompleted");
                        if (waterDone != null) cbWater.setChecked(waterDone);
                    }
                });
    }

    private void updateMacroUI(int c, int p, int carbs, int f) {
        int left = Math.max(0, goalKcal - c);
        tvDailyGoal.setText(getString(R.string.daily_goal_value_placeholder, goalKcal));
        tvEaten.setText(getString(R.string.eaten_label_placeholder, c));
        tvCaloriesLeft.setText(String.valueOf(left));
        
        if (goalKcal > 0) calorieProgress.setProgress((int) (((float) c / goalKcal) * 100));

        tvProteinValue.setText(getString(R.string.protein_value_placeholder, p));
        pbProtein.setMax(goalProtein);
        pbProtein.setProgress(p);

        tvCarbsValue.setText(getString(R.string.carbs_value_placeholder, carbs));
        pbCarbs.setMax(goalCarbs);
        pbCarbs.setProgress(carbs);

        tvFatValue.setText(getString(R.string.fat_value_placeholder, f));
        pbFat.setMax(goalFat);
        pbFat.setProgress(f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        if (logListener != null) logListener.remove();
    }
}
