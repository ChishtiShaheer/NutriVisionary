package com.example.nutrivisionary;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProgressAnalyticsActivity extends AppCompatActivity {

    private TextView tabWeek, tabMonth, tabYear;
    private TextView currentWeight, avgCaloriesValue, streakValue, goalProgressValue;
    private TextView tvSleepStats, tvSleepWarning, tvSleepGoalLabel;
    private ViewGroup mainContainer;
    private GridLayout heatmapGrid;
    private LinearLayout layoutSleepCycles;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final Map<String, LogData> logs = new HashMap<>();
    private double sleepGoal = 8.0;
    private int calorieGoal = 2000;

    private static final int CELL_SIZE_DP = 16;
    private static final int CELL_MARGIN_DP = 2;
    private static final int LABEL_W_DP = 14;
    private static final int ROW_H_DP = CELL_SIZE_DP + 2 * CELL_MARGIN_DP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_analytics);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        mainContainer = findViewById(R.id.mainContainer);
        heatmapGrid = findViewById(R.id.heatmapGrid);
        layoutSleepCycles = findViewById(R.id.layoutSleepCycles);
        tabWeek = findViewById(R.id.tabWeek);
        tabMonth = findViewById(R.id.tabMonth);
        tabYear = findViewById(R.id.tabYear);
        currentWeight = findViewById(R.id.currentWeight);
        avgCaloriesValue = findViewById(R.id.avgCaloriesValue);
        streakValue = findViewById(R.id.streakValue);
        goalProgressValue = findViewById(R.id.goalProgressValue);
        tvSleepStats = findViewById(R.id.tvSleepStats);
        tvSleepWarning = findViewById(R.id.tvSleepWarning);
        tvSleepGoalLabel = findViewById(R.id.tvSleepGoalLabel);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tabWeek.setOnClickListener(v -> updateView("week"));
        tabMonth.setOnClickListener(v -> updateView("month"));
        tabYear.setOnClickListener(v -> updateView("year"));

        fetchData();
    }

    private void fetchData() {
        String uid = mAuth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                Double w = userDoc.getDouble("weight");
                if (w != null) currentWeight.setText(String.format(Locale.getDefault(), "%.1f kg", w));
                Double sGoal = userDoc.getDouble("sleepGoal");
                if (sGoal != null) sleepGoal = sGoal;
                Long cGoal = userDoc.getLong("targetKcal");
                if (cGoal != null) calorieGoal = cGoal.intValue();
            }

            db.collection("users").document(uid).collection("logs").get().addOnSuccessListener(snap -> {
                logs.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    LogData ld = new LogData();
                    Number k = (Number) doc.get("consumedKcal");
                    ld.kcal = (k != null) ? k.intValue() : 0;
                    Number s = (Number) doc.get("hoursSleep");
                    ld.sleep = (s != null) ? s.doubleValue() : 0.0;
                    logs.put(doc.getId(), ld);
                }
                updateView("month"); 
            });
        });
    }

    private void updateView(String timeframe) {
        if (mainContainer != null) {
            TransitionManager.beginDelayedTransition(mainContainer);
        }
        resetTabs();

        if (timeframe.equals("week")) {
            setSelectedTab(tabWeek);
            populateWeeklyHeatmap();
            calculateStats(7);
        } else {
            int days = timeframe.equals("year") ? 180 : 30;
            setSelectedTab(timeframe.equals("year") ? tabYear : tabMonth);
            populateContributionHeatmap(days);
            calculateStats(days);
        }
        drawSleepBars();
    }

    private void populateWeeklyHeatmap() {
        if (heatmapGrid == null) return;
        heatmapGrid.removeAllViews();
        
        // Horizontal row of 7 columns, each containing Initial + Square
        heatmapGrid.setRowCount(1);
        heatmapGrid.setColumnCount(7);
        heatmapGrid.setOrientation(GridLayout.HORIZONTAL);

        Calendar cal = Calendar.getInstance();
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DATE, -1);
        }

        String[] days = {"M", "T", "W", "T", "F", "S", "S"};
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            
            TextView tv = new TextView(this);
            tv.setText(days[i]);
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(this, R.color.navyBlue));
            item.addView(tv);

            View sq = makeSquare(sdf.format(cal.getTime()));
            // Square params are generated inside makeSquare, but we can tweak them
            GridLayout.LayoutParams sqParams = (GridLayout.LayoutParams) sq.getLayoutParams();
            sqParams.width = dpToPx(24);
            sqParams.height = dpToPx(24);
            sqParams.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            sq.setLayoutParams(sqParams);
            item.addView(sq);

            GridLayout.LayoutParams itemParams = new GridLayout.LayoutParams();
            itemParams.width = dpToPx(40);
            itemParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            item.setLayoutParams(itemParams);
            
            heatmapGrid.addView(item);
            cal.add(Calendar.DATE, 1);
        }
    }

    private void populateContributionHeatmap(int totalDays) {
        if (heatmapGrid == null) return;
        heatmapGrid.removeAllViews();

        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.DATE, -totalDays);
        while (startCal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            startCal.add(Calendar.DATE, -1);

        long diffMs = Calendar.getInstance().getTimeInMillis() - startCal.getTimeInMillis();
        int weeks = (int) (diffMs / (7L * 24 * 60 * 60 * 1000)) + 1;

        final int ROWS = 8; // Row 0 is Month, Row 1-7 are Days
        final int TOTAL_COLS = 1 + weeks;

        heatmapGrid.setRowCount(ROWS);
        heatmapGrid.setColumnCount(TOTAL_COLS);
        heatmapGrid.setOrientation(GridLayout.VERTICAL); 

        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat monthSdf = new SimpleDateFormat("MMM", Locale.getDefault());

        // Col 0 Labels
        heatmapGrid.addView(makeDayLabel("")); // corner
        String[] labels = {"M", "T", "W", "T", "F", "S", "S"};
        for (String label : labels) {
            heatmapGrid.addView(makeDayLabel(label));
        }

        // Cols 1 to N
        Calendar weekCal = (Calendar) startCal.clone();
        for (int w = 0; w < weeks; w++) {
            // Row 0: month name
            String mText = (weekCal.get(Calendar.DAY_OF_MONTH) <= 7) ? monthSdf.format(weekCal.getTime()) : "";
            heatmapGrid.addView(makeMonthLabel(mText));

            // Row 1-7: squares
            Calendar dayCal = (Calendar) weekCal.clone();
            for (int d = 0; d < 7; d++) {
                heatmapGrid.addView(makeSquare(dateSdf.format(dayCal.getTime())));
                dayCal.add(Calendar.DATE, 1);
            }
            weekCal.add(Calendar.DATE, 7);
        }
    }

    private TextView makeDayLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(8);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = dpToPx(LABEL_W_DP);
        p.height = dpToPx(ROW_H_DP);
        tv.setLayoutParams(p);
        return tv;
    }

    private TextView makeMonthLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(8);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = dpToPx(CELL_SIZE_DP + 2 * CELL_MARGIN_DP);
        p.height = dpToPx(ROW_H_DP);
        tv.setLayoutParams(p);
        return tv;
    }

    private View makeSquare(String dateStr) {
        View sq = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(3));
        gd.setColor(ContextCompat.getColor(this, heatmapColorFor(dateStr)));
        sq.setBackground(gd);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = dpToPx(CELL_SIZE_DP);
        p.height = dpToPx(CELL_SIZE_DP);
        p.setMargins(dpToPx(CELL_MARGIN_DP), dpToPx(CELL_MARGIN_DP), dpToPx(CELL_MARGIN_DP), dpToPx(CELL_MARGIN_DP));
        sq.setLayoutParams(p);
        return sq;
    }

    private int heatmapColorFor(String dateStr) {
        LogData d = logs.get(dateStr);
        if (d == null || d.kcal == 0) return R.color.heatmap_empty;
        if (d.kcal > calorieGoal) return R.color.macroFat;
        if (d.kcal >= (int) (calorieGoal * 0.8)) return R.color.heatmap_hit;
        return R.color.heatmap_partial;
    }

    private void calculateStats(int days) {
        long totalKcal = 0;
        int loggedCount = 0, hits = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days; i++) {
            LogData d = logs.get(sdf.format(cal.getTime()));
            if (d != null && d.kcal > 0) {
                totalKcal += d.kcal;
                loggedCount++;
                if (d.kcal <= calorieGoal) hits++;
            }
            cal.add(Calendar.DATE, -1);
        }
        avgCaloriesValue.setText(loggedCount > 0 ? (totalKcal / loggedCount) + " kcal" : "0 kcal");
        goalProgressValue.setText(loggedCount > 0 ? (hits * 100 / loggedCount) + "%" : "0%");
        cal = Calendar.getInstance();
        int streak = 0;
        while (logs.containsKey(sdf.format(cal.getTime()))) {
            streak++;
            cal.add(Calendar.DATE, -1);
        }
        streakValue.setText(streak + " Days");
    }

    private void drawSleepBars() {
        if (layoutSleepCycles == null) return;
        layoutSleepCycles.removeAllViews();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -14);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        double totalSleep = 0;
        int count = 0, missed = 0;
        for (int i = 0; i < 15; i++) {
            LogData d = logs.get(sdf.format(cal.getTime()));
            double s = (d != null) ? d.sleep : 0;
            totalSleep += s;
            if (s > 0) count++;
            if (s > 0 && s < sleepGoal) missed++;
            View bar = new View(this);
            int height = (int) (s * 10 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(16), Math.max(dpToPx(4), Math.min(dpToPx(100), height)));
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(ContextCompat.getColor(this, s >= sleepGoal ? R.color.macroProtein : (s > 0 ? R.color.heatmap_partial : R.color.heatmap_empty)));
            layoutSleepCycles.addView(bar);
            cal.add(Calendar.DATE, 1);
        }
        tvSleepStats.setText(String.format(Locale.getDefault(), "Avg Sleep: %.1f hrs", count > 0 ? totalSleep / count : 0));
        tvSleepWarning.setText(missed > 0 ? missed + " days below goal!" : "Goal met! 🎉");
        tvSleepWarning.setTextColor(ContextCompat.getColor(this, missed > 0 ? R.color.macroFat : R.color.macroProtein));
    }

    private void resetTabs() {
        for (TextView t : new TextView[]{tabWeek, tabMonth, tabYear}) {
            if (t != null) {
                t.setBackground(null);
                t.setTextColor(ContextCompat.getColor(this, R.color.slateGray));
                t.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void setSelectedTab(TextView tab) {
        if (tab == null) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dpToPx(22));
        pill.setColor(ContextCompat.getColor(this, R.color.royalBlue));
        tab.setBackground(pill);
        tab.setTextColor(ContextCompat.getColor(this, R.color.white));
        tab.setTypeface(null, Typeface.BOLD);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static class LogData { int kcal; double sleep; }
}
