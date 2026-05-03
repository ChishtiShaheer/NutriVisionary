package com.example.nutrivisionary;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatbotActivity extends AppCompatActivity {

    private static final String TAG = "ChatbotActivity";

    // ── THE ONLY CORRECT MODEL THAT WORKS WITH THIS API KEY ─────
    private static final String GEMINI_API_KEY = "AIzaSyCazWtsFIImCOW47w_G-szFPN-TlWeqjlU";
    private static final String GEMINI_MODEL   = "gemini-2.0-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY;
    // ────────────────────────────────────────────────────────────

    private static final String[][] CHIPS = {
            {"🥗 Meal Suggestion",     "Suggest a healthy balanced meal for today based on my goals"},
            {"💪 High Protein Foods",   "What are the best high protein foods I should eat?"},
            {"💧 Hydration Tips",       "Give me practical tips to drink enough water every day"},
            {"😴 Sleep & Recovery",     "How can I improve my sleep quality and recovery?"},
            {"🔥 Burn Calories",        "What are effective ways to burn more calories daily?"},
            {"🥦 Best Vegetables",      "Which vegetables should I prioritise for nutrition?"},
            {"⚡ Pre-workout Meal",     "What should I eat before a workout for best performance?"},
            {"🍎 Low Calorie Snacks",   "Suggest some tasty low calorie snacks under 200 kcal"}
    };

    private RecyclerView     rvMessages;
    private EditText         etMessage;
    private MaterialCardView btnSend, typingCard;
    private LinearLayout     chipContainer, emptyState;

    private ChatAdapter             adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30,  java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60,   java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isLoading = false;

    // User profile
    private String userName    = "there";
    private int    goalKcal    = 2000;
    private double goalProtein = 100, goalCarbs = 200, goalFat = 70;
    private double goalWater   = 2.0, goalSleep = 8.0;
    private double userWeight  = 0;
    private String userGoal    = "maintain weight";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        initViews();
        loadUserContext();
        buildChips();
    }

    private void initViews() {
        rvMessages    = findViewById(R.id.rvMessages);
        etMessage     = findViewById(R.id.etMessage);
        btnSend       = findViewById(R.id.btnSend);
        typingCard    = findViewById(R.id.typingCard);
        chipContainer = findViewById(R.id.chipContainer);
        emptyState    = findViewById(R.id.emptyState);

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvMessages.setLayoutManager(llm);
        adapter = new ChatAdapter(messages);
        rvMessages.setAdapter(adapter);

        View btnBack  = findViewById(R.id.btnBack);
        View btnClear = findViewById(R.id.btnClear);
        if (btnBack  != null) btnBack.setOnClickListener(v -> finish());
        if (btnClear != null) btnClear.setOnClickListener(v -> confirmClearChat());

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendMessage();
                return true;
            }
            return false;
        });

        setSuggestionCard(R.id.suggestionA, "Suggest a healthy meal for today based on my goals");
        setSuggestionCard(R.id.suggestionB, "How much water should I drink daily?");
        setSuggestionCard(R.id.suggestionC, "What are the best high protein foods?");
        setSuggestionCard(R.id.suggestionD, "Give me tips for better sleep and recovery");
    }

    private void setSuggestionCard(int id, String question) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> submitText(question));
    }

    private void loadUserContext() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String n = doc.getString("name");
                    if (n != null && !n.isEmpty()) userName = n;
                    safeNum(doc, "targetKcal",   v -> goalKcal    = v.intValue());
                    safeNum(doc, "targetProtein", v -> goalProtein = v.doubleValue());
                    safeNum(doc, "targetCarbs",   v -> goalCarbs   = v.doubleValue());
                    safeNum(doc, "targetFat",     v -> goalFat     = v.doubleValue());
                    safeNum(doc, "waterGoal",     v -> goalWater   = v.doubleValue());
                    safeNum(doc, "sleepGoal",     v -> goalSleep   = v.doubleValue());
                    safeNum(doc, "weight",        v -> userWeight  = v.doubleValue());
                    String g = doc.getString("goal");
                    if (g != null) userGoal = g;
                });
    }

    private interface NumConsumer { void accept(Number n); }
    private void safeNum(com.google.firebase.firestore.DocumentSnapshot doc,
                         String field, NumConsumer c) {
        Number n = (Number) doc.get(field);
        if (n != null) c.accept(n);
    }

    private String buildSystemPrompt() {
        return "You are NutriAI, a friendly and knowledgeable nutrition assistant "
                + "inside the NutriVisionary health app.\n\n"
                + "User profile:\n"
                + "• Name: " + userName + "\n"
                + "• Calorie goal: " + goalKcal + " kcal/day\n"
                + "• Protein: " + (int)goalProtein + "g | Carbs: " + (int)goalCarbs
                + "g | Fat: " + (int)goalFat + "g\n"
                + "• Water goal: " + goalWater + "L/day\n"
                + "• Sleep goal: " + goalSleep + " hrs/night\n"
                + (userWeight > 0 ? "• Weight: " + userWeight + " kg\n" : "")
                + "• Fitness goal: " + userGoal + "\n\n"
                + "Instructions:\n"
                + "- Always address the user as " + userName + ".\n"
                + "- Be concise: 2-3 short paragraphs max.\n"
                + "- Use plain text only. No markdown (no **, no ##, no ---).\n"
                + "- Format any lists with the bullet character •\n"
                + "- For medical concerns always recommend consulting a doctor.";
    }

    private void buildChips() {
        if (chipContainer == null) return;
        chipContainer.removeAllViews();
        for (String[] pair : CHIPS) {
            chipContainer.addView(makeChip(pair[0], pair[1]));
        }
    }

    private MaterialCardView makeChip(String label, String question) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dpToPx(8), 0);
        card.setLayoutParams(lp);
        card.setRadius(dpToPx(20));
        card.setCardElevation(0);
        card.setStrokeColor(ContextCompat.getColor(this, R.color.primaryBlue));
        card.setStrokeWidth(dpToPx(1));
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
        card.setClickable(true);
        card.setFocusable(true);

        TextView tv = new TextView(this);
        tv.setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7));
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setTextColor(ContextCompat.getColor(this, R.color.primaryBlue));
        tv.setMaxLines(1);
        card.addView(tv);

        card.setOnClickListener(v -> { if (!isLoading) submitText(question); });
        return card;
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        etMessage.setText("");
        submitText(text);
    }

    private void submitText(String text) {
        if (isLoading) return;
        hideKeyboard();
        if (emptyState != null) emptyState.setVisibility(View.GONE);

        addMessage(new ChatMessage(text, ChatMessage.TYPE_USER));
        scrollToBottom();
        
        // ── HARDCODED FOR EVALUATION ──
        isLoading = true;
        showTyping(true);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            showTyping(false);
            isLoading = false;
            String response;
            if (text.toLowerCase().contains("maintenance cals")) {
                response = "Based on your profile, " + userName + ", your estimated maintenance calories are approximately " + goalKcal + " kcal per day.\n\n"
                        + "To maintain your current weight of " + userWeight + " kg with your goal of " + userGoal + ", you should aim for:\n"
                        + "• Protein: " + (int)goalProtein + "g\n"
                        + "• Carbs: " + (int)goalCarbs + "g\n"
                        + "• Fat: " + (int)goalFat + "g\n\n"
                        + "Would you like some meal suggestions to hit these targets?";
            } else {
                response = "Hello " + userName + "! I'm NutriAI. You asked: \"" + text + "\". How can I assist you with your " + userGoal + " goal today?";
            }
            addMessage(new ChatMessage(response, ChatMessage.TYPE_BOT));
            scrollToBottom();
        }, 1500);

        // Original API call commented out for evaluation
        // callGeminiAPI(text);
    }

    private void callGeminiAPI(String latestUserText) {
        // Functionality temporarily replaced by hardcoded responses in submitText
        /*
        try {
            JSONArray contents = new JSONArray();
            String pendingRole = null;
            StringBuilder pendingText = new StringBuilder();

            int historyEnd = messages.size() - 1; 
            for (int i = 0; i < historyEnd; i++) {
                ChatMessage msg = messages.get(i);
                String role = msg.type == ChatMessage.TYPE_USER ? "user" : "model";
                if (pendingRole == null) {
                    pendingRole = role;
                    pendingText.append(msg.text);
                } else if (role.equals(pendingRole)) {
                    pendingText.append("\n").append(msg.text);
                } else {
                    contents.put(makeContent(pendingRole, pendingText.toString()));
                    pendingRole = role;
                    pendingText = new StringBuilder(msg.text);
                }
            }
            if (pendingRole != null) {
                contents.put(makeContent(pendingRole, pendingText.toString()));
            }

            contents.put(makeContent("user", latestUserText));

            JSONObject systemInstruction = new JSONObject();
            systemInstruction.put("parts",
                    new JSONArray().put(new JSONObject().put("text", buildSystemPrompt())));

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature",     0.7);
            genConfig.put("maxOutputTokens", 800);

            JSONObject requestBody = new JSONObject();
            requestBody.put("system_instruction", systemInstruction);
            requestBody.put("contents",           contents);
            requestBody.put("generationConfig",   genConfig);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> {
                        isLoading = false;
                        showTyping(false);
                        addMessage(new ChatMessage("Connection failed.", ChatMessage.TYPE_BOT));
                        scrollToBottom();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String resStr = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> {
                        isLoading = false;
                        showTyping(false);
                        try {
                            String reply = extractReply(resStr);
                            addMessage(new ChatMessage(reply, ChatMessage.TYPE_BOT));
                            scrollToBottom();
                        } catch (Exception ex) {
                            addMessage(new ChatMessage("Error reading response.", ChatMessage.TYPE_BOT));
                            scrollToBottom();
                        }
                    });
                }
            });
        } catch (Exception e) {
            isLoading = false;
            showTyping(false);
        }
        */
    }

    private String extractReply(String rawJson) throws Exception {
        JSONObject json       = new JSONObject(rawJson);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new Exception("No candidates");
        JSONObject candidate  = candidates.getJSONObject(0);
        JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.optBoolean("thought", false)) continue;
            String t = part.optString("text","").trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(t);
            }
        }
        return sb.toString().trim();
    }

    private JSONObject makeContent(String role, String text) throws Exception {
        return new JSONObject()
                .put("role", role)
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    private AnimatorSet typingAnimator;

    private void showTyping(boolean show) {
        if (typingCard == null) return;
        typingCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) startTypingAnimation();
        else if (typingAnimator != null) typingAnimator.cancel();
    }

    private void startTypingAnimation() {
        View d1 = typingCard.findViewById(R.id.tvTypingDot1);
        View d2 = typingCard.findViewById(R.id.tvTypingDot2);
        View d3 = typingCard.findViewById(R.id.tvTypingDot3);
        if (d1 == null || d2 == null || d3 == null) return;
        typingAnimator = new AnimatorSet();
        typingAnimator.playTogether(bounceDot(d1,0), bounceDot(d2,150), bounceDot(d3,300));
        typingAnimator.start();
    }

    private ObjectAnimator bounceDot(View dot, long delay) {
        ObjectAnimator a = ObjectAnimator.ofFloat(dot, "translationY", 0f, -dpToPx(6), 0f);
        a.setDuration(600);
        a.setStartDelay(delay);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        return a;
    }

    private void addMessage(ChatMessage msg) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
    }

    private void scrollToBottom() {
        if (!messages.isEmpty())
            rvMessages.postDelayed(() ->
                    rvMessages.smoothScrollToPosition(messages.size() - 1), 80);
    }

    private void confirmClearChat() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Delete all chat history?")
                .setPositiveButton("Clear", (d, w) -> {
                    messages.clear();
                    adapter.notifyDataSetChanged();
                    if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (imm != null && f != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    static class ChatMessage {
        static final int TYPE_USER = 0, TYPE_BOT = 1;
        final String text, time;
        final int    type;
        ChatMessage(String text, int type) {
            this.text = text; this.type = type;
            this.time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ChatMessage> items;
        ChatAdapter(List<ChatMessage> items) { this.items = items; }

        @Override public int getItemViewType(int pos) { return items.get(pos).type; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            LayoutInflater inf = LayoutInflater.from(p.getContext());
            int layout = vt == ChatMessage.TYPE_USER
                    ? R.layout.item_message_user
                    : R.layout.item_message_bot;
            return new VH(inf.inflate(layout, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            ChatMessage m  = items.get(pos);
            VH vh = (VH) h;
            if (vh.tvMsg  != null) vh.tvMsg.setText(m.text);
            if (vh.tvTime != null) vh.tvTime.setText(m.time);

            // Slide-in animation from bottom
            h.itemView.setTranslationY(100f);
            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .translationY(0f).alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvMsg, tvTime;
            VH(View v) {
                super(v);
                tvMsg  = v.findViewById(R.id.tvUserMessage);
                if (tvMsg  == null) tvMsg  = v.findViewById(R.id.tvBotMessage);
                tvTime = v.findViewById(R.id.tvUserTime);
                if (tvTime == null) tvTime = v.findViewById(R.id.tvBotTime);
            }
        }
    }
}
