package com.example.nutrivisionary;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MealPlannerActivity extends AppCompatActivity {

    private TextView tvSelectedGoal, tvRemainingKcal, tvTargetKcalValue;
    private Slider kcalSlider;
    private CircularProgressIndicator remainingKcalProgress;
    private MaterialButton btnLockCalories, btnChangeGoal, btnGenerateSelfPlan;
    
    private int targetKcal = 2000;
    private int consumedKcal = 0;
    private int consumedProtein = 0, consumedCarbs = 0, consumedFat = 0;
    private boolean isLocked = false;
    private String todayDate;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final String[] goals = {"Weight Loss", "Muscle Building", "Maintain Weight", "Keto Diet", "Vegan Athlete"};

    private final Map<String, List<String>> mealFoods = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_planner);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        tvSelectedGoal = findViewById(R.id.tvSelectedGoal);
        tvRemainingKcal = findViewById(R.id.tvRemainingKcal);
        tvTargetKcalValue = findViewById(R.id.tvTargetKcalValue);
        kcalSlider = findViewById(R.id.kcalSlider);
        remainingKcalProgress = findViewById(R.id.remainingKcalProgress);
        btnLockCalories = findViewById(R.id.btnLockCalories);
        btnChangeGoal = findViewById(R.id.btnChangeGoal);
        btnGenerateSelfPlan = findViewById(R.id.btnGenerateSelfPlan);

        mealFoods.put("Breakfast", new ArrayList<>());
        mealFoods.put("Lunch", new ArrayList<>());
        mealFoods.put("Snacks", new ArrayList<>());
        mealFoods.put("Dinner", new ArrayList<>());

        MaterialCardView goalCard = findViewById(R.id.goalCard);
        View.OnClickListener goalClickListener = v -> showGoalSelectionDialog();
        goalCard.setOnClickListener(goalClickListener);
        btnChangeGoal.setOnClickListener(goalClickListener);

        kcalSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!isLocked) {
                targetKcal = (int) value;
                updateCalorieUI();
            }
        });

        btnLockCalories.setOnClickListener(v -> toggleLock());
        btnGenerateSelfPlan.setOnClickListener(v -> saveDayToFirebase());

        setupMealCards();
        loadUserData();
    }

    private void showGoalSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Your Goal");
        builder.setItems(goals, (dialog, which) -> {
            String newGoal = goals[which];
            tvSelectedGoal.setText(newGoal);
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                db.collection("users").document(currentUser.getUid())
                        .update("goal", newGoal);
            }
        });
        builder.show();
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String goal = documentSnapshot.getString("goal");
                        if (goal != null) tvSelectedGoal.setText(goal);
                        loadTodayLogs();
                    }
                });
    }

    private void loadTodayLogs() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .collection("logs").document(todayDate).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long target = doc.getLong("targetKcal");
                        Long consumed = doc.getLong("consumedKcal");
                        Long p = doc.getLong("consumedProtein");
                        Long c = doc.getLong("consumedCarbs");
                        Long f = doc.getLong("consumedFat");

                        if (target != null) {
                            targetKcal = target.intValue();
                            kcalSlider.setValue((float) targetKcal);
                        }
                        if (consumed != null) consumedKcal = consumed.intValue();
                        if (p != null) consumedProtein = p.intValue();
                        if (c != null) consumedCarbs = c.intValue();
                        if (f != null) consumedFat = f.intValue();
                        
                        Boolean locked = doc.getBoolean("isLocked");
                        if (locked != null && locked) forceLockState();

                        loadMealList(doc, "Breakfast", findViewById(R.id.cardBreakfast));
                        loadMealList(doc, "Lunch", findViewById(R.id.cardLunch));
                        loadMealList(doc, "Snacks", findViewById(R.id.cardSnacks));
                        loadMealList(doc, "Dinner", findViewById(R.id.cardDinner));
                        
                        updateCalorieUI();
                    }
                });
    }

    private void loadMealList(com.google.firebase.firestore.DocumentSnapshot doc, String mealType, View cardView) {
        @SuppressWarnings("unchecked")
        List<String> foods = (List<String>) doc.get("foods_" + mealType);
        if (foods != null) {
            mealFoods.put(mealType, foods);
            LinearLayout layout = cardView.findViewById(R.id.layoutFoodItems);
            layout.removeAllViews();
            layout.setVisibility(View.VISIBLE);
            for (String food : foods) {
                addFoodView(layout, food);
            }
        }
    }

    private void forceLockState() {
        isLocked = true;
        kcalSlider.setEnabled(false);
        btnLockCalories.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_secure));
        btnLockCalories.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.royalBlue)));
        btnGenerateSelfPlan.setVisibility(View.VISIBLE);
    }

    private void toggleLock() {
        if (!isLocked) {
            isLocked = true;
            kcalSlider.setEnabled(false);
            btnLockCalories.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_secure));
            btnLockCalories.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.royalBlue)));
            btnGenerateSelfPlan.setVisibility(View.VISIBLE);
            saveDayToFirebase();
        } else {
            isLocked = false;
            kcalSlider.setEnabled(true);
            consumedKcal = 0;
            consumedProtein = 0; consumedCarbs = 0; consumedFat = 0;
            btnLockCalories.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_partial_secure));
            btnLockCalories.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.slateGray)));
            btnGenerateSelfPlan.setVisibility(View.GONE);
            
            for (String key : mealFoods.keySet()) {
                List<String> foods = mealFoods.get(key);
                if (foods != null) foods.clear();
            }
            resetMealCards();
            updateCalorieUI();
            saveDayToFirebase();
        }
    }

    private void saveDayToFirebase() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> dayLog = new HashMap<>();
        dayLog.put("targetKcal", targetKcal);
        dayLog.put("consumedKcal", consumedKcal);
        dayLog.put("consumedProtein", consumedProtein);
        dayLog.put("consumedCarbs", consumedCarbs);
        dayLog.put("consumedFat", consumedFat);
        dayLog.put("isLocked", isLocked);
        dayLog.put("lastUpdated", new Date());

        for (String mealType : mealFoods.keySet()) {
            dayLog.put("foods_" + mealType, mealFoods.get(mealType));
        }

        db.collection("users").document(currentUser.getUid())
                .collection("logs").document(todayDate)
                .set(dayLog, SetOptions.merge())
                .addOnFailureListener(e -> Log.e("MealPlanner", "Save failed", e));
    }

    private void setupMealCards() {
        setupMealCard(findViewById(R.id.cardBreakfast), "Breakfast");
        setupMealCard(findViewById(R.id.cardLunch), "Lunch");
        setupMealCard(findViewById(R.id.cardSnacks), "Snacks");
        setupMealCard(findViewById(R.id.cardDinner), "Dinner");
    }

    private void setupMealCard(View cardView, String title) {
        TextView tvTitle = cardView.findViewById(R.id.tvMealTitle);
        View btnAddFood = cardView.findViewById(R.id.btnAddFood);
        tvTitle.setText(title);

        btnAddFood.setOnClickListener(v -> {
            if (isLocked) {
                showFoodSelectionDialog(cardView, title);
            } else {
                Toast.makeText(this, "Please lock your target calories first!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFoodSelectionDialog(View cardView, String mealType) {
        // Real-world dummy data with macros
        String[] foods = {"Apple (95 kcal, 25g C)", "Chicken Breast (165 kcal, 31g P)", "Brown Rice (215 kcal, 45g C)", "Greek Yogurt (100 kcal, 10g P)", "Avocado (160 kcal, 15g F)"};
        int[] calories = {95, 165, 215, 100, 160};
        int[] protein = {0, 31, 5, 10, 2};
        int[] carbs = {25, 0, 45, 4, 9};
        int[] fat = {0, 4, 1, 0, 15};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add to " + mealType);
        builder.setItems(foods, (dialog, which) -> {
            addFoodToCard(cardView, foods[which], calories[which], protein[which], carbs[which], fat[which], mealType);
        });
        builder.show();
    }

    private void addFoodToCard(View cardView, String foodDisplay, int kcal, int p, int c, int f, String mealType) {
        consumedKcal += kcal;
        consumedProtein += p;
        consumedCarbs += c;
        consumedFat += f;
        updateCalorieUI();

        List<String> foods = mealFoods.get(mealType);
        if (foods != null) foods.add(foodDisplay);

        LinearLayout layoutFoodItems = cardView.findViewById(R.id.layoutFoodItems);
        layoutFoodItems.setVisibility(View.VISIBLE);
        addFoodView(layoutFoodItems, foodDisplay);

        TextView tvMealCalories = cardView.findViewById(R.id.tvMealCalories);
        tvMealCalories.setVisibility(View.VISIBLE);
        String currentCalsStr = tvMealCalories.getText().toString().replace(" kcal", "");
        int currentCals = currentCalsStr.isEmpty() || currentCalsStr.equals("0") ? 0 : Integer.parseInt(currentCalsStr);
        tvMealCalories.setText(String.format(Locale.getDefault(), "%d kcal", currentCals + kcal));
        
        saveDayToFirebase();
    }

    private void addFoodView(LinearLayout layout, String text) {
        TextView tvFoodItem = new TextView(this);
        tvFoodItem.setText(String.format("• %s", text));
        tvFoodItem.setTextColor(ContextCompat.getColor(this, R.color.grayText));
        tvFoodItem.setTextSize(14);
        layout.addView(tvFoodItem);
    }

    private void resetMealCards() {
        resetMealCard(findViewById(R.id.cardBreakfast));
        resetMealCard(findViewById(R.id.cardLunch));
        resetMealCard(findViewById(R.id.cardSnacks));
        resetMealCard(findViewById(R.id.cardDinner));
    }

    private void resetMealCard(View cardView) {
        LinearLayout layoutFoodItems = cardView.findViewById(R.id.layoutFoodItems);
        layoutFoodItems.removeAllViews();
        layoutFoodItems.setVisibility(View.GONE);
        TextView tvMealCalories = cardView.findViewById(R.id.tvMealCalories);
        tvMealCalories.setText("0 kcal");
        tvMealCalories.setVisibility(View.GONE);
    }

    private void updateCalorieUI() {
        int remaining = targetKcal - consumedKcal;
        tvTargetKcalValue.setText(String.format(Locale.getDefault(), "%d kcal", targetKcal));
        tvRemainingKcal.setText(String.valueOf(Math.max(0, remaining)));
        float progress = ((float) consumedKcal / targetKcal) * 100;
        remainingKcalProgress.setProgress((int) progress);
    }
}
