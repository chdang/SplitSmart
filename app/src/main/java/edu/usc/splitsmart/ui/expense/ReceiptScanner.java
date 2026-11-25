package edu.usc.splitsmart.ui.expense;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scans paper receipts with the camera. It uses Google's document scanner, which finds the
 * receipt's edges, flattens and crops it, and can take several pages in one go. On devices
 * without Google Play services it falls back to a plain photo from the camera app.
 *
 * <p>Either way the result is a list of image URIs, handled exactly like photos from the picker.
 */
final class ReceiptScanner {

    /** Long receipts sometimes take a few shots; more than this is almost certainly a mistake. */
    static final int MAX_PAGES = 5;

    /**
     * Tests set this to skip the document scanner, which runs in another app that Espresso
     * can't drive, and exercise the camera path instead.
     */
    @VisibleForTesting
    static boolean useDocumentScanner = true;

    private final AppCompatActivity activity;
    private final Consumer<List<Uri>> onScanned;
    private final Runnable onUnavailable;
    private final ActivityResultLauncher<IntentSenderRequest> scanner;
    private final ActivityResultLauncher<Uri> camera;

    /** Where the camera app is writing its photo, while it's open. */
    @Nullable private File cameraFile;

    /**
     * Must be created in onCreate, since it registers for activity results. {@code onScanned}
     * gets the pages; {@code onUnavailable} runs when this device can't scan at all.
     */
    ReceiptScanner(AppCompatActivity activity, Consumer<List<Uri>> onScanned,
                   Runnable onUnavailable) {
        this.activity = activity;
        this.onScanned = onScanned;
        this.onUnavailable = onUnavailable;
        scanner = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(), this::onScannerResult);
        camera = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(), this::onCameraResult);
    }

    void scan() {
        if (!useDocumentScanner) {
            takePhoto();
            return;
        }
        GmsDocumentScanner client = GmsDocumentScanning.getClient(
                new GmsDocumentScannerOptions.Builder()
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setPageLimit(MAX_PAGES)
                        // "Attach receipt" already covers photos that are in the gallery.
                        .setGalleryImportAllowed(false)
                        .build());
        client.getStartScanIntent(activity)
                .addOnSuccessListener(sender ->
                        scanner.launch(new IntentSenderRequest.Builder(sender).build()))
                // No Play services, or its scanner module couldn't be installed.
                .addOnFailureListener(error -> takePhoto());
    }

    /** Throws away a camera photo left over from a screen that closed mid-capture. */
    void discard() {
        if (cameraFile != null) {
            cameraFile.delete();
            cameraFile = null;
        }
    }

    private void onScannerResult(ActivityResult result) {
        GmsDocumentScanningResult scan = result.getResultCode() == Activity.RESULT_OK
                ? GmsDocumentScanningResult.fromActivityResultIntent(result.getData()) : null;
        if (scan == null || scan.getPages() == null || scan.getPages().isEmpty()) {
            return;   // the user backed out of the scanner
        }
        List<Uri> pages = new ArrayList<>();
        for (GmsDocumentScanningResult.Page page : scan.getPages()) {
            pages.add(page.getImageUri());
        }
        onScanned.accept(pages);
    }

    private void takePhoto() {
        File dir = new File(activity.getCacheDir(), "receipts_camera");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            onUnavailable.run();
            return;
        }
        discard();
        cameraFile = new File(dir, UUID.randomUUID() + ".jpg");
        Uri output = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".receipts", cameraFile);
        try {
            camera.launch(output);
        } catch (ActivityNotFoundException noCameraApp) {
            discard();
            onUnavailable.run();
        }
    }

    private void onCameraResult(Boolean saved) {
        File photo = cameraFile;
        if (!Boolean.TRUE.equals(saved) || photo == null || photo.length() == 0) {
            discard();   // cancelled, or the camera app wrote nothing
            return;
        }
        // Kept until the next photo or discard(): ReceiptSection reads it on a worker thread.
        onScanned.accept(Collections.singletonList(Uri.fromFile(photo)));
    }
}
