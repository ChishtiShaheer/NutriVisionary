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

public class GeminiClient {

    private static final String TAG = "GeminiClient";

    private static final String API_KEY = "AIzaSyCazWtsFIImCOW47w_G-szFPN-TlWeqjlU";
    private static final String MODEL   = "gemini-3.1-flash-live-preview";

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    public static final String TAG_CHAT   = "gemini_chat";
    public static final String TAG_VISION = "gemini_vision";

    private static final int CHAT_TIMEOUT_MS   = 30_000;
    private static final int VISION_TIMEOUT_MS = 90_000;

    private static final int RATE_LIMIT_RETRY_DELAY_MS = 8_000;
    private static final int MAX_RETRIES_ON_429        = 1;

    private static GeminiClient instance;
    private final RequestQueue queue;

    public static synchronized GeminiClient getInstance(Context context) {
        if (instance == null) {
            instance = new GeminiClient(context.getApplicationContext());
        }
        return instance;
    }

    private GeminiClient(Context context) {
        File cacheDir = new File(context.getCacheDir(), "volley_gemini");
        Cache  cache  = new DiskBasedCache(cacheDir, 1024 * 1024);
        Network net   = new BasicNetwork(new HurlStack());
        queue = new RequestQueue(cache, net);
        queue.start();
    }

    public interface GeminiCallback {
        void onSuccess(String replyText);
        void onError(int httpCode, String message);
    }

    public void sendChatRequest(String systemPrompt,
                                List<ChatbotActivity.ChatMessage> history,
                                GeminiCallback callback) {
        queue.cancelAll(TAG_CHAT);

        JSONObject body;
        try {
            body = buildChatBody(systemPrompt, history);
        } catch (Exception e) {
            callback.onError(-1, "Failed to build request: " + e.getMessage());
            return;
        }

        enqueue(body, TAG_CHAT, CHAT_TIMEOUT_MS, callback);
    }

    public void sendVisionRequest(String prompt,
                                  Bitmap bitmap,
                                  GeminiCallback callback) {
        queue.cancelAll(TAG_VISION);

        JSONObject body;
        try {
            body = buildVisionBody(prompt, bitmap);
        } catch (Exception e) {
            callback.onError(-1, "Failed to build request: " + e.getMessage());
            return;
        }

        enqueue(body, TAG_VISION, VISION_TIMEOUT_MS, callback);
    }

    public void cancelAll() {
        queue.cancelAll(TAG_CHAT);
        queue.cancelAll(TAG_VISION);
    }

    private void enqueue(JSONObject body, String tag, int timeoutMs, GeminiCallback callback) {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                API_URL,
                body,
                response -> {
                    try {
                        String text = extractText(response);
                        callback.onSuccess(text);
                    } catch (Exception e) {
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
                    callback.onError(code, friendlyError(code, errBody));
                }
        ) {
            @Override public String getTag() { return tag; }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                timeoutMs,
                MAX_RETRIES_ON_429,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT) {
            @Override
            public void retry(com.android.volley.VolleyError error) throws com.android.volley.VolleyError {
                int code = error.networkResponse != null
                        ? error.networkResponse.statusCode : -1;
                if (code == 429 || code == -1) {
                    try { Thread.sleep(RATE_LIMIT_RETRY_DELAY_MS); }
                    catch (InterruptedException ignored) {}
                    super.retry(error);
                } else {
                    throw error;
                }
            }
        });

        queue.add(req);
    }

    private JSONObject buildChatBody(String systemPrompt,
                                     List<ChatbotActivity.ChatMessage> history)
            throws Exception {

        JSONArray contents    = new JSONArray();
        String    pendingRole = null;
        StringBuilder pendingText = new StringBuilder();

        int historyEnd = history.size() - 1;
        for (int i = 0; i < historyEnd; i++) {
            ChatbotActivity.ChatMessage msg = history.get(i);
            String role = msg.type == ChatbotActivity.ChatMessage.TYPE_USER ? "user" : "model";
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

        if (!history.isEmpty()) {
            contents.put(makeContent("user", history.get(history.size() - 1).text));
        }

        JSONObject sysInst = new JSONObject()
                .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", systemPrompt)));

        JSONObject genConfig = new JSONObject()
                .put("temperature",     0.7)
                .put("maxOutputTokens", 800);

        return new JSONObject()
                .put("system_instruction", sysInst)
                .put("contents",           contents)
                .put("generationConfig",   genConfig);
    }

    private JSONObject buildVisionBody(String prompt, Bitmap bitmap) throws Exception {
        Bitmap scaled = scaleBitmap(bitmap, 1024);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

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

        JSONObject genConfig = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens",  300)
                .put("temperature",       0.1);

        return new JSONObject()
                .put("contents",         contents)
                .put("generationConfig", genConfig);
    }

    public static String extractText(JSONObject response) throws Exception {
        if (response.has("promptFeedback")) {
            String block = response.getJSONObject("promptFeedback")
                    .optString("blockReason", "");
            if (!block.isEmpty())
                return "I can't help with that topic. Please try rephrasing!";
        }

        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0)
            throw new Exception("No candidates in response");

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

    private static String friendlyError(int code, String body) {
        String apiMsg = "";
        try {
            JSONObject err = new JSONObject(body);
            JSONObject e   = err.optJSONObject("error");
            if (e != null) apiMsg = e.optString("message", "");
        } catch (Exception ignored) {}

        switch (code) {
            case 400:
                return "Bad request (400).";
            case 401:
            case 403:
                return "API Key error. Check console!";
            case 404:
                return "Limit reached.";
            case 429:
                return "Limit reached.";
            case 500:
            case 503:
                return "Gemini is busy/down. Try later.";
            default:
                if (code == -1) return "No internet connection?";
                return "Error " + code + (apiMsg.isEmpty() ? "" : ": " + apiMsg);
        }
    }
}
