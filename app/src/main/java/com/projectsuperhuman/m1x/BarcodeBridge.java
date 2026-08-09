package com.projectsuperhuman.m1x;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native barcode bridge used by the Food Tracker.
 *
 * Gallery/screenshot path:
 *  - Android system picker returns the original full-resolution image URI.
 *  - Bundled ML Kit barcode scanning decodes EAN/UPC directly from that URI.
 *  - ZXing is kept as a secondary fallback for awkward images.
 *
 * Camera path:
 *  - Camera writes a full-resolution photo into our cache through FileProvider.
 *  - We never rely on the tiny Intent "data" thumbnail.
 */
public final class BarcodeBridge {
    private static final int PICK_BARCODE = 7631;
    private static final int CAMERA_BARCODE = 7632;

    private final MainActivitx activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BarcodeScanner scanner;

    private Uri pendingCameraUri;
    private File pendingCameraFile;

    BarcodeBridge(MainActivitx a) {
        activity = a;
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E)
                .enableAllPotentialBarcodes()
                .build();
        scanner = BarcodeScanning.getClient(options);
    }


    @JavascriptInterface
    public void startLiveScan() {
        activity.runOnUiThread(() -> {
            try {
                IntentIntegrator integrator = new IntentIntegrator(activity);
                integrator.setDesiredBarcodeFormats(Arrays.asList(
                        IntentIntegrator.EAN_13,
                        IntentIntegrator.EAN_8,
                        IntentIntegrator.UPC_A,
                        IntentIntegrator.UPC_E));
                integrator.setPrompt("Point the camera at the product barcode");
                integrator.setBeepEnabled(false);
                integrator.setBarcodeImageEnabled(false);
                integrator.setOrientationLocked(true);
                integrator.setCameraId(0);
                integrator.initiateScan();
            } catch (Exception e) {
                emitError("Could not start the live barcode scanner.");
            }
        });
    }

    @JavascriptInterface
    public void pickImage() {
        activity.runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(i, PICK_BARCODE);
        });
    }

    @JavascriptInterface
    public void capturePhoto() {
        activity.runOnUiThread(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "barcode-camera");
                if (!dir.exists() && !dir.mkdirs()) {
                    emitError("Could not prepare the camera cache.");
                    return;
                }
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                pendingCameraFile = File.createTempFile("barcode_" + stamp + "_", ".jpg", dir);
                pendingCameraUri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + ".fileprovider",
                        pendingCameraFile);

                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.setClipData(ClipData.newRawUri("barcode_photo", pendingCameraUri));

                if (i.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivityForResult(i, CAMERA_BARCODE);
                } else {
                    cleanupCameraFile();
                    emitError("No camera app is available.");
                }
            } catch (Exception e) {
                cleanupCameraFile();
                emitError("Could not start the camera.");
            }
        });
    }

    boolean onActivityResult(int request, int result, Intent data) {
        if (request == IntentIntegrator.REQUEST_CODE) {
            IntentResult live = IntentIntegrator.parseActivityResult(request, result, data);
            if (live == null || live.getContents() == null) {
                emitError("Barcode scan cancelled.");
            } else {
                String code = digitsOnly(live.getContents());
                if (looksLikeRetailBarcode(code)) emitCode(code, "native_live_zxing");
                else emitError("That was not a supported EAN/UPC retail barcode.");
            }
            return true;
        }
        if (request != PICK_BARCODE && request != CAMERA_BARCODE) return false;
        if (result != Activity.RESULT_OK) {
            if (request == CAMERA_BARCODE) cleanupCameraFile();
            emitError("Barcode scan cancelled.");
            return true;
        }

        if (request == PICK_BARCODE) {
            Uri uri = data == null ? null : data.getData();
            if (uri == null) {
                emitError("No image was selected.");
                return true;
            }
            try {
                final int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            scanUri(uri, false);
        } else {
            if (pendingCameraUri == null) {
                emitError("Camera did not return a full-resolution photo.");
                cleanupCameraFile();
                return true;
            }
            scanUri(pendingCameraUri, true);
        }
        return true;
    }

    private void scanUri(Uri uri, boolean deleteAfter) {
        activity.runOnUiThread(() -> {
            try {
                InputImage image = InputImage.fromFilePath(activity, uri);
                scanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            String code = chooseBarcode(barcodes);
                            if (code != null) {
                                emitCode(code);
                                if (deleteAfter) cleanupCameraFile();
                            } else {
                                // ML Kit could see the file but did not decode a supported EAN/UPC.
                                // ZXing gets a second chance using several scale/crop variants.
                                worker.execute(() -> zxingFallback(uri, deleteAfter));
                            }
                        })
                        .addOnFailureListener(err -> worker.execute(() -> zxingFallback(uri, deleteAfter)));
            } catch (IOException e) {
                worker.execute(() -> zxingFallback(uri, deleteAfter));
            } catch (Exception e) {
                if (deleteAfter) cleanupCameraFile();
                emitError("Could not read that image.");
            }
        });
    }

    private String chooseBarcode(List<Barcode> barcodes) {
        if (barcodes == null) return null;
        for (Barcode b : barcodes) {
            int f = b.getFormat();
            if (f == Barcode.FORMAT_EAN_13 || f == Barcode.FORMAT_EAN_8 ||
                    f == Barcode.FORMAT_UPC_A || f == Barcode.FORMAT_UPC_E) {
                String raw = digitsOnly(b.getRawValue());
                if (looksLikeRetailBarcode(raw)) return raw;
            }
        }
        return null;
    }

    private void zxingFallback(Uri uri, boolean deleteAfter) {
        Bitmap source = null;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in);
            if (source == null) {
                emitError("Could not decode that image.");
                return;
            }
            String code = decodeWithZxing(source);
            if (code == null || code.isEmpty()) {
                emitError("No EAN/UPC barcode found. Try the original image, a closer crop, or enter the number manually.");
            } else {
                emitCode(code);
            }
        } catch (Exception e) {
            emitError("Could not read that image.");
        } finally {
            if (source != null && !source.isRecycled()) source.recycle();
            if (deleteAfter) cleanupCameraFile();
        }
    }

    private String decodeWithZxing(Bitmap input) {
        Bitmap[] variants = buildVariants(input);
        try {
            for (Bitmap b : variants) {
                if (b == null) continue;
                String x = decodeOne(b);
                if (looksLikeRetailBarcode(x)) return digitsOnly(x);
            }
        } finally {
            for (Bitmap b : variants) {
                if (b != null && b != input && !b.isRecycled()) b.recycle();
            }
        }
        return null;
    }

    private Bitmap[] buildVariants(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap centerWide = null, lower = null, upper = null;
        try {
            if (h > 180) {
                int band = Math.max(150, (int) (h * 0.45f));
                int y = Math.max(0, (h - band) / 2);
                centerWide = Bitmap.createBitmap(src, 0, y, w, Math.min(band, h - y));
            }
            if (h > 300) {
                int y = (int) (h * 0.45f);
                lower = Bitmap.createBitmap(src, 0, y, w, h - y);
                upper = Bitmap.createBitmap(src, 0, 0, w, Math.max(1, (int) (h * 0.60f)));
            }
        } catch (Exception ignored) {}

        Bitmap srcLarge = scaleForBarcode(src, 2200);
        Bitmap centreLarge = scaleForBarcode(centerWide, 2200);
        Bitmap lowerLarge = scaleForBarcode(lower, 2200);
        Bitmap upperLarge = scaleForBarcode(upper, 2200);
        return new Bitmap[]{src, srcLarge, centerWide, centreLarge, lower, lowerLarge, upper, upperLarge};
    }

    private Bitmap scaleForBarcode(Bitmap b, int targetW) {
        if (b == null || b.getWidth() >= targetW) return b;
        int th = Math.max(120, Math.round((float) b.getHeight() * targetW / b.getWidth()));
        return Bitmap.createScaledBitmap(b, targetW, th, false);
    }

    private String decodeOne(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w <= 0 || h <= 0 || (long) w * h > 30_000_000L) return null;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        RGBLuminanceSource lum = new RGBLuminanceSource(w, h, px);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Arrays.asList(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);

        MultiFormatReader r = new MultiFormatReader();
        r.setHints(hints);
        try {
            Result x = r.decodeWithState(new BinaryBitmap(new HybridBinarizer(lum)));
            return x.getText();
        } catch (Exception ignored) {}
        r.reset();
        r.setHints(hints);
        try {
            Result x = r.decodeWithState(new BinaryBitmap(new GlobalHistogramBinarizer(lum)));
            return x.getText();
        } catch (Exception ignored) {}
        return null;
    }

    private boolean looksLikeRetailBarcode(String value) {
        String d = digitsOnly(value);
        return d != null && (d.length() == 8 || d.length() == 12 || d.length() == 13);
    }

    private String digitsOnly(String value) {
        if (value == null) return null;
        String d = value.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : d;
    }

    private void cleanupCameraFile() {
        try {
            if (pendingCameraFile != null && pendingCameraFile.exists()) pendingCameraFile.delete();
        } catch (Exception ignored) {}
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    void close() {
        try { scanner.close(); } catch (Exception ignored) {}
        worker.shutdownNow();
        cleanupCameraFile();
    }

    private void emitCode(String code) { emitCode(code, "native_mlkit"); }

    private void emitCode(String code, String engine) {
        try {
            JSONObject j = new JSONObject();
            j.put("ok", true);
            j.put("code", code);
            j.put("engine", engine);
            emit(j);
        } catch (Exception ignored) {}
    }

    private void emitError(String msg) {
        try {
            JSONObject j = new JSONObject();
            j.put("ok", false);
            j.put("message", msg);
            j.put("engine", "native_mlkit");
            emit(j);
        } catch (Exception ignored) {}
    }

    private void emit(JSONObject j) {
        activity.runOnUiThread(() -> activity.webView.evaluateJavascript(
                "window.ProjectSuperhumanBarcode&&ProjectSuperhumanBarcode.onResult(" +
                        JSONObject.quote(j.toString()) + ");", null));
    }
}
