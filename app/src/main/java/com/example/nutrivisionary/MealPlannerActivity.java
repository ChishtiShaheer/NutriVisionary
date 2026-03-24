package com.example.nutrivisionary;

import android.content.res.ColorStateList;
import android.os.Bundle;
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

public class MealPlannerActivity extends AppCompatActivity {

    private TextView tvSelectedGoal, tvRemainingKcal, tvTargetKcalValue;
    private Slider kcalSlider;
    private CircularProgressIndicator remainingKcalProgress;
    private MaterialButton btnLockCalories, btnChangeGoal, btnGenerateSelfPlan;
    
    private int targetKcal = 2000;
    private int consumedKcal = 0;
    private boolean isLocked = false;

    private String[] goals = {"Weight Loss", "Muscle Building", "Maintain Weight", "Keto Diet", "Vegan Athlete"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_planner);

        tvSelectedGoal = findViewById(R.id.tvSelectedGoal);
        tvRemainingKcal = findViewById(R.id.tvRemainingKcal);
        tvTargetKcalValue = findViewById(R.id.tvTargetKcalValue);
        kcalSlider = findViewById(R.id.kcalSlider);
        remainingKcalProgress = findViewById(R.id.remainingKcalProgress);
        btnLockCalories = findViewById(R.id.btnLockCalories);
        btnChangeGoal = findViewById(R.id.btnChangeGoal);
        btnGenerateSelfPlan = findViewById(R.id.btnGenerateSelfPlan);

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

        btnGenerateSelfPlan.setOnClickListener(v -> {
            Toast.makeText(this, "Success! Your custom meal plan is ready.", Toast.LENGTH_LONG).show();
        });

        setupMealCards();
        updateCalorieUI();
    }

    private void toggleLock() {
        if (!isLocked) {
            isLocked = true;
            kcalSlider.setEnabled(false);
            btnLockCalories.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_secure));
            btnLockCalories.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.royalBlue)));
            btnGenerateSelfPlan.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Calories Locked! You can now add food.", Toast.LENGTH_SHORT).show();
        } else {
            isLocked = false;
            kcalSlider.setEnabled(true);
            consumedKcal = 0;
            btnLockCalories.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_partial_secure));
            btnLockCalories.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.slateGray)));
            btnGenerateSelfPlan.setVisibility(View.GONE);
            resetMealCards();
            updateCalorieUI();
            Toast.makeText(this, "Data Reset & Unlocked", Toast.LENGTH_SHORT).show();
        }
    }

    private void showGoalSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Your Goal");
        builder.setItems(goals, (dialog, which) -> {
            tvSelectedGoal.setText(goals[which]);
        });
        builder.show();
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
                showFoodSelectionDialog(cardView);
            } else {
                Toast.makeText(this, "Please lock your target calories first!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFoodSelectionDialog(View cardView) {
        String[] foods = {"Apple (95 kcal)", "Chicken Breast (165 kcal)", "Brown Rice (215 kcal)", "Greek Yogurt (100 kcal)", "Salad (50 kcal)"};
        String[] foodNamesOnly = {"Apple", "Chicken Breast", "Brown Rice", "Greek Yogurt", "Salad"};
        int[] calories = {95, 165, 215, 100, 50};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Food");
        builder.setItems(foods, (dialog, which) -> {
            int remaining = targetKcal - consumedKcal;
            int foodKcal = calories[which];
            
            if (remaining <= 0) {
                Toast.makeText(this, "Calorie limit reached! Cannot add more.", Toast.LENGTH_SHORT).show();
            } else if (foodKcal > remaining) {
                Toast.makeText(this, "This item (" + foodKcal + " kcal) exceeds your remaining " + remaining + " kcal!", Toast.LENGTH_SHORT).show();
            } else {
                addFoodToCard(cardView, foodNamesOnly[which], foodKcal);
            }
        });
        builder.show();
    }

    private void addFoodToCard(View cardView, String foodName, int kcal) {
        consumedKcal += kcal;
        updateCalorieUI();

        LinearLayout layoutFoodItems = cardView.findViewById(R.id.layoutFoodItems);
        layoutFoodItems.setVisibility(View.VISIBLE);
        
        TextView tvFoodItem = new TextView(this);
        tvFoodItem.setText("• " + foodName + " (" + kcal + " kcal)");
        tvFoodItem.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        tvFoodItem.setTextSize(14);
        layoutFoodItems.addView(tvFoodItem);

        TextView tvMealCalories = cardView.findViewById(R.id.tvMealCalories);
        tvMealCalories.setVisibility(View.VISIBLE);
        
        String currentCalsStr = tvMealCalories.getText().toString().replace(" kcal", "");
        int currentCals = currentCalsStr.isEmpty() || currentCalsStr.equals("0") ? 0 : Integer.parseInt(currentCalsStr);
        tvMealCalories.setText((currentCals + kcal) + " kcal");
        
        Toast.makeText(this, "Added " + foodName, Toast.LENGTH_SHORT).show();
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
        tvTargetKcalValue.setText(targetKcal + " kcal");
        tvRemainingKcal.setText(String.valueOf(Math.max(0, remaining)));
        
        float progress = ((float) consumedKcal / targetKcal) * 100;
        remainingKcalProgress.setProgress((int) progress);
    }
}
