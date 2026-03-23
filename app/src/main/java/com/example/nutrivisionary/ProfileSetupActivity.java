package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileSetupActivity extends AppCompatActivity {

    Button saveProfileBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        saveProfileBtn = findViewById(R.id.saveProfileBtn);

        saveProfileBtn.setOnClickListener(v -> {
            startActivity(new Intent(ProfileSetupActivity.this, HomeDashboardActivity.class));
            finish();
        });
    }
}