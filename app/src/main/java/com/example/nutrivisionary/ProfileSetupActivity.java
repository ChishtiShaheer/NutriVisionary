package com.example.nutrivisionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
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
    private TextInputEditText etName, etAge, etWeight, etHeight, etTargetWeight, etWaterGoal, etSleepGoal;
    private ChipGroup cgGender, cgActivity, cgGoal, cgDiet;
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
        etTargetWeight = findViewById(R.id.etTargetWeight);
        etWaterGoal = findViewById(R.id.etWaterGoal);
        etSleepGoal = findViewById(R.id.etSleepGoal);
        
        cgGender = findViewById(R.id.cgGender);
        cgActivity = findViewById(R.id.cgActivity);
        cgGoal = findViewById(R.id.cgGoal);
        cgDiet = findViewById(R.id.cgDiet);
        saveProfileBtn = findViewById(R.id.saveProfileBtn);

        SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
        String name = sharedPref.getString("userName", "");
        if (!name.isEmpty()) etName.setText(name);

        fabEditPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        saveProfileBtn.setOnClickListener(v -> {
            if (validateInputs()) {
                saveProfileToFirebase();
            }
        });

        loadUserProfile();
    }

    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                if (doc.contains("name")) etName.setText(doc.getString("name"));
                if (doc.contains("age")) etAge.setText(String.valueOf(doc.getLong("age")));
                if (doc.contains("weight")) etWeight.setText(String.valueOf(doc.get("weight")));
                if (doc.contains("height")) etHeight.setText(String.valueOf(doc.get("height")));
                if (doc.contains("targetWeight")) etTargetWeight.setText(String.valueOf(doc.get("targetWeight")));
                if (doc.contains("waterGoal")) etWaterGoal.setText(String.valueOf(doc.get("waterGoal")));
                if (doc.contains("sleepGoal")) etSleepGoal.setText(String.valueOf(doc.get("sleepGoal")));
                
                selectChipByText(cgGender, doc.getString("gender"));
                selectChipByText(cgActivity, doc.getString("activityLevel"));
                selectChipByText(cgGoal, doc.getString("goal"));
                selectChipByText(cgDiet, doc.getString("dietaryPreference"));
            }
        });
    }

    private void selectChipByText(ChipGroup cg, String text) {
        if (text == null) return;
        for (int i = 0; i < cg.getChildCount(); i++) {
            View v = cg.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                if (chip.getText().toString().equalsIgnoreCase(text)) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
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
        if (user == null) {
            Toast.makeText(this, "Session expired. Log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        saveProfileBtn.setEnabled(false);
        saveProfileBtn.setText("Saving...");

        String name = etName.getText().toString().trim();
        int age = Integer.parseInt(etAge.getText().toString().trim());
        double weight = Double.parseDouble(etWeight.getText().toString().trim());
        double height = Double.parseDouble(etHeight.getText().toString().trim());
        double targetWeight = Double.parseDouble(etTargetWeight.getText().toString().trim());
        double waterGoal = Double.parseDouble(etWaterGoal.getText().toString().trim());
        double sleepGoal = Double.parseDouble(etSleepGoal.getText().toString().trim());

        String gender = getSelectedChipText(cgGender);
        String activityLevel = getSelectedChipText(cgActivity);
        String goal = getSelectedChipText(cgGoal);
        String dietaryPref = getSelectedChipText(cgDiet);

        // Nutrition logic
        double bmr = (gender.equalsIgnoreCase("Male")) ? 
                (10 * weight) + (6.25 * height) - (5 * age) + 5 :
                (10 * weight) + (6.25 * height) - (5 * age) - 161;

        double multiplier = 1.2;
        if (activityLevel.contains("Moderate")) multiplier = 1.55;
        else if (activityLevel.contains("Active")) multiplier = 1.725;
        else if (activityLevel.contains("Athlete")) multiplier = 1.9;
        
        double tdee = bmr * multiplier;
        int targetCals = (int) tdee;
        if (goal.contains("Lose")) targetCals -= 500;
        else if (goal.contains("Gain")) targetCals += 500;

        int protein = (int) (weight * 2.0); 
        int fat = (int) ((targetCals * 0.25) / 9);
        int carbs = (targetCals - (protein * 4) - (fat * 9)) / 4;

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("age", age);
        profile.put("weight", weight);
        profile.put("height", height);
        profile.put("targetWeight", targetWeight);
        profile.put("waterGoal", waterGoal);
        profile.put("sleepGoal", sleepGoal);
        profile.put("gender", gender);
        profile.put("activityLevel", activityLevel);
        profile.put("goal", goal);
        profile.put("dietaryPreference", dietaryPref);
        profile.put("targetKcal", targetCals);
        profile.put("targetProtein", (double) protein);
        profile.put("targetCarbs", (double) carbs);
        profile.put("targetFat", (double) fat);
        profile.put("isProfileComplete", true);

        db.collection("users").document(user.getUid())
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences sharedPref = getSharedPreferences("NutriPrefs", Context.MODE_PRIVATE);
                    sharedPref.edit()
                            .putString("userName", name)
                            .putBoolean("isLoggedIn", true)
                            .putBoolean("isProfileComplete", true)
                            .apply();
                    
                    Toast.makeText(ProfileSetupActivity.this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(ProfileSetupActivity.this, HomeDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileSetup", "Save failed", e);
                    saveProfileBtn.setEnabled(true);
                    saveProfileBtn.setText("Finish Setup");
                    Toast.makeText(ProfileSetupActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        if (etName.getText().toString().trim().isEmpty() || 
            etAge.getText().toString().trim().isEmpty() ||
            etWeight.getText().toString().trim().isEmpty() || 
            etHeight.getText().toString().trim().isEmpty() ||
            etTargetWeight.getText().toString().trim().isEmpty() ||
            etWaterGoal.getText().toString().trim().isEmpty() ||
            etSleepGoal.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (cgGender.getCheckedChipId() == View.NO_ID || 
            cgActivity.getCheckedChipId() == View.NO_ID ||
            cgGoal.getCheckedChipId() == View.NO_ID ||
            cgDiet.getCheckedChipId() == View.NO_ID) {
            Toast.makeText(this, "Please select all preferences", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
