package edu.usc.splitsmart.logic;

import java.util.List;

import edu.usc.splitsmart.model.Receipt;

/**
 * Attaches receipt photos to expenses, lists them, and removes them. Any member of the
 * expense's group can view its receipts; only the expense's creator can add or delete them,
 * the same rule that governs editing the expense itself (R7).
 */
public interface ReceiptHandler {

    /**
     * The largest image accepted, in bytes. Screens shrink photos below this before sending
     * them; the limit keeps a single row comfortably inside SQLite's cursor window.
     */
    int MAX_IMAGE_BYTES = 1_000_000;

    /**
     * Attaches each image (JPEG bytes) to the expense, all or nothing. Fails with
     * NOT_LOGGED_IN, NOT_FOUND, NOT_CREATOR if {@code actorId} isn't the logged-in creator, or
     * INVALID_RECEIPT if the list is empty or any image is empty or over {@link #MAX_IMAGE_BYTES}.
     */
    void addReceipts(String expenseId, String actorId, List<byte[]> images,
                     Callback<List<Receipt>> callback);

    /** The expense's receipts, oldest first. Requires a logged-in member of its group. */
    void getReceipts(String expenseId, Callback<List<Receipt>> callback);

    /** The receipt's JPEG bytes. Requires a logged-in member of the expense's group. */
    void getReceiptImage(String receiptId, Callback<byte[]> callback);

    /** Removes the receipt. Requires the expense's logged-in creator. */
    void deleteReceipt(String receiptId, String actorId, Callback<Void> callback);
}
