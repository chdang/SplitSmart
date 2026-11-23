package edu.usc.splitsmart.model;

import java.time.Instant;

/**
 * A receipt photo attached to an expense. The image itself lives in the database next to this
 * row but isn't carried here, so listing an expense's receipts stays cheap; screens load the
 * image separately when they show it.
 */
public final class Receipt {
    private final String id;
    private final String expenseId;
    private final String uploaderId;
    private final Instant addedAt;

    public Receipt(String id, String expenseId, String uploaderId, Instant addedAt) {
        this.id = id;
        this.expenseId = expenseId;
        this.uploaderId = uploaderId;
        this.addedAt = addedAt;
    }

    public String getId() {
        return id;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
