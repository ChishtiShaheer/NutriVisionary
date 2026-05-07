package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String todayDate;

    private TextView tvUserName, tvCaloriesLeft, tvLeftLabel, tvDailyGoal, tvEaten;
    private ProgressBar calorieProgress;

    private TextView tvProteinValue, tvCarbsValue, tvFatValue;
    private ProgressBar pbProtein, pbCarbs, pbFat;
    private MaterialCardView cardProtein, cardCarbs, cardFat;

    private TextView tvWaterValue, tvSleepValue;
    private ProgressBar pbWater, pbSleep;

    private TextView tvBreakfastInfo, tvLunchInfo, tvDinnerInfo, tvSnacksInfo;
    private LinearLayout layoutBreakfast, layoutLunch, layoutDinner, layoutSnacks;

    private int goalKcal = 2000;
    private double goalProtein = 100, goalCarbs = 200, goalFat = 70;
    private double goalWater = 2.0, goalSleep = 8.0;

    private List<String> breakfastList = new ArrayList<>(), lunchList = new ArrayList<>(), 
                         dinnerList = new ArrayList<>(), snacksList = new ArrayList<>();

    private ListenerRegistration userListener, logListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        initUI();
        setupRealtimeListeners();
    }

    private void initUI() {
        tvUserName       = findViewById(R.id.tvUserName);
        tvCaloriesLeft   = findViewById(R.id.calories_left);
        tvLeftLabel      = findViewById(R.id.kcal_left_label_view);
        tvDailyGoal      = findViewById(R.id.daily_goal_value);
        tvEaten          = findViewById(R.id.eaten_label);
        calorieProgress  = findViewById(R.id.calorieProgress);

        tvProteinValue   = findViewById(R.id.tvProteinValue);
        tvCarbsValue     = findViewById(R.id.tvCarbsValue);
        tvFatValue       = findViewById(R.id.tvFatValue);
        pbProtein        = findViewById(R.id.pbProtein);
        pbCarbs          = findViewById(R.id.pbCarbs);
        pbFat            = findViewById(R.id.pbFat);
        cardProtein      = findViewById(R.id.cardProtein);
        cardCarbs        = findViewById(R.id.cardCarbs);
        cardFat          = findViewById(R.id.cardFat);

        tvWaterValue     = findViewById(R.id.tvWaterValue);
        tvSleepValue     = findViewById(R.id.tvSleepValue);
        pbWater          = findViewById(R.id.pbWater);
        pbSleep          = findViewById(R.id.pbSleep);

        tvBreakfastInfo  = findViewById(R.id.tvBreakfastInfo);
        tvLunchInfo      = findViewById(R.id.tvLunchInfo);
        tvDinnerInfo     = findViewById(R.id.tvDinnerInfo);
        tvSnacksInfo     = findViewById(R.id.tvSnacksInfo);

        layoutBreakfast  = findViewById(R.id.layoutBreakfast);
        layoutLunch      = findViewById(R.id.layoutLunch);
        layoutDinner     = findViewById(R.id.layoutDinner);
        layoutSnacks     = findViewById(R.id.layoutSnacks);

        findViewById(R.id.navScan).setOnClickListener(v ->
                startActivity(new Intent(this, ScanFoodActivity.class)));
        findViewById(R.id.navAI).setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));
        findViewById(R.id.navProgress).setOnClickListener(v ->
                startActivity(new Intent(this, ProgressAnalyticsActivity.class)));
        findViewById(R.id.settingsIcon).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.profileIcon).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileSetupActivity.class)));

        findViewById(R.id.btnAddBreakfast).setOnClickListener(v -> openManualLog(getString(R.string.breakfast)));
        findViewById(R.id.btnAddLunch).setOnClickListener(v -> openManualLog(getString(R.string.lunch)));
        findViewById(R.id.btnAddDinner).setOnClickListener(v -> openManualLog(getString(R.string.dinner)));
        findViewById(R.id.btnAddSnacks).setOnClickListener(v -> openManualLog(getString(R.string.snacks)));

        layoutBreakfast.setOnClickListener(v -> showMealDetails(getString(R.string.breakfast), breakfastList));
        layoutLunch.setOnClickListener(v     -> showMealDetails(getString(R.string.lunch), lunchList));
        layoutDinner.setOnClickListener(v    -> showMealDetails(getString(R.string.dinner), dinnerList));
        layoutSnacks.setOnClickListener(v    -> showMealDetails(getString(R.string.snacks), snacksList));

        findViewById(R.id.btnQuickWater250).setOnClickListener(v -> logWaterIncrement(0.25));
        findViewById(R.id.btnQuickWater500).setOnClickListener(v -> logWaterIncrement(0.50));
        findViewById(R.id.btnCustomWater).setOnClickListener(v -> showCustomWaterDialog());

        findViewById(R.id.btnLogSleep).setOnClickListener(v -> showSleepTimePicker());
        findViewById(R.id.cardSleep).setOnClickListener(v -> showSleepTimePicker());
    }

    private void openManualLog(String mealType) {
        Intent intent = new Intent(this, ManualFoodLogActivity.class);
        intent.putExtra("mealType", mealType);
        startActivity(intent);
    }

    private void showMealDetails(String title, List<String> foods) {
        if (foods == null || foods.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_logged, title), Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String f : foods) sb.append("• ").append(f).append("\n");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(sb.toString().trim())
                .setPositiveButton(getString(R.string.close), null)
                .setNeutralButton(getString(R.string.add_more), (d, w) -> openManualLog(title))
                .show();
    }

    private void logWaterIncrement(double litres) {
        updateHealthLog("consumedWater", litres, true);
    }

    private void showCustomWaterDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.log_water))
                .setView(input)
                .setPositiveButton(getString(R.string.log), (d, w) -> {
                    try {
                        double ml = Double.parseDouble(input.getText().toString());
                        updateHealthLog("consumedWater", ml / 1000.0, true);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showSleepTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(8)
                .setTitleText(getString(R.string.log_sleep_duration))
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
        update.put(field, additive ? FieldValue.increment(value) : value);
        db.collection("users").document(user.getUid())
                .collection("logs").document(todayDate)
                .set(update, SetOptions.merge());
    }

    private void setupRealtimeListeners() {
        FirebaseUser current = mAuth.getCurrentUser();
        if (current == null) return;

        userListener = db.collection("users").document(current.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    String name = snapshot.getString("name");
                    if (name != null) tvUserName.setText(name);
                    goalKcal    = getInt(snapshot, "targetKcal", 2000);
                    goalProtein = getDouble(snapshot, "targetProtein", 100.0);
                    goalCarbs   = getDouble(snapshot, "targetCarbs", 200.0);
                    goalFat     = getDouble(snapshot, "targetFat", 70.0);
                    goalWater   = getDouble(snapshot, "waterGoal", 2.0);
                    goalSleep   = getDouble(snapshot, "sleepGoal", 8.0);
                    tvDailyGoal.setText(getString(R.string.daily_goal_value_placeholder, goalKcal));
                });

        logListener = db.collection("users").document(current.getUid())
                .collection("logs").document(todayDate)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        int consumed  = getInt(snapshot, "consumedKcal", 0);
                        double p      = getDouble(snapshot, "consumedProtein", 0.0);
                        double c      = getDouble(snapshot, "consumedCarbs", 0.0);
                        double f      = getDouble(snapshot, "consumedFat", 0.0);
                        double water  = getDouble(snapshot, "consumedWater", 0.0);
                        double sleep  = getDouble(snapshot, "hoursSleep", 0.0);

                        updateProgressUI(consumed, p, c, f, water, sleep);

                        breakfastList = (List<String>) snapshot.get("foods_Breakfast");
                        lunchList     = (List<String>) snapshot.get("foods_Lunch");
                        dinnerList    = (List<String>) snapshot.get("foods_Dinner");
                        snacksList    = (List<String>) snapshot.get("foods_Snacks");
                        updateMealUI(breakfastList, lunchList, dinnerList, snacksList);
                    } else {
                        updateProgressUI(0, 0, 0, 0, 0, 0);
                        updateMealUI(null, null, null, null);
                    }
                });
    }

    private void updateProgressUI(int consumed, double p, double carbs, double f, double water, double sleep) {
        int cLeft = goalKcal - consumed;
        tvCaloriesLeft.setText(String.valueOf(Math.abs(cLeft)));
        tvEaten.setText(getString(R.string.eaten_label_placeholder, consumed));
        calorieProgress.setProgress(goalKcal > 0 ? Math.min(100, (int) ((float) consumed / goalKcal * 100)) : 0);

        if (cLeft < 0) {
            tvCaloriesLeft.setTextColor(ContextCompat.getColor(this, R.color.macroFat));
            if (tvLeftLabel != null) tvLeftLabel.setText(getString(R.string.kcal_over_label));
        } else {
            tvCaloriesLeft.setTextColor(ContextCompat.getColor(this, R.color.white));
            if (tvLeftLabel != null) tvLeftLabel.setText(getString(R.string.kcal_left_label));
        }

        updateMacroCard(tvProteinValue, pbProtein, cardProtein, p,     goalProtein, getString(R.string.g_left), R.color.macroProtein);
        updateMacroCard(tvCarbsValue,   pbCarbs,   cardCarbs,   carbs, goalCarbs,   getString(R.string.g_left), R.color.macroCarbs);
        updateMacroCard(tvFatValue,     pbFat,     cardFat,     f,     goalFat,     getString(R.string.g_left), R.color.macroFat);

        tvWaterValue.setText(getString(R.string.water_progress_format, water, goalWater));
        pbWater.setMax((int) (goalWater * 100));
        pbWater.setProgress((int) (water * 100));

        tvSleepValue.setText(getString(R.string.sleep_progress_format, sleep, goalSleep));
        pbSleep.setMax((int) (goalSleep * 10));
        pbSleep.setProgress((int) (sleep * 10));
    }

    private void updateMacroCard(TextView tv, ProgressBar pb, MaterialCardView card,
                                 double consumed, double goal, String label, int colorRes) {
        double left = goal - consumed;
        if (consumed > goal) {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.macroFat));
            tv.setTextColor(ContextCompat.getColor(this, R.color.macroFat));
            tv.setText(getString(R.string.macro_over_placeholder, (int)Math.abs(left)));
        } else {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.mealStroke));
            tv.setTextColor(ContextCompat.getColor(this, colorRes));
            tv.setText((int)Math.max(0, left) + " " + label);
        }
        pb.setMax((int) goal);
        pb.setProgress((int) consumed);
    }

    private void updateMealUI(List<String> breakfast, List<String> lunch, List<String> dinner, List<String> snacks) {
        tvBreakfastInfo.setText(formatMealList(breakfast));
        tvLunchInfo.setText(formatMealList(lunch));
        tvDinnerInfo.setText(formatMealList(dinner));
        tvSnacksInfo.setText(formatMealList(snacks));
    }

    private String formatMealList(List<String> list) {
        if (list == null || list.isEmpty()) return getString(R.string.not_logged);
        return String.join(", ", list);
    }

    private double getDouble(DocumentSnapshot doc, String field, double def) {
        Number n = (Number) doc.get(field);
        return n != null ? n.doubleValue() : def;
    }

    private int getInt(DocumentSnapshot doc, String field, int def) {
        Number n = (Number) doc.get(field);
        return n != null ? n.intValue() : def;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        if (logListener != null)  logListener.remove();
    }
}
