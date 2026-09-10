package io.github.jaffe2718.petprofile.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.LanTransferPayload;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;
import io.github.jaffe2718.petprofile.util.KeeperInfoManager;
import io.github.jaffe2718.petprofile.util.LanTransferClient;
import io.github.jaffe2718.petprofile.util.QrCodeUtil;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrScannerActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 5401;
    private static final int REQUEST_GALLERY = 5402;

    private PetRepository repository;
    private PreviewView previewView;
    private BarcodeScanner barcodeScanner;
    private volatile boolean processed;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        hideSystemBars();
        repository = PetRepository.get(this);
        previewView = findViewById(R.id.previewView);
        barcodeScanner = BarcodeScanning.getClient();
        findViewById(R.id.closeButton).setOnClickListener(v -> finish());
        findViewById(R.id.pickFromGalleryButton).setOnClickListener(v -> pickFromGallery());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
            } catch (Throwable t) {
                Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GALLERY || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        analysisExecutor.execute(() -> {
            try {
                Bitmap bitmap = loadScaledBitmap(uri);
                if (bitmap == null) {
                    Async.ui(() -> Toast.makeText(this, R.string.qr_parse_failed, Toast.LENGTH_LONG).show());
                    return;
                }
                barcodeScanner.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener(barcodes -> {
                            if (!barcodes.isEmpty() && barcodes.get(0).getRawValue() != null) {
                                processed = true;
                                handleQrText(barcodes.get(0).getRawValue());
                            } else {
                                Async.ui(() -> Toast.makeText(this, R.string.qr_parse_failed, Toast.LENGTH_LONG).show());
                            }
                        })
                        .addOnFailureListener(e ->
                                Async.ui(() -> Toast.makeText(this, R.string.qr_parse_failed, Toast.LENGTH_LONG).show()));
            } catch (Throwable t) {
                Async.ui(() -> Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private Bitmap loadScaledBitmap(Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        } catch (Throwable ignored) {
            return null;
        }
        int sample = 1;
        int max = 2048;
        while (options.outWidth / sample > max || options.outHeight / sample > max) {
            sample *= 2;
        }
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sample;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, decodeOptions);
        } catch (Throwable t) {
            return null;
        }
    }

    private void analyze(@NonNull ImageProxy imageProxy) {
        if (processed) {
            imageProxy.close();
            return;
        }
        try {
            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );
            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && !processed) {
                            String value = barcodes.get(0).getRawValue();
                            if (value != null) {
                                processed = true;
                                handleQrText(value);
                            }
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(error -> imageProxy.close());
        } catch (Throwable t) {
            imageProxy.close();
        }
    }

    private void handleQrText(String text) {
        LanTransferPayload lanPayload = QrCodeUtil.parseLanTransferPayload(text);
        if (lanPayload != null) {
            handleLanTransfer(lanPayload);
            return;
        }
        Async.run(() -> {
            try {
                ExportBundle bundle = QrCodeUtil.parseTransferQrText(text);
                if (bundle == null) {
                    Async.ui(() -> {
                        Toast.makeText(this, R.string.qr_too_large_use_zip, Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                repository.importTransferBundle(bundle, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(QrScannerActivity.this, R.string.imported, Toast.LENGTH_SHORT).show();
                        RoutineScheduler.scheduleAll(QrScannerActivity.this);
                        RoutineNotifier.sync(QrScannerActivity.this);
                        finish();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Toast.makeText(QrScannerActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            } catch (Throwable t) {
                Async.ui(() -> {
                    Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void handleLanTransfer(LanTransferPayload payload) {
        Toast.makeText(this, R.string.transfer_lan_connecting, Toast.LENGTH_LONG).show();
        // A transfer streams a whole profile tree, so it must not occupy the data-read executor.
        Async.runLong(() -> {
            try {
                byte[] zipBytes = LanTransferClient.download(payload, KeeperInfoManager.load(QrScannerActivity.this));
                ExportBundle bundle = BackupManager.readZipBytes(QrScannerActivity.this, zipBytes);
                repository.importTransferBundle(bundle, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(QrScannerActivity.this, R.string.imported, Toast.LENGTH_SHORT).show();
                        RoutineScheduler.scheduleAll(QrScannerActivity.this);
                        RoutineNotifier.sync(QrScannerActivity.this);
                        finish();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Toast.makeText(QrScannerActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            } catch (Throwable t) {
                Async.ui(() -> {
                    Toast.makeText(QrScannerActivity.this, R.string.transfer_lan_failed, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == REQUEST_CAMERA) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
    }
}
