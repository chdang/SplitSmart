package edu.usc.splitsmart.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.usc.splitsmart.model.Receipt;

/** Stores receipt images as BLOBs in the receipts table of {@link SplitSmartDatabase}. */
public final class SqliteReceiptRepository implements ReceiptRepository {

    /** Every column except the image, so listing receipts never pulls the bytes. */
    private static final String[] METADATA = {"id", "expense_id", "uploader_id", "added_at"};

    private final SplitSmartDatabase database;

    public SqliteReceiptRepository(SplitSmartDatabase database) {
        this.database = database;
    }

    @Override
    public List<Receipt> saveReceipts(String expenseId, String uploaderId, List<byte[]> images,
                                      Instant addedAt) {
        SQLiteDatabase db = database.getWritableDatabase();
        List<Receipt> saved = new ArrayList<>();
        db.beginTransaction();
        try {
            for (byte[] image : images) {
                String id = UUID.randomUUID().toString();
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("expense_id", expenseId);
                values.put("uploader_id", uploaderId);
                values.put("image", image);
                values.put("added_at", addedAt.toEpochMilli());
                db.insertOrThrow(SplitSmartDatabase.RECEIPTS, null, values);
                saved.add(new Receipt(id, expenseId, uploaderId, addedAt));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return saved;
    }

    @Override
    public void deleteReceipt(String receiptId) {
        database.getWritableDatabase().delete(SplitSmartDatabase.RECEIPTS, "id = ?",
                new String[]{receiptId});
    }

    @Nullable
    @Override
    public Receipt findReceiptById(String receiptId) {
        try (Cursor c = database.getReadableDatabase().query(SplitSmartDatabase.RECEIPTS,
                METADATA, "id = ?", new String[]{receiptId}, null, null, null)) {
            return c.moveToFirst() ? readReceipt(c) : null;
        }
    }

    @Override
    public List<Receipt> findReceiptsByExpense(String expenseId) {
        List<Receipt> receipts = new ArrayList<>();
        // rowid breaks ties between receipts saved in the same transaction (same added_at).
        try (Cursor c = database.getReadableDatabase().query(SplitSmartDatabase.RECEIPTS,
                METADATA, "expense_id = ?", new String[]{expenseId}, null, null,
                "added_at ASC, rowid ASC")) {
            while (c.moveToNext()) {
                receipts.add(readReceipt(c));
            }
        }
        return receipts;
    }

    @Nullable
    @Override
    public byte[] findImage(String receiptId) {
        try (Cursor c = database.getReadableDatabase().query(SplitSmartDatabase.RECEIPTS,
                new String[]{"image"}, "id = ?", new String[]{receiptId}, null, null, null)) {
            return c.moveToFirst() ? c.getBlob(0) : null;
        }
    }

    private static Receipt readReceipt(Cursor c) {
        return new Receipt(c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("expense_id")),
                c.getString(c.getColumnIndexOrThrow("uploader_id")),
                Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("added_at"))));
    }
}
