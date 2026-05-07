package com.example.nutrivisionary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
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

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFoodActivity extends AppCompatActivity {

    private static final String TAG             = "ScanFoodActivity";
    private static final int    CAMERA_PERM_CODE = 10;

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

    // Last analysis result
    private String lastResultName    = "";
    private double lastResultKcal    = 0;
    private double lastResultProtein = 0;
    private double lastResultCarbs   = 0;
    private double lastResultFat     = 0;

    // mealType MUST keep original casing ("Breakfast","Lunch","Dinner","Snacks")
    // because Firestore fields are "foods_Breakfast" etc.
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
        if (extra != null && !extra.isEmpty()) mealType = extra;

        bindViews();
        setupClickListeners();

        if (allPermissionsGranted()) startCamera();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERM_CODE);

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    // ─────────────────────────────────────────────────────────────
    // VIEWS
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
        if (btnBack    != null) btnBack.setOnClickListener(v  -> finish());
        if (fabCapture != null) fabCapture.setOnClickListener(v -> takePhoto());
        if (btnRetake  != null) btnRetake.setOnClickListener(v  -> resetCamera());
        if (btnAnalyze != null) btnAnalyze.setOnClickListener(v -> startAnalysis());
        if (btnLogFood != null) {
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
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
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
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
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
        if (cameraProvider != null) cameraProvider.unbindAll();
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
    // ANALYSIS — via GeminiClient (Volley)
    // ─────────────────────────────────────────────────────────────

    private void startAnalysis() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show();
            return;
        }
        btnRetake.setEnabled(false);
        btnAnalyze.setEnabled(false);
        if (layoutProcessing != null) layoutProcessing.setVisibility(View.VISIBLE);

        // Prompt: demand pure JSON, no markdown, exact keys
        String prompt =
                "You are a nutrition expert. Identify the food in this image. "
                        + "Return ONLY a raw JSON object — no markdown, no backticks, no extra text. "
                        + "Use exactly these keys:\n"
                        + "{\"name\": string, \"calories\": integer, \"protein_g\": number, "
                        + "\"carbs_g\": number, \"fat_g\": number, \"description\": string}\n"
                        + "Give values for a typical single serving. "
                        + "If no food is visible use name=\"Unknown Food\" and 0 for all numbers.";

        GeminiClient.getInstance(this).sendVisionRequest(
                prompt,
                capturedBitmap,
                new GeminiClient.GeminiCallback() {
                    @Override
                    public void onSuccess(String jsonText) {
                        // jsonText is already the raw text from the model
                        handleVisionResult(jsonText);
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.e(TAG, "Vision error " + code + ": " + message);
                        hideProcessing();
                        Toast.makeText(ScanFoodActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleVisionResult(String rawText) {
        hideProcessing();
        try {
            // Extract the JSON object from the text (strips any residual markdown)
            String jsonStr = extractJsonObject(rawText);
            Log.d(TAG, "Parsed JSON: " + jsonStr);

            JSONObject result = new JSONObject(jsonStr);
            lastResultName    = result.optString("name",        "Unknown Food");
            lastResultKcal    = result.optDouble("calories",     0);
            lastResultProtein = result.optDouble("protein_g",    0);
            lastResultCarbs   = result.optDouble("carbs_g",      0);
            lastResultFat     = result.optDouble("fat_g",        0);
            String desc       = result.optString("description", "No details available.");

            if (tvResultFoodName != null) tvResultFoodName.setText(lastResultName);
            if (tvResultCalories != null)
                tvResultCalories.setText(String.format(Locale.getDefault(), "%d kcal", (int) lastResultKcal));
            if (tvResultProtein  != null)
                tvResultProtein.setText(String.format(Locale.getDefault(), "%.1fg", lastResultProtein));
            if (tvResultCarbs    != null)
                tvResultCarbs.setText(String.format(Locale.getDefault(), "%.1fg", lastResultCarbs));
            if (tvResultFat      != null)
                tvResultFat.setText(String.format(Locale.getDefault(), "%.1fg", lastResultFat));
            if (tvResultDetails  != null) tvResultDetails.setText(desc);

            if (layoutResult != null) layoutResult.setVisibility(View.VISIBLE);
            if (btnAnalyze   != null) btnAnalyze.setVisibility(View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "JSON parse error. Raw: " + rawText, e);
            Toast.makeText(this,
                    "Could not read food data. Try a clearer photo.", Toast.LENGTH_LONG).show();
            if (btnAnalyze != null) btnAnalyze.setEnabled(true);
        }
    }

    private void hideProcessing() {
        if (layoutProcessing != null) layoutProcessing.setVisibility(View.GONE);
        btnRetake.setEnabled(true);
    }

    /**
     * Extracts the first complete {...} JSON object from a string
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
    // LOG TO FIREBASE — atomic FieldValue.increment, no pre-fetch
    // ─────────────────────────────────────────────────────────────

    private void logFoodToFirebase() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastResultKcal == 0 && lastResultName.isEmpty()) {
            Toast.makeText(this, "No food result to log", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogFood.setEnabled(false);

        String uid       = mAuth.getCurrentUser().getUid();
        String today     = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // IMPORTANT: do NOT call .toLowerCase() — Firestore fields are case-sensitive
        // and HomeDashboard reads "foods_Breakfast", "foods_Lunch" etc.
        String mealField = "foods_" + mealType;
        String entry     = String.format(Locale.getDefault(),
                "%s (%d kcal)", lastResultName, (int) lastResultKcal);

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
                    Toast.makeText(this,
                            "Failed to log: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this,
                        "Camera permission required", Toast.LENGTH_LONG).show();
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