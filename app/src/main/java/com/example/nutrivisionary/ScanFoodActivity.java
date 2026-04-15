package com.example.nutrivisionary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFoodActivity extends AppCompatActivity {

    private static final String TAG = "ScanFoodActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ImageView ivCapturedImage;
    private FloatingActionButton fabCapture;
    private MaterialButton btnRetake, btnAnalyze;
    private LinearLayout layoutProcessing;
    private MaterialCardView layoutResult;
    private ImageView btnBack;
    private View viewfinderFrame;
    private TextView tvInstruction;

    // Result views
    private TextView tvResultFoodName, tvResultCalories, tvResultProtein, tvResultCarbs, tvResultDetails;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_food);

        viewFinder = findViewById(R.id.viewFinder);
        ivCapturedImage = findViewById(R.id.ivCapturedImage);
        fabCapture = findViewById(R.id.fabCapture);
        btnRetake = findViewById(R.id.btnRetake);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        layoutProcessing = findViewById(R.id.layoutProcessing);
        layoutResult = findViewById(R.id.layoutResult);
        btnBack = findViewById(R.id.btnBack);
        viewfinderFrame = findViewById(R.id.viewfinderFrame);
        tvInstruction = findViewById(R.id.tvInstruction);

        // Result views
        tvResultFoodName = findViewById(R.id.tvResultFoodName);
        tvResultCalories = findViewById(R.id.tvResultCalories);
        tvResultProtein = findViewById(R.id.tvResultProtein);
        tvResultCarbs = findViewById(R.id.tvResultCarbs);
        tvResultDetails = findViewById(R.id.tvResultDetails);

        btnBack.setOnClickListener(v -> finish());
        btnRetake.setOnClickListener(v -> resetCamera());
        btnAnalyze.setOnClickListener(v -> startAnalysis());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        fabCapture.setOnClickListener(v -> takePhoto());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();

                runOnUiThread(() -> {
                    showCapturedImage(bitmap);
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void showCapturedImage(Bitmap bitmap) {
        ivCapturedImage.setImageBitmap(bitmap);
        ivCapturedImage.setVisibility(View.VISIBLE);
        viewFinder.setVisibility(View.INVISIBLE);
        viewfinderFrame.setVisibility(View.GONE);
        tvInstruction.setVisibility(View.GONE);

        fabCapture.setVisibility(View.GONE);
        btnRetake.setVisibility(View.VISIBLE);
        btnAnalyze.setVisibility(View.VISIBLE);
        
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void resetCamera() {
        ivCapturedImage.setVisibility(View.GONE);
        viewFinder.setVisibility(View.VISIBLE);
        viewfinderFrame.setVisibility(View.VISIBLE);
        tvInstruction.setVisibility(View.VISIBLE);
        layoutResult.setVisibility(View.GONE);

        fabCapture.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
        btnAnalyze.setVisibility(View.GONE);
        btnAnalyze.setEnabled(true);
        btnRetake.setEnabled(true);
        layoutProcessing.setVisibility(View.GONE);

        startCamera();
    }

    private void startAnalysis() {
        btnRetake.setEnabled(false);
        btnAnalyze.setEnabled(false);
        layoutProcessing.setVisibility(View.VISIBLE);

        // Simulate AI Processing and Response
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            layoutProcessing.setVisibility(View.GONE);
            showResult("Grilled Avocado & Quinoa Bowl", "380 kcal", "12g", "45g", 
                    "This nutrient-dense bowl is packed with healthy fats and complex carbohydrates. Great for sustained energy.");
            
            btnRetake.setEnabled(true);
        }, 3000);
    }

    private void showResult(String name, String cal, String protein, String carbs, String details) {
        tvResultFoodName.setText(name);
        tvResultCalories.setText(cal);
        tvResultProtein.setText(protein);
        tvResultCarbs.setText(carbs);
        tvResultDetails.setText(details);
        
        layoutResult.setVisibility(View.VISIBLE);
        btnAnalyze.setVisibility(View.GONE);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
