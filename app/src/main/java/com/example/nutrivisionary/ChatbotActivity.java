package com.example.nutrivisionary;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatbotActivity extends AppCompatActivity {

    private static final String TAG = "ChatbotActivity";

    // Chip [label, question] pairs
    private static final String[][] CHIPS = {
            {"🥗 Meal Suggestion",   "Suggest a healthy balanced meal for today based on my goals"},
            {"💪 High Protein",       "What are the best high protein foods I should eat?"},
            {"💧 Hydration Tips",     "Give me practical tips to drink enough water every day"},
            {"😴 Sleep & Recovery",   "How can I improve my sleep quality and recovery?"},
            {"🔥 Burn Calories",      "What are effective ways to burn more calories daily?"},
            {"🥦 Best Vegetables",    "Which vegetables should I prioritise for nutrition?"},
            {"⚡ Pre-workout Meal",   "What should I eat before a workout for best performance?"},
            {"🍎 Low Cal Snacks",     "Suggest some tasty low calorie snacks under 200 kcal"}
    };

    private RecyclerView     rvMessages;
    private EditText         etMessage;
    private MaterialCardView btnSend, typingCard;
    private LinearLayout     chipContainer, emptyState;

    private ChatAdapter             adapter;
    final  List<ChatMessage>        messages = new ArrayList<>();

    private boolean isLoading = false;

    // User profile (populated from Firestore)
    private String userName    = "there";
    private int    goalKcal    = 2000;
    private double goalProtein = 100, goalCarbs = 200, goalFat = 70;
    private double goalWater   = 2.0, goalSleep = 8.0;
    private double userWeight  = 0;
    private String userGoal    = "maintain weight";

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        initViews();
        loadUserContext();
        buildChips();
    }

    // ─────────────────────────────────────────────────────────────
    // VIEWS
    // ─────────────────────────────────────────────────────────────

    private void initViews() {
        rvMessages    = findViewById(R.id.rvMessages);
        etMessage     = findViewById(R.id.etMessage);
        btnSend       = findViewById(R.id.btnSend);
        typingCard    = findViewById(R.id.typingCard);
        chipContainer = findViewById(R.id.chipContainer);
        emptyState    = findViewById(R.id.emptyState);

        // stackFromEnd = newest messages appear at bottom
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvMessages.setLayoutManager(llm);
        adapter = new ChatAdapter(messages);
        rvMessages.setAdapter(adapter);

        View back  = findViewById(R.id.btnBack);
        View clear = findViewById(R.id.btnClear);
        if (back  != null) back.setOnClickListener(v  -> finish());
        if (clear != null) clear.setOnClickListener(v -> confirmClearChat());

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Empty-state suggestion cards
        setCard(R.id.suggestionA, "Suggest a healthy meal for today based on my goals");
        setCard(R.id.suggestionB, "How much water should I drink daily?");
        setCard(R.id.suggestionC, "What are the best high protein foods?");
        setCard(R.id.suggestionD, "Give me tips for better sleep and recovery");
    }

    private void setCard(int id, String question) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> submitText(question));
    }

    // ─────────────────────────────────────────────────────────────
    // USER CONTEXT
    // ─────────────────────────────────────────────────────────────

    private void loadUserContext() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String n = doc.getString("name");
                    if (n != null && !n.isEmpty()) userName = n;
                    getNum(doc, "targetKcal",   v -> goalKcal    = v.intValue());
                    getNum(doc, "targetProtein", v -> goalProtein = v.doubleValue());
                    getNum(doc, "targetCarbs",   v -> goalCarbs   = v.doubleValue());
                    getNum(doc, "targetFat",     v -> goalFat     = v.doubleValue());
                    getNum(doc, "waterGoal",     v -> goalWater   = v.doubleValue());
                    getNum(doc, "sleepGoal",     v -> goalSleep   = v.doubleValue());
                    getNum(doc, "weight",        v -> userWeight  = v.doubleValue());
                    String g = doc.getString("goal");
                    if (g != null) userGoal = g;
                });
    }

    private interface NC { void accept(Number n); }
    private void getNum(com.google.firebase.firestore.DocumentSnapshot doc, String f, NC c) {
        Number n = (Number) doc.get(f);
        if (n != null) c.accept(n);
    }

    private String buildSystemPrompt() {
        return "You are NutriAI, a friendly and knowledgeable nutrition assistant "
                + "inside the NutriVisionary health app.\n\n"
                + "User profile:\n"
                + "• Name: " + userName + "\n"
                + "• Calorie goal: " + goalKcal + " kcal/day\n"
                + "• Protein: " + (int)goalProtein + "g  Carbs: " + (int)goalCarbs
                + "g  Fat: " + (int)goalFat + "g\n"
                + "• Water: " + goalWater + "L/day  Sleep: " + goalSleep + " hrs/night\n"
                + (userWeight > 0 ? "• Weight: " + userWeight + " kg\n" : "")
                + "• Goal: " + userGoal + "\n\n"
                + "Rules:\n"
                + "- Address the user as " + userName + ".\n"
                + "- 2-3 short paragraphs max. Be warm and actionable.\n"
                + "- Plain text only — no markdown (no ** ## --- *).\n"
                + "- Use • for any bullet lists.\n"
                + "- For medical questions recommend a healthcare provider.";
    }

    // ─────────────────────────────────────────────────────────────
    // CHIPS
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // SEND
    // ─────────────────────────────────────────────────────────────

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
        isLoading = true;
        showTyping(true);

        // Delegate to GeminiClient (Volley-based)
        GeminiClient.getInstance(this).sendChatRequest(
                buildSystemPrompt(),
                messages,                      // full history including new message
                new GeminiClient.GeminiCallback() {
                    @Override
                    public void onSuccess(String reply) {
                        isLoading = false;
                        showTyping(false);
                        addMessage(new ChatMessage(reply, ChatMessage.TYPE_BOT));
                        scrollToBottom();
                    }

                    @Override
                    public void onError(int code, String message) {
                        isLoading = false;
                        showTyping(false);
                        Log.e(TAG, "Gemini error " + code + ": " + message);
                        addMessage(new ChatMessage(message, ChatMessage.TYPE_BOT));
                        scrollToBottom();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // TYPING INDICATOR
    // ─────────────────────────────────────────────────────────────

    private AnimatorSet typingAnimator;

    private void showTyping(boolean show) {
        if (typingCard == null) return;
        typingCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            startTypingAnimation();
        } else if (typingAnimator != null) {
            typingAnimator.cancel();
        }
    }

    private void startTypingAnimation() {
        View d1 = typingCard.findViewById(R.id.tvTypingDot1);
        View d2 = typingCard.findViewById(R.id.tvTypingDot2);
        View d3 = typingCard.findViewById(R.id.tvTypingDot3);
        if (d1 == null || d2 == null || d3 == null) return;
        typingAnimator = new AnimatorSet();
        typingAnimator.playTogether(bounceDot(d1, 0), bounceDot(d2, 150), bounceDot(d3, 300));
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

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private void addMessage(ChatMessage msg) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
    }

    private void scrollToBottom() {
        if (!messages.isEmpty())
            rvMessages.postDelayed(
                    () -> rvMessages.smoothScrollToPosition(messages.size() - 1), 80);
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

    // ─────────────────────────────────────────────────────────────
    // DATA MODEL
    // ─────────────────────────────────────────────────────────────

    public static class ChatMessage {
        public static final int TYPE_USER = 0, TYPE_BOT = 1;
        public final String text, time;
        public final int    type;
        public ChatMessage(String text, int type) {
            this.text = text;
            this.type = type;
            this.time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────────

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<ChatMessage> items;
        ChatAdapter(List<ChatMessage> items) { this.items = items; }

        @Override public int getItemViewType(int pos) { return items.get(pos).type; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == ChatMessage.TYPE_USER
                    ? R.layout.item_message_user
                    : R.layout.item_message_bot;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ChatMessage m = items.get(pos);
            if (h.tvMsg  != null) h.tvMsg.setText(m.text);
            if (h.tvTime != null) h.tvTime.setText(m.time);

            // Slide-up entrance animation
            h.itemView.setTranslationY(60f);
            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .translationY(0f).alpha(1f)
                    .setDuration(250)
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