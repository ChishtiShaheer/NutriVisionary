package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
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

    private TextView tvUserName, tvCaloriesLeft, tvDailyGoal, tvEaten, tvLeftLabel;
    private TextView tvProteinValue, tvCarbsValue, tvFatValue;
    private ProgressBar calorieProgress, pbProtein, pbCarbs, pbFat;
    private MaterialCardView cardProtein, cardCarbs, cardFat;
    
    private TextView tvWaterValue, tvSleepValue, tvShieldStatus;
    private ProgressBar pbWater, pbSleep;
    private ImageView ivShield;
    
    private TextView tvBreakfastInfo, tvLunchInfo, tvDinnerInfo, tvSnacksInfo;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration userListener, logListener;
    private String todayDate;

    private int goalKcal = 2000;
    private double goalProtein = 100, goalCarbs = 200, goalFat = 70;
    private double goalWater = 2.0, goalSleep = 8.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        initViews();
        setupRealtimeListeners();
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvCaloriesLeft = findViewById(R.id.calories_left);
        tvDailyGoal = findViewById(R.id.daily_goal_value);
        tvEaten = findViewById(R.id.eaten_label);
        tvLeftLabel = findViewById(R.id.kcal_left_label_view);
        calorieProgress = findViewById(R.id.calorieProgress);
        
        tvProteinValue = findViewById(R.id.tvProteinValue);
        tvCarbsValue = findViewById(R.id.tvCarbsValue);
        tvFatValue = findViewById(R.id.tvFatValue);
        pbProtein = findViewById(R.id.pbProtein);
        pbCarbs = findViewById(R.id.pbCarbs);
        pbFat = findViewById(R.id.pbFat);
        cardProtein = findViewById(R.id.cardProtein);
        cardCarbs = findViewById(R.id.cardCarbs);
        cardFat = findViewById(R.id.cardFat);

        tvWaterValue = findViewById(R.id.tvWaterValue);
        tvSleepValue = findViewById(R.id.tvSleepValue);
        pbWater = findViewById(R.id.pbWater);
        pbSleep = findViewById(R.id.pbSleep);
        ivShield = findViewById(R.id.ivShield);
        tvShieldStatus = findViewById(R.id.tvShieldStatus);

        tvBreakfastInfo = findViewById(R.id.tvBreakfastInfo);
        tvLunchInfo = findViewById(R.id.tvLunchInfo);
        tvDinnerInfo = findViewById(R.id.tvDinnerInfo);
        tvSnacksInfo = findViewById(R.id.tvSnacksInfo);

        // Navigation
        findViewById(R.id.navScan).setOnClickListener(v -> startActivity(new Intent(this, ScanFoodActivity.class)));
        findViewById(R.id.navAI).setOnClickListener(v -> startActivity(new Intent(this, ChatbotActivity.class)));
        findViewById(R.id.navProgress).setOnClickListener(v -> startActivity(new Intent(this, ProgressAnalyticsActivity.class)));
        findViewById(R.id.settingsIcon).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.profileIcon).setOnClickListener(v -> startActivity(new Intent(this, ProfileSetupActivity.class)));

        // Manual Meal Logging
        findViewById(R.id.btnAddBreakfast).setOnClickListener(v -> openManualLog("Breakfast"));
        findViewById(R.id.btnAddLunch).setOnClickListener(v -> openManualLog("Lunch"));
        findViewById(R.id.btnAddDinner).setOnClickListener(v -> openManualLog("Dinner"));
        findViewById(R.id.btnAddSnacks).setOnClickListener(v -> openManualLog("Snacks"));

        // Optimized Input Modalities
        findViewById(R.id.btnQuickWater250).setOnClickListener(v -> updateHealthLog("consumedWater", 0.25, true));
        findViewById(R.id.btnQuickWater500).setOnClickListener(v -> updateHealthLog("consumedWater", 0.50, true));
        
        findViewById(R.id.cardSleep).setOnClickListener(v -> showSleepTimePicker());
    }

    private void openManualLog(String mealType) {
        Intent intent = new Intent(this, ManualFoodLogActivity.class);
        intent.putExtra("mealType", mealType);
        startActivity(intent);
    }

    private void showSleepTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(8)
                .setMinute(0)
                .setTitleText("Log Sleep Duration")
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            double hours = picker.getHour() + (picker.getMinute() / 60.0);
            updateHealthLog("hoursSleep", hours, false);
        });

        picker.show(getSupportFragmentManager(), "SLEEP_PICKER");
    }

    private void updateHealthLog(String field, double value, boolean additive) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> update = new HashMap<>();
        if (additive) {
            update.put(field, FieldValue.increment(value));
        } else {
            update.put(field, value);
        }

        db.collection("users").document(user.getUid()).collection("logs").document(todayDate)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    String msg = additive ? String.format(Locale.getDefault(), "+%.2f added!", value) : "Sleep logged!";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("HomeDashboard", "Log failed", e);
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupRealtimeListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        userListener = db.collection("users").document(currentUser.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        String name = snapshot.getString("name");
                        if (name != null) tvUserName.setText(name);
                        
                        Number tk = (Number) snapshot.get("targetKcal");
                        goalKcal = tk != null ? tk.intValue() : 2000;
                        
                        goalProtein = getDouble(snapshot, "targetProtein", 100.0);
                        goalCarbs = getDouble(snapshot, "targetCarbs", 200.0);
                        goalFat = getDouble(snapshot, "targetFat", 70.0);
                        goalWater = getDouble(snapshot, "waterGoal", 2.0);
                        goalSleep = getDouble(snapshot, "sleepGoal", 8.0);

                        updateUI();
                    }
                });

        logListener = db.collection("users").document(currentUser.getUid())
                .collection("logs").document(todayDate)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        Number ck = (Number) snapshot.get("consumedKcal");
                        int consumed = ck != null ? ck.intValue() : 0;
                        
                        double p = getDouble(snapshot, "consumedProtein", 0.0);
                        double c = getDouble(snapshot, "consumedCarbs", 0.0);
                        double f = getDouble(snapshot, "consumedFat", 0.0);
                        double water = getDouble(snapshot, "consumedWater", 0.0);
                        double sleep = getDouble(snapshot, "hoursSleep", 0.0);
                        double vitC = getDouble(snapshot, "vitC", 0.0);
                        double vitD = getDouble(snapshot, "vitD", 0.0);
                        double zinc = getDouble(snapshot, "zinc", 0.0);

                        updateProgressUI(consumed, p, c, f, water, sleep, vitC, vitD, zinc);
                        updateMealUI(snapshot.get("foods_Breakfast"), snapshot.get("foods_Lunch"), snapshot.get("foods_Dinner"), snapshot.get("foods_Snacks"));
                    } else {
                        updateProgressUI(0, 0, 0, 0, 0, 0, 0, 0, 0);
                        updateMealUI(null, null, null, null);
                    }
                });
    }

    private double getDouble(com.google.firebase.firestore.DocumentSnapshot doc, String field, double def) {
        Number n = (Number) doc.get(field);
        return n != null ? n.doubleValue() : def;
    }

    private void updateUI() {
        tvDailyGoal.setText(getString(R.string.daily_goal_value_placeholder, goalKcal));
    }

    private void updateProgressUI(int c, double p, double carbs, double f, double water, double sleep, double vitC, double vitD, double zinc) {
        int cLeft = goalKcal - c;
        tvCaloriesLeft.setText(String.valueOf(Math.abs(cLeft)));
        tvEaten.setText(getString(R.string.eaten_label_placeholder, c));
        calorieProgress.setProgress(goalKcal > 0 ? Math.min(100, (int) (((float) c / goalKcal) * 100)) : 0);

        if (cLeft < 0) {
            tvCaloriesLeft.setTextColor(ContextCompat.getColor(this, R.color.macroFat));
            if (tvLeftLabel != null) tvLeftLabel.setText(R.string.kcal_over_label);
        } else {
            tvCaloriesLeft.setTextColor(ContextCompat.getColor(this, R.color.white));
            if (tvLeftLabel != null) tvLeftLabel.setText(R.string.kcal_left_label);
        }

        updateMacroCard(tvProteinValue, pbProtein, cardProtein, p, goalProtein, R.string.protein_left_placeholder, R.color.macroProtein);
        updateMacroCard(tvCarbsValue, pbCarbs, cardCarbs, carbs, goalCarbs, R.string.carbs_left_placeholder, R.color.macroCarbs);
        updateMacroCard(tvFatValue, pbFat, cardFat, f, goalFat, R.string.fat_left_placeholder, R.color.macroFat);

        tvWaterValue.setText(String.format(Locale.getDefault(), "%.2f / %.1f L", water, goalWater));
        pbWater.setMax((int) (goalWater * 100));
        pbWater.setProgress((int) (water * 100));

        tvSleepValue.setText(String.format(Locale.getDefault(), "%.1f / %.1f hrs", sleep, goalSleep));
        pbSleep.setMax((int) (goalSleep * 10));
        pbSleep.setProgress((int) (sleep * 10));

        // Minerals logic check: VitC: 75, VitD: 15, Zinc: 8
        boolean isProtected = (vitC >= 75 && vitD >= 15 && zinc >= 8);
        if (isProtected) {
            ivShield.setImageResource(R.drawable.ic_shield_ok);
            ivShield.setColorFilter(ContextCompat.getColor(this, R.color.macroProtein));
            tvShieldStatus.setText(getString(R.string.status_protected));
            tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.macroProtein));
        } else {
            ivShield.setImageResource(R.drawable.ic_shield_alert);
            ivShield.setColorFilter(ContextCompat.getColor(this, R.color.macroFat));
            tvShieldStatus.setText(getString(R.string.status_vulnerable));
            tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.macroFat));
        }
    }

    private void updateMacroCard(TextView tv, ProgressBar pb, MaterialCardView card, double consumed, double goal, int stringRes, int normalColorRes) {
        double left = goal - consumed;
        if (consumed > goal) {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.macroFat));
            card.setStrokeWidth(4);
            tv.setTextColor(ContextCompat.getColor(this, R.color.macroFat));
            tv.setText(getString(R.string.macro_over_placeholder, (int) Math.abs(left)));
        } else {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.mealStroke));
            card.setStrokeWidth(2);
            tv.setTextColor(ContextCompat.getColor(this, normalColorRes));
            tv.setText(getString(stringRes, (int) Math.max(0, left)));
        }
        pb.setMax((int) goal);
        pb.setProgress((int) consumed);
    }

    private void updateMealUI(Object breakfast, Object lunch, Object dinner, Object snacks) {
        tvBreakfastInfo.setText(formatMealList(breakfast));
        tvLunchInfo.setText(formatMealList(lunch));
        tvDinnerInfo.setText(formatMealList(dinner));
        tvSnacksInfo.setText(formatMealList(snacks));
    }

    private String formatMealList(Object foodObj) {
        if (foodObj instanceof List) {
            List<String> list = (List<String>) foodObj;
            if (list.isEmpty()) return getString(R.string.not_logged);
            return String.join(", ", list);
        }
        return getString(R.string.not_logged);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        if (logListener != null) logListener.remove();
    }
}
