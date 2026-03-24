package com.example.nutrivisionary;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;
import java.util.Random;

public class ProgressAnalyticsActivity extends AppCompatActivity {

    private TextView tabWeek, tabMonth, tabYear;
    private TextView currentWeight, weightDiff;
    private TextView avgCaloriesValue, streakValue, goalProgressValue;
    private ViewGroup mainContainer;
    private GridLayout heatmapGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_analytics);

        // Initialize views
        mainContainer = findViewById(R.id.mainContainer);
        heatmapGrid = findViewById(R.id.heatmapGrid);
        tabWeek = findViewById(R.id.tabWeek);
        tabMonth = findViewById(R.id.tabMonth);
        tabYear = findViewById(R.id.tabYear);

        currentWeight = findViewById(R.id.currentWeight);
        weightDiff = findViewById(R.id.weightDiff);
        avgCaloriesValue = findViewById(R.id.avgCaloriesValue);
        streakValue = findViewById(R.id.streakValue);
        goalProgressValue = findViewById(R.id.goalProgressValue);

        // Set click listeners
        tabWeek.setOnClickListener(v -> updateUI("week"));
        tabMonth.setOnClickListener(v -> updateUI("month"));
        tabYear.setOnClickListener(v -> updateUI("year"));

        // Initialize Heatmap
        populateHeatmap();
    }

    private void populateHeatmap() {
        heatmapGrid.removeAllViews();
        String[] days = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Add Month Headers (Top Row)
        for (int j = 0; j < 13; j++) {
            TextView monthTxt = new TextView(this);
            if (j > 0) monthTxt.setText(months[j - 1]);
            monthTxt.setTextSize(8);
            monthTxt.setGravity(Gravity.CENTER);
            monthTxt.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = (j == 0) ? dpToPx(30) : dpToPx(20);
            params.height = dpToPx(15);
            monthTxt.setLayoutParams(params);
            heatmapGrid.addView(monthTxt);
        }

        Random random = new Random();
        int[] colors = {R.color.heatmap_empty, R.color.heatmap_partial, R.color.heatmap_hit};

        // Add Rows (Days + Squares)
        for (int i = 1; i <= 7; i++) {
            // Day Label
            TextView dayTxt = new TextView(this);
            dayTxt.setText(days[i]);
            dayTxt.setTextSize(8);
            dayTxt.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            dayTxt.setPadding(0, 0, dpToPx(4), 0);
            dayTxt.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
            
            GridLayout.LayoutParams dayParams = new GridLayout.LayoutParams();
            dayParams.width = dpToPx(30);
            dayParams.height = dpToPx(20);
            dayTxt.setLayoutParams(dayParams);
            heatmapGrid.addView(dayTxt);

            // Squares for each month
            for (int j = 0; j < 12; j++) {
                View square = new View(this);
                int colorRes = colors[random.nextInt(3)];
                square.setBackgroundColor(ContextCompat.getColor(this, colorRes));
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = dpToPx(16);
                params.height = dpToPx(16);
                params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                square.setLayoutParams(params);
                heatmapGrid.addView(square);
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void updateUI(String timeframe) {
        // Start animation for all changes
        TransitionManager.beginDelayedTransition(mainContainer);

        // Update Tabs UI
        resetTabs();
        switch (timeframe) {
            case "week":
                setSelectedTab(tabWeek);
                setData("72.4 kg", "-1.2 kg this week", "1,840", "14 Days", "82%");
                break;
            case "month":
                setSelectedTab(tabMonth);
                setData("73.1 kg", "-2.5 kg this month", "1,750", "28 Days", "75%");
                break;
            case "year":
                setSelectedTab(tabYear);
                setData("75.8 kg", "-8.2 kg this year", "1,900", "120 Days", "68%");
                break;
        }
    }

    private void resetTabs() {
        tabWeek.setBackground(null);
        tabWeek.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        tabWeek.setTypeface(null, Typeface.NORMAL);

        tabMonth.setBackground(null);
        tabMonth.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        tabMonth.setTypeface(null, Typeface.NORMAL);

        tabYear.setBackground(null);
        tabYear.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        tabYear.setTypeface(null, Typeface.NORMAL);
    }

    private void setSelectedTab(TextView tab) {
        tab.setBackgroundResource(R.drawable.rounded_bar_pill);
        tab.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.royalBlue));
        tab.setTextColor(ContextCompat.getColor(this, R.color.white));
        tab.setTypeface(null, Typeface.BOLD);
    }

    private void setData(String weight, String diff, String cal, String streak, String progress) {
        currentWeight.setText(weight);
        weightDiff.setText(diff);
        avgCaloriesValue.setText(cal);
        streakValue.setText(streak);
        goalProgressValue.setText(progress);
    }
}
