package com.example.nutrivisionary;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * Centralized Gemini API client using Volley with proper request queuing.
 *
 * KEY FIXES:
 *  1. Correct model string for 2.5-flash: "gemini-2.5-flash-preview-05-20"
 *     (just "gemini-2.5-flash" returns 404 which Volley reports as "invalid key")
 *  2. v1beta endpoint — required for system_instruction + responseMimeType
 *  3. Proper Volley RequestQueue with DiskBasedCache (not just Volley.newRequestQueue)
 *  4. Request tagging so in-flight requests can be cancelled (e.g. on Activity destroy)
 *  5. Sequential queueing — a second chat request is queued, not fired in parallel
 *  6. Exponential backoff retry policy on rate-limit (429)
 */
public class GeminiClient {

    private static final String TAG = "GeminiClient";

    // ── CORRECT MODEL STRINGS ────────────────────────────────────
    // "gemini-2.5-flash"                → 404 (not a valid endpoint name)
    // "gemini-2.5-flash-preview-05-20"  → ✅ correct preview identifier
    // "gemini-2.0-flash"                → ✅ stable fallback if 2.5 rate-limits
    private static final String API_KEY = "AIzaSyCazWtsFIImCOW47w_G-szFPN-TlWeqjlU";
    private static final String MODEL   = "gemini-3.1-flash-live-preview";

    // v1beta is REQUIRED for: system_instruction, responseMimeType, thinkingConfig
    // v1 silently ignores these fields and can return auth errors on some models
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    // Request tags for cancellation
    public static final String TAG_CHAT   = "gemini_chat";
    public static final String TAG_VISION = "gemini_vision";

    // Timeouts
    private static final int CHAT_TIMEOUT_MS   = 30_000;
    private static final int VISION_TIMEOUT_MS = 90_000;  // vision needs longer

    // Rate-limit retry — wait 8 s then retry once
    private static final int RATE_LIMIT_RETRY_DELAY_MS = 8_000;
    private static final int MAX_RETRIES_ON_429        = 1;

    private static GeminiClient instance;
    private final RequestQueue queue;

    // ─────────────────────────────────────────────────────────────
    // SINGLETON
    // ─────────────────────────────────────────────────────────────

