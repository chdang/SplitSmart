package edu.usc.splitsmart.ui.expense;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import edu.usc.splitsmart.logic.ReceiptHandler;

/**
 * Turns a picked photo into the compact JPEG the database stores, and decodes stored bytes
 * back into bitmaps for display. Phone photos are often 5-10 MB; receipts only need to be
 * readable, so they're shrunk to {@link #MAX_EDGE} pixels on the long side.
 */
final class ReceiptImages {

    static final int MAX_EDGE = 1600;

    /** Refuse to even read anything bigger than this; no real photo is this large. */
    private static final int MAX_SOURCE_BYTES = 40 * 1024 * 1024;

    private ReceiptImages() {
    }

    /** Reads, rotates upright, shrinks, and JPEG-encodes the image. Call off the main thread. */
    static byte[] encode(ContentResolver resolver, Uri uri) throws IOException {
        byte[] raw = readAll(resolver, uri);
        Bitmap bitmap = decode(raw, MAX_EDGE);
        if (bitmap == null) {
            throw new IOException("Not a decodable image");
        }
        bitmap = upright(bitmap, raw);
        int limit = ReceiptHandler.MAX_IMAGE_BYTES;
        // Lower the quality first, then the size, until it fits under the handler's limit.
        for (int edge = MAX_EDGE; edge >= 400; edge = edge * 3 / 4) {
            Bitmap scaled = scaleDown(bitmap, edge);
            for (int quality = 85; quality >= 55; quality -= 15) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                if (out.size() <= limit) {
                    return out.toByteArray();
                }
            }
        }
        throw new IOException("Image still too large after shrinking");
    }

    /** Decodes JPEG/PNG bytes no larger than about {@code maxEdge} on the long side. */
    @Nullable
    static Bitmap decode(byte[] bytes, int maxEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        // Power-of-two sampling is cheap and keeps memory low; scaleDown finishes the job.
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return bitmap == null ? null : scaleDown(bitmap, maxEdge);
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maxEdge) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxEdge) {
            return bitmap;
        }
        float scale = maxEdge / (float) longest;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
    }

    /** Applies the camera's EXIF rotation, which is lost once the image is re-encoded. */
    private static Bitmap upright(Bitmap bitmap, byte[] raw) {
        int degrees;
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(raw));
            degrees = rotationOf(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL));
        } catch (IOException noExif) {
            return bitmap;
        }
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
    }

    private static int rotationOf(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static byte[] readAll(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("No stream for " + uri);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_SOURCE_BYTES) {
                    throw new IOException("Source image too large");
                }
            }
            return out.toByteArray();
        }
    }
}
