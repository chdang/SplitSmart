package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.List;

import edu.usc.splitsmart.model.Receipt;

/**
 * Reads and writes receipt photos attached to expenses. Like the other repositories, every
 * method blocks on the database, is called off the main thread, and does no permission
 * checking; {@code ReceiptHandler} decides who may do what.
 */
public interface ReceiptRepository {

    /** Saves every image against the expense in one transaction, so it's all or nothing. */
    List<Receipt> saveReceipts(String expenseId, String uploaderId, List<byte[]> images,
                               Instant addedAt);

    void deleteReceipt(String receiptId);

    @Nullable
    Receipt findReceiptById(String receiptId);

    /** The expense's receipts, oldest first, without their image bytes. */
    List<Receipt> findReceiptsByExpense(String expenseId);

    /** The stored JPEG bytes, or null if the receipt doesn't exist. */
    @Nullable
    byte[] findImage(String receiptId);
}
