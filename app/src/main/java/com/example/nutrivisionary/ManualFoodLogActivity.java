package com.example.nutrivisionary;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManualFoodLogActivity extends AppCompatActivity {

    private String mealType;
    private RecyclerView rvFoodItems;
    private FoodAdapter adapter;
    private List<FoodItem> foodList;
    private List<FoodItem> filteredList;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_food_log);

        mealType = getIntent().getStringExtra("mealType");
        if (mealType == null) mealType = getString(R.string.snacks);

        TextView tvTitle = findViewById(R.id.tvLogTitle);
        tvTitle.setText(getString(R.string.log_to_meal, mealType));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etSearch = findViewById(R.id.etSearchFood);
        rvFoodItems = findViewById(R.id.rvFoodItems);
        rvFoodItems.setLayoutManager(new LinearLayoutManager(this));

        loadFoodDatabase();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadFoodDatabase() {
        foodList = new ArrayList<>();
        foodList.add(new FoodItem("Oatmeal", 150, 6.0, 27.0, 3.0));
        foodList.add(new FoodItem("Chicken Breast (100g)", 165, 31.0, 0.0, 4.0));
        foodList.add(new FoodItem("Brown Rice (1 cup)", 215, 5.0, 45.0, 2.0));
        foodList.add(new FoodItem("Banana", 105, 1.0, 27.0, 0.0));
        foodList.add(new FoodItem("Greek Yogurt", 100, 10.0, 6.0, 0.0));
        foodList.add(new FoodItem("Egg (Large)", 70, 6.0, 0.0, 5.0));
        foodList.add(new FoodItem("Almonds (28g)", 160, 6.0, 6.0, 14.0));
        foodList.add(new FoodItem("Avocado", 160, 2.0, 9.0, 15.0));
        foodList.add(new FoodItem("Salmon (100g)", 208, 20.0, 0.0, 13.0));
        foodList.add(new FoodItem("Apple", 95, 0.0, 25.0, 0.0));
        foodList.add(new FoodItem("Orange", 62, 1.2, 15.0, 0.2));
        foodList.add(new FoodItem("Steak (100g)", 250, 26.0, 0.0, 15.0));
        foodList.add(new FoodItem("Healthy Salad", 120, 3.0, 10.0, 5.0));

        filteredList = new ArrayList<>(foodList);
        adapter = new FoodAdapter(filteredList);
        rvFoodItems.setAdapter(adapter);
    }

    private void filter(String text) {
        filteredList.clear();
        for (FoodItem item : foodList) {
            if (item.name.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void logFood(FoodItem food) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, getString(R.string.not_logged), Toast.LENGTH_SHORT).show();
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String field = "foods_" + mealType;
        String logEntry = String.format(Locale.getDefault(), "%s (%d kcal)", food.name, food.kcal);

        Map<String, Object> update = new HashMap<>();
        update.put("consumedKcal", FieldValue.increment(food.kcal));
        update.put("consumedProtein", FieldValue.increment(food.protein));
        update.put("consumedCarbs", FieldValue.increment(food.carbs));
        update.put("consumedFat", FieldValue.increment(food.fat));
        update.put(field, FieldValue.arrayUnion(logEntry));
        update.put("lastUpdated", FieldValue.serverTimestamp());

        db.collection("users").document(uid).collection("logs").document(today)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    setResult(RESULT_OK);
                    Toast.makeText(ManualFoodLogActivity.this, getString(R.string.logged_to_meal, food.name, mealType), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ManualFoodLogActivity.this, getString(R.string.failed_to_log) + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class FoodItem {
        String name;
        int kcal;
        double protein, carbs, fat;
        FoodItem(String name, int kcal, double protein, double carbs, double fat) {
            this.name = name; this.kcal = kcal; this.protein = protein; this.carbs = carbs; this.fat = fat;
        }
    }

    private class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.ViewHolder> {
        List<FoodItem> items;
        FoodAdapter(List<FoodItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food_manual, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FoodItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvMacros.setText(String.format(Locale.getDefault(), "P: %.1fg | C: %.1fg | F: %.1fg", item.protein, item.carbs, item.fat));
            holder.tvKcal.setText(getString(R.string.unit_kcal, item.kcal));
            
            holder.btnQuickAdd.setOnClickListener(v -> logFood(item));
            holder.itemView.setOnClickListener(v -> logFood(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvMacros, tvKcal;
            MaterialButton btnQuickAdd;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvManualFoodName);
                tvMacros = v.findViewById(R.id.tvManualFoodMacros);
                tvKcal = v.findViewById(R.id.tvManualFoodKcal);
                btnQuickAdd = v.findViewById(R.id.btnQuickAdd);
            }
        }
    }
}