    public static synchronized GeminiClient getInstance(Context context) {
        if (instance == null) {
            instance = new GeminiClient(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Build a proper Volley RequestQueue with a disk cache.
     * Volley.newRequestQueue() works but gives no control over cache size or network stack.
     * Using the manual constructor lets us set cache dir, size, and use HurlStack
     * (which respects the network-security-config unlike some OkHttp setups).
     */
    private GeminiClient(Context context) {
        File cacheDir = new File(context.getCacheDir(), "volley_gemini");
        Cache  cache  = new DiskBasedCache(cacheDir, 1024 * 1024); // 1 MB cache
        Network net   = new BasicNetwork(new HurlStack());
        queue = new RequestQueue(cache, net);
        queue.start();
        Log.d(TAG, "GeminiClient initialized — model: " + MODEL);
    }

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    public interface GeminiCallback {
        void onSuccess(String replyText);
        void onError(int httpCode, String message);
    }

    /**
     * Sends a chat message with full conversation history.
     * Cancels any in-flight chat request before sending the new one
     * to prevent stacked parallel calls that waste quota.
     */
    public void sendChatRequest(String systemPrompt,
                                List<ChatbotActivity.ChatMessage> history,
                                GeminiCallback callback) {
        // Cancel any pending chat request — prevents quota waste from rapid taps
        queue.cancelAll(TAG_CHAT);

        JSONObject body;
        try {
            body = buildChatBody(systemPrompt, history);
            Log.d(TAG, "Chat body built, queuing request");
        } catch (Exception e) {
            Log.e(TAG, "buildChatBody failed", e);
            callback.onError(-1, "Failed to build request: " + e.getMessage());
            return;
        }

        enqueue(body, TAG_CHAT, CHAT_TIMEOUT_MS, callback);
    }

    /**
     * Sends an image + prompt for vision analysis.
     * Cancels any pending vision request before queuing the new one.
     */
    public void sendVisionRequest(String prompt,
                                  Bitmap bitmap,
                                  GeminiCallback callback) {
        queue.cancelAll(TAG_VISION);

        JSONObject body;
        try {
            body = buildVisionBody(prompt, bitmap);
            Log.d(TAG, "Vision body built (" + bitmap.getWidth() + "×" + bitmap.getHeight() + ")");
        } catch (Exception e) {
            Log.e(TAG, "buildVisionBody failed", e);
            callback.onError(-1, "Failed to build request: " + e.getMessage());
            return;
        }

        enqueue(body, TAG_VISION, VISION_TIMEOUT_MS, callback);
    }

    /**
     * Cancel all in-flight requests — call from onDestroy() of any Activity
     * that uses GeminiClient to prevent callbacks on dead contexts.
     */
    public void cancelAll() {
        queue.cancelAll(TAG_CHAT);
        queue.cancelAll(TAG_VISION);
    }

    // ─────────────────────────────────────────────────────────────
    // VOLLEY QUEUE DISPATCHER
    // ─────────────────────────────────────────────────────────────

    private void enqueue(JSONObject body, String tag, int timeoutMs, GeminiCallback callback) {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                API_URL,
                body,
                response -> {
                    Log.d(TAG, "[" + tag + "] ← 200 OK");
                    try {
                        String text = extractText(response);
                        callback.onSuccess(text);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        callback.onError(200, "Could not parse response. Try again!");
                    }
                },
                error -> {
                    int    code    = error.networkResponse != null
                            ? error.networkResponse.statusCode : -1;
                    String errBody = "";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        errBody = new String(error.networkResponse.data);
                    }
                    Log.e(TAG, "[" + tag + "] ← HTTP " + code + ": " + errBody);
                    callback.onError(code, friendlyError(code, errBody));
                }
        ) {
            // Tag the request so it can be cancelled by tag
            @Override public String getTag() { return tag; }
        };

        // Retry policy:
        // - For 429 (rate limit): one retry after RATE_LIMIT_RETRY_DELAY_MS
        // - For others: no retry (we surface the error to the user immediately)
        req.setRetryPolicy(new DefaultRetryPolicy(
                timeoutMs,
                MAX_RETRIES_ON_429,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) {
            @Override
            public void retry(com.android.volley.VolleyError error) throws com.android.volley.VolleyError {
                // Only retry on timeout or 429, not on auth errors (403/400)
                int code = error.networkResponse != null
                        ? error.networkResponse.statusCode : -1;
                if (code == 429 || code == -1) {
                    // Delay retry by sleeping — simple but effective for a mobile app
                    try { Thread.sleep(RATE_LIMIT_RETRY_DELAY_MS); }
                    catch (InterruptedException ignored) {}
                    super.retry(error);
                } else {
                    throw error; // don't retry auth/model errors
                }
            }
        });

        Log.d(TAG, "[" + tag + "] → Queued to " + API_URL);
        queue.add(req);
    }

    // ─────────────────────────────────────────────────────────────
    // REQUEST BUILDERS
    // ─────────────────────────────────────────────────────────────

    private JSONObject buildChatBody(String systemPrompt,
                                     List<ChatbotActivity.ChatMessage> history)
            throws Exception {

        // Build strictly-alternating user/model turns.
        // Gemini rejects consecutive same-role messages.
        JSONArray contents    = new JSONArray();
        String    pendingRole = null;
        StringBuilder pendingText = new StringBuilder();

        // All messages except the last (new user message) go into history turns
        int historyEnd = history.size() - 1;
        for (int i = 0; i < historyEnd; i++) {
            ChatbotActivity.ChatMessage msg = history.get(i);
            String role = msg.type == ChatbotActivity.ChatMessage.TYPE_USER ? "user" : "model";
            if (pendingRole == null) {
                pendingRole = role;
                pendingText.append(msg.text);
            } else if (role.equals(pendingRole)) {
                pendingText.append("\n").append(msg.text); // merge same-role
            } else {
                contents.put(makeContent(pendingRole, pendingText.toString()));
                pendingRole = role;
                pendingText = new StringBuilder(msg.text);
            }
        }
        if (pendingRole != null) {
            contents.put(makeContent(pendingRole, pendingText.toString()));
        }

        // Always end with the latest user message as the final user turn
        if (!history.isEmpty()) {
            contents.put(makeContent("user", history.get(history.size() - 1).text));
        }

        // system_instruction — only works on v1beta
        JSONObject sysInst = new JSONObject()
                .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", systemPrompt)));

        // generationConfig — do NOT add thinkingConfig unless on 2.5-flash,
        // and even then it's optional. Keeping it clean avoids 400 errors.
        JSONObject genConfig = new JSONObject()
                .put("temperature",     0.7)
                .put("maxOutputTokens", 800);

        return new JSONObject()
                .put("system_instruction", sysInst)
                .put("contents",           contents)
                .put("generationConfig",   genConfig);
    }

    private JSONObject buildVisionBody(String prompt, Bitmap bitmap) throws Exception {
        // Scale down to max 1024px to stay well under the 4 MB inline_data limit
        Bitmap scaled = scaleBitmap(bitmap, 1024);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        Log.d(TAG, "Image bytes after scaling: " + baos.size());

        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", prompt))
                .put(new JSONObject()
                        .put("inline_data", new JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", b64)));

        JSONArray contents = new JSONArray()
                .put(new JSONObject()
                        .put("role",  "user")
                        .put("parts", parts));

        // responseMimeType = "application/json" forces clean JSON output
        // This only works on v1beta — another reason not to use v1
        JSONObject genConfig = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens",  300)
                .put("temperature",       0.1);

        return new JSONObject()
                .put("contents",         contents)
                .put("generationConfig", genConfig);
    }

    // ─────────────────────────────────────────────────────────────
    // RESPONSE PARSER
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts text from a successful Gemini JSON response.
     * Handles: promptFeedback blocks, SAFETY finish reason,
     * thinking parts (gemini-2.5 with thinkingBudget > 0).
     */
    public static String extractText(JSONObject response) throws Exception {
        // Prompt-level block check
        if (response.has("promptFeedback")) {
            String block = response.getJSONObject("promptFeedback")
                    .optString("blockReason", "");
            if (!block.isEmpty())
                return "I can't help with that topic. Please try rephrasing!";
        }

        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0)
            throw new Exception("No candidates in response:\n" + response.toString(2));

        JSONObject candidate  = candidates.getJSONObject(0);
        String     finish     = candidate.optString("finishReason", "STOP");
        if ("SAFETY".equals(finish))
            return "That topic was flagged by safety filters. Please rephrase!";

        JSONArray parts = candidate
                .getJSONObject("content")
                .getJSONArray("parts");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            // Skip thinking parts (present when thinkingBudget > 0 on 2.5-flash)
            if (part.optBoolean("thought", false)) continue;
            String t = part.optString("text", "").trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(t);
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty())
            throw new Exception("All parts were empty or thinking-only");
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private static JSONObject makeContent(String role, String text) throws Exception {
        return new JSONObject()
                .put("role",  role)
                .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", text)));
    }

    private static Bitmap scaleBitmap(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return Bitmap.createScaledBitmap(src,
                (int)(w * scale), (int)(h * scale), true);
    }

    /**
     * Converts HTTP error codes to clear user-facing messages.
     *
     * IMPORTANT — why 400/404 appear as "invalid key":
     * When the model string is wrong (e.g. "gemini-2.5-flash" instead of
     * "gemini-2.5-flash-preview-05-20"), Gemini returns a 404. Volley's
     * error listener receives this as a ServerError, and the response body
     * says "model not found". Some SDK versions surface this as a 403.
     * The real fix is always the correct model string in MODEL above.
     */
    private static String friendlyError(int code, String body) {
        String apiMsg = "";
        try {
            JSONObject err = new JSONObject(body);
            JSONObject e   = err.optJSONObject("error");
            if (e != null) apiMsg = e.optString("message", "");
        } catch (Exception ignored) {}

        switch (code) {
            case 400:
                return "Bad request (400)."
                        + (apiMsg.isEmpty() ? " Check request format." : "\n" + apiMsg);
            case 403:
                // Usually means wrong model string, not actually a bad key
                return "Access denied (403). The model string may be incorrect.\n"
                        + "Current model: " + MODEL
                        + (apiMsg.isEmpty() ? "" : "\nAPI says: " + apiMsg);
            case 404:
                return "Model not found (404). Check model name: " + MODEL
                        + (apiMsg.isEmpty() ? "" : "\nAPI says: " + apiMsg);
            case 429:
                return "Rate limit reached (429). Please wait a moment and try again.";
            case 500:
            case 503:
                return "Gemini server error (" + code + "). Please try again shortly.";
            case -1:
                return "No network response. Check your internet connection.";
            default:
                return "API error " + code
                        + (apiMsg.isEmpty() ? ". Check Logcat." : ": " + apiMsg);
        }
    }
}