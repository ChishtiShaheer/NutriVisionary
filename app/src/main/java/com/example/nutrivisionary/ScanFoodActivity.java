package com.example.nutrivisionary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ScanFoodActivity extends AppCompatActivity {

    private static final String TAG = "ScanFoodActivity";
    private static final int    CAMERA_PERM_CODE = 10;

    // ── gemini-2.0-flash: stable multimodal model, supports vision ──
    // gemini-1.0-pro   → REMOVED, always 404, do not use
    // gemini-1.5-flash → works but older generation
    // gemini-2.0-flash → current stable release, best balance of speed + quality
    private static final String GEMINI_API_KEY = "AIzaSyCazWtsFIImCOW47w_G-szFPN-TlWeqjlU";
    private static final String GEMINI_MODEL   = "gemini-2.0-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY;
    // ────────────────────────────────────────────────────────────────

    // Views
    private PreviewView          viewFinder;
    private ImageView            ivCapturedImage;
    private View                 btnBack, viewfinderFrame;
    private FloatingActionButton fabCapture;
    private MaterialButton       btnRetake, btnAnalyze, btnLogFood;
    private LinearLayout         layoutProcessing;
    private MaterialCardView     layoutResult;
    private TextView             tvInstruction;
    private TextView             tvResultFoodName, tvResultCalories,
            tvResultProtein, tvResultCarbs,
            tvResultFat, tvResultDetails;

    // Camera
    private ImageCapture          imageCapture;
    private ExecutorService       cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Bitmap                capturedBitmap;

    // Firebase
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // HTTP — longer timeout for vision requests
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60,  java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90,   java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // Last scanned result
    private String lastResultName    = "";
    private double lastResultKcal    = 0;
    private double lastResultProtein = 0;
    private double lastResultCarbs   = 0;
    private double lastResultFat     = 0;

    private String mealType = "Snacks";

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_food);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        String extra = getIntent().getStringExtra("mealType");
        mealType = extra != null ? extra : "Snacks";

        bindViews();
        setupClickListeners();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERM_CODE);
        }
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    // ─────────────────────────────────────────────────────────────
    // VIEWS & CLICKS
    // ─────────────────────────────────────────────────────────────

    private void bindViews() {
        viewFinder       = findViewById(R.id.viewFinder);
        ivCapturedImage  = findViewById(R.id.ivCapturedImage);
        fabCapture       = findViewById(R.id.fabCapture);
        btnRetake        = findViewById(R.id.btnRetake);
        btnAnalyze       = findViewById(R.id.btnAnalyze);
        btnLogFood       = findViewById(R.id.btnLogFood);
        layoutProcessing = findViewById(R.id.layoutProcessing);
        layoutResult     = findViewById(R.id.layoutResult);
        btnBack          = findViewById(R.id.btnBack);
        viewfinderFrame  = findViewById(R.id.viewfinderFrame);
        tvInstruction    = findViewById(R.id.tvInstruction);

        tvResultFoodName = findViewById(R.id.tvResultFoodName);
        tvResultCalories = findViewById(R.id.tvResultCalories);
        tvResultProtein  = findViewById(R.id.tvResultProtein);
        tvResultCarbs    = findViewById(R.id.tvResultCarbs);
        tvResultFat      = findViewById(R.id.tvResultFat);
        tvResultDetails  = findViewById(R.id.tvResultDetails);
    }

    private void setupClickListeners() {
        if (btnBack     != null) btnBack.setOnClickListener(v -> finish());
        if (fabCapture  != null) fabCapture.setOnClickListener(v -> takePhoto());
        if (btnRetake   != null) btnRetake.setOnClickListener(v -> resetCamera());
        if (btnAnalyze  != null) btnAnalyze.setOnClickListener(v -> startAnalysis());
        if (btnLogFood  != null) {
            btnLogFood.setText(String.format("Log to %s", mealType));
            btnLogFood.setOnClickListener(v -> logFoodToFirebase());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CAMERA
    // ─────────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera bind error", e);
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        capturedBitmap = imageProxyToBitmap(image);
                        image.close();
                        showCapturedImage(capturedBitmap);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Capture error", e);
                        Toast.makeText(ScanFoodActivity.this,
                                "Capture failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Matrix m = new Matrix();
        m.postRotate(image.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private void showCapturedImage(Bitmap bitmap) {
        ivCapturedImage.setImageBitmap(bitmap);
        ivCapturedImage.setVisibility(View.VISIBLE);
        viewFinder.setVisibility(View.INVISIBLE);
        if (viewfinderFrame != null) viewfinderFrame.setVisibility(View.GONE);
        if (tvInstruction   != null) tvInstruction.setVisibility(View.GONE);
        fabCapture.setVisibility(View.GONE);
        btnRetake.setVisibility(View.VISIBLE);
        btnAnalyze.setVisibility(View.VISIBLE);
        if (layoutResult != null) layoutResult.setVisibility(View.GONE);
        if (cameraProvider  != null) cameraProvider.unbindAll();
    }

    private void resetCamera() {
        capturedBitmap = null;
        lastResultName = ""; lastResultKcal = 0;
        lastResultProtein = 0; lastResultCarbs = 0; lastResultFat = 0;

        ivCapturedImage.setVisibility(View.GONE);
        viewFinder.setVisibility(View.VISIBLE);
        if (viewfinderFrame  != null) viewfinderFrame.setVisibility(View.VISIBLE);
        if (tvInstruction    != null) tvInstruction.setVisibility(View.VISIBLE);
        if (layoutResult     != null) layoutResult.setVisibility(View.GONE);
        if (layoutProcessing != null) layoutProcessing.setVisibility(View.GONE);
        fabCapture.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
        btnAnalyze.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(true);
        btnRetake.setEnabled(true);
        startCamera();
    }

    // ─────────────────────────────────────────────────────────────
    // VISION ANALYSIS
    // ─────────────────────────────────────────────────────────────

    private void startAnalysis() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "No image to analyse", Toast.LENGTH_SHORT).show();
            return;
        }
        btnRetake.setEnabled(false);
        btnAnalyze.setEnabled(false);
        if (layoutProcessing != null) layoutProcessing.setVisibility(View.VISIBLE);
        callGeminiVision(capturedBitmap);
    }

    private void callGeminiVision(Bitmap bitmap) {
        // Scale down if too large to stay well under the 4 MB inline_data limit
        Bitmap scaled = scaleBitmap(bitmap, 1024);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        byte[] imgBytes    = baos.toByteArray();
        String base64Image = Base64.encodeToString(imgBytes, Base64.NO_WRAP);
        Log.d(TAG, "Image bytes: " + imgBytes.length);

        try {
            // Prompt: demand pure JSON, no markdown
            String prompt =
                    "You are a nutrition expert. Look at this food image and return ONLY a raw JSON "
                            + "object — no markdown, no backticks, no explanation text before or after it. "
                            + "Use exactly these keys:\n"
                            + "{\"name\": string, \"calories\": integer, \"protein_g\": number, "
                            + "\"carbs_g\": number, \"fat_g\": number, \"description\": string}\n"
                            + "Give realistic values for a typical single serving. "
                            + "If no food is visible, use name=\"Unknown Food\" and 0 for all numbers.";

            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            parts.put(new JSONObject()
                    .put("inline_data", new JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)));

            JSONObject content = new JSONObject();
            content.put("role",  "user");
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            // responseMimeType forces Gemini to return JSON only (2.0-flash supports this)
            JSONObject genConfig = new JSONObject();
            genConfig.put("responseMimeType", "application/json");
            genConfig.put("maxOutputTokens",  256);
            genConfig.put("temperature",       0.1); // near-zero for factual data

            JSONObject requestBody = new JSONObject();
            requestBody.put("contents",         contents);
            requestBody.put("generationConfig", genConfig);

            Log.d(TAG, "→ Vision POST to " + API_URL);

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
                    Log.e(TAG, "Vision network failure", e);
                    runOnUiThread(() -> {
                        if (layoutProcessing != null) layoutProcessing.setVisibility(View.GONE);
                        btnRetake.setEnabled(true);
                        btnAnalyze.setEnabled(true);
                        Toast.makeText(ScanFoodActivity.this,
                                "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    String resStr = response.body() != null ? response.body().string() : "";
                    int    code   = response.code();
                    Log.d(TAG, "← HTTP " + code + " body: " + resStr);
                    runOnUiThread(() -> handleVisionResponse(code, resStr));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Vision request build error", e);
            if (layoutProcessing != null) layoutProcessing.setVisibility(View.GONE);
            btnRetake.setEnabled(true);
            btnAnalyze.setEnabled(true);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Scale bitmap so its longest edge is at most maxDim pixels. */
    private Bitmap scaleBitmap(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return Bitmap.createScaledBitmap(src, (int)(w*scale), (int)(h*scale), true);
    }

    private void handleVisionResponse(int httpCode, String resStr) {
        if (layoutProcessing != null) layoutProcessing.setVisibility(View.GONE);
        btnRetake.setEnabled(true);

        // ── Diagnose non-200 errors clearly ──────────────────────
        if (httpCode != 200) {
            String hint;
            switch (httpCode) {
                case 400: hint = "Bad request — invalid image or request format"; break;
                case 403: hint = "API key invalid or restricted"; break;
                case 404: hint = "Model not found — check model name"; break;
                case 429: hint = "Rate limit exceeded — wait a moment"; break;
                case 500: hint = "Gemini server error — try again"; break;
                default:  hint = "Unexpected error";
            }
            Log.e(TAG, "HTTP " + httpCode + " — " + hint + "\n" + resStr);
            Toast.makeText(this,
                    "Analysis failed (HTTP " + httpCode + "): " + hint,
                    Toast.LENGTH_LONG).show();
            btnAnalyze.setEnabled(true);
            return;
        }

        try {
            JSONObject json       = new JSONObject(resStr);

            // Safety / blocking check
            if (json.has("promptFeedback")) {
                String block = json.getJSONObject("promptFeedback")
                        .optString("blockReason","");
                if (!block.isEmpty()) {
                    Toast.makeText(this, "Image blocked: " + block, Toast.LENGTH_LONG).show();
                    btnAnalyze.setEnabled(true);
                    return;
                }
            }

            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new Exception("No candidates in response");
            }

            String finishReason = candidates.getJSONObject(0).optString("finishReason","STOP");
            if (finishReason.equals("SAFETY")) {
                Toast.makeText(this, "Image blocked by safety filter", Toast.LENGTH_LONG).show();
                btnAnalyze.setEnabled(true);
                return;
            }

            String rawText = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim();

            Log.d(TAG, "Raw text from Gemini: " + rawText);

            // Strip any markdown the model might add despite instructions
            String jsonText = extractJsonObject(rawText);
            Log.d(TAG, "Extracted JSON: " + jsonText);

            JSONObject result = new JSONObject(jsonText);
            lastResultName    = result.optString("name",        "Unknown Food");
            lastResultKcal    = result.optDouble("calories",     0);
            lastResultProtein = result.optDouble("protein_g",    0);
            lastResultCarbs   = result.optDouble("carbs_g",      0);
            lastResultFat     = result.optDouble("fat_g",        0);
            String desc       = result.optString("description", "No details available.");

            // Display results
            if (tvResultFoodName != null) tvResultFoodName.setText(lastResultName);
            if (tvResultCalories != null) tvResultCalories.setText(
                    String.format(Locale.getDefault(), "%d kcal", (int) lastResultKcal));
            if (tvResultProtein  != null) tvResultProtein.setText(
                    String.format(Locale.getDefault(), "%.1fg", lastResultProtein));
            if (tvResultCarbs    != null) tvResultCarbs.setText(
                    String.format(Locale.getDefault(), "%.1fg", lastResultCarbs));
            if (tvResultFat      != null) tvResultFat.setText(
                    String.format(Locale.getDefault(), "%.1fg", lastResultFat));
            if (tvResultDetails  != null) tvResultDetails.setText(desc);

            if (layoutResult != null) layoutResult.setVisibility(View.VISIBLE);
            if (btnAnalyze   != null) btnAnalyze.setVisibility(View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "Parse error. Raw: " + resStr, e);
            Toast.makeText(this,
                    "Could not read food data. Try a clearer photo.", Toast.LENGTH_LONG).show();
            btnAnalyze.setEnabled(true);
        }
    }

    /**
     * Robustly extracts the first complete {...} JSON object from a string
     * that may contain surrounding markdown or explanatory text.
     */
    private String extractJsonObject(String text) throws Exception {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start)
            throw new Exception("No JSON object found in: " + text);
        return text.substring(start, end + 1);
    }

    // ─────────────────────────────────────────────────────────────
    // LOG TO FIREBASE — atomic, no pre-fetch
    // ─────────────────────────────────────────────────────────────

    private void logFoodToFirebase() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastResultName.isEmpty() || lastResultName.equals("Unknown Food") && lastResultKcal == 0) {
            Toast.makeText(this, "No food identified to log", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogFood.setEnabled(false);

        String uid       = mAuth.getCurrentUser().getUid();
        String today     = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String mealField = "foods_" + mealType;
        String entry     = String.format(Locale.getDefault(),
                "%s (%d kcal)", lastResultName, (int) lastResultKcal);

        // FieldValue.increment is atomic — no pre-read needed, works even if
        // the document doesn't exist yet (Firestore creates it on first .set+merge)
        Map<String, Object> update = new HashMap<>();
        update.put("consumedKcal",    FieldValue.increment(lastResultKcal));
        update.put("consumedProtein", FieldValue.increment(lastResultProtein));
        update.put("consumedCarbs",   FieldValue.increment(lastResultCarbs));
        update.put("consumedFat",     FieldValue.increment(lastResultFat));
        update.put(mealField,         FieldValue.arrayUnion(entry));
        update.put("lastUpdated",     FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("logs").document(today)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(a -> {
                    Toast.makeText(this,
                            lastResultName + " logged to " + mealType + " ✓",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore write failed", e);
                    btnLogFood.setEnabled(true);
                    Toast.makeText(this, "Failed to log: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ─────────────────────────────────────────────────────────────
    // PERMISSIONS
    // ─────────────────────────────────────────────────────────────

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == CAMERA_PERM_CODE) {
            if (allPermissionsGranted()) startCamera();
            else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}