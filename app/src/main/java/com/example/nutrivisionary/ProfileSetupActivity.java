package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private ShapeableImageView ivProfilePhoto;
    private FloatingActionButton fabEditPhoto;
    private TextInputEditText etName, etAge, etWeight, etHeight;
    private ChipGroup cgGender, cgActivity, cgGoal;
    private MaterialButton saveProfileBtn;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        fabEditPhoto = findViewById(R.id.fabEditPhoto);
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etWeight = findViewById(R.id.etWeight);
        etHeight = findViewById(R.id.etHeight);
        cgGender = findViewById(R.id.cgGender);
        cgActivity = findViewById(R.id.cgActivity);
        cgGoal = findViewById(R.id.cgGoal);
        saveProfileBtn = findViewById(R.id.saveProfileBtn);

        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        etName.setText(sharedPref.getString("userName", ""));

        fabEditPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        saveProfileBtn.setOnClickListener(v -> {
            if (validateInputs()) {
                saveProfileToFirebase();
            }
        });
    }

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    ivProfilePhoto.setImageURI(imageUri);
                }
            }
    );

    private void saveProfileToFirebase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        saveProfileBtn.setEnabled(false);

        String name = etName.getText().toString().trim();
        int age = Integer.parseInt(etAge.getText().toString().trim());
        double weight = Double.parseDouble(etWeight.getText().toString().trim());
        double height = Double.parseDouble(etHeight.getText().toString().trim());

        String gender = getSelectedChipText(cgGender);
        String activityLevel = getSelectedChipText(cgActivity);
        String goal = getSelectedChipText(cgGoal);

        // --- NUTRITION LOGIC (The "Real App" part) ---
        // 1. Calculate BMR (Mifflin-St Jeor)
        double bmr;
        if (gender.equalsIgnoreCase("Male")) {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }

        // 2. Adjust for Activity Level
        double multiplier = 1.2; // Default
        if (activityLevel.contains("Moderate")) multiplier = 1.55;
        else if (activityLevel.contains("Active")) multiplier = 1.725;
        else if (activityLevel.contains("Athlete")) multiplier = 1.9;
        
        double tdee = bmr * multiplier;

        // 3. Adjust for Goal
        int targetCals = (int) tdee;
        if (goal.contains("Lose")) targetCals -= 500;
        else if (goal.contains("Gain")) targetCals += 500;

        // 4. Calculate Macros
        int protein = (int) (weight * 2); // ~2g per kg
        int fat = (int) ((targetCals * 0.25) / 9); // 25% cals from fat
        int carbs = (targetCals - (protein * 4) - (fat * 9)) / 4;

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("age", age);
        profile.put("weight", weight);
        profile.put("height", height);
        profile.put("gender", gender);
        profile.put("activityLevel", activityLevel);
        profile.put("goal", goal);
        profile.put("targetKcal", targetCals);
        profile.put("targetProtein", protein);
        profile.put("targetCarbs", carbs);
        profile.put("targetFat", fat);
        profile.put("isProfileComplete", true);

        db.collection("users").document(user.getUid())
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
                    sharedPref.edit().putString("userName", name).putBoolean("isLoggedIn", true).apply();
                    startActivity(new Intent(ProfileSetupActivity.this, HomeDashboardActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    saveProfileBtn.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getSelectedChipText(ChipGroup chipGroup) {
        int chipId = chipGroup.getCheckedChipId();
        if (chipId != View.NO_ID) {
            Chip chip = findViewById(chipId);
            return chip.getText().toString();
        }
        return "";
    }

    private boolean validateInputs() {
        if (etName.getText().toString().trim().isEmpty() || etAge.getText().toString().trim().isEmpty() ||
            etWeight.getText().toString().trim().isEmpty() || etHeight.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (cgGender.getCheckedChipId() == View.NO_ID || cgActivity.getCheckedChipId() == View.NO_ID || 
            cgGoal.getCheckedChipId() == View.NO_ID) {
            Toast.makeText(this, "Please select all options", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
