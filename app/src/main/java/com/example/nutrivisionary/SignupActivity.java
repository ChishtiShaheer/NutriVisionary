package com.example.nutrivisionary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SignupActivity extends AppCompatActivity {

    Button signupBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        signupBtn = findViewById(R.id.signUpBtn);

        signupBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileSetupActivity.class));
        });
    }
}