package edu.usc.splitsmart.model;

import java.time.Instant;

/** A payment one member made to another outside the app, recorded so balances stay right (R16). */
public final class Settlement {
    private final String id;
    private final String groupId;
    private final String recorderId;
    private final String payerId;
    private final String receiverId;
    private final long amountCents;
    private final String note;
    private final Instant settledAt;

    public Settlement(String id, String groupId, String recorderId, String payerId,
                      String receiverId, long amountCents, String note, Instant settledAt) {
        this.id = id;
        this.groupId = groupId;
        this.recorderId = recorderId;
        this.payerId = payerId;
        this.receiverId = receiverId;
        this.amountCents = amountCents;
        this.note = note == null ? "" : note;
        this.settledAt = settledAt;
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getRecorderId() {
        return recorderId;
    }

    public String getPayerId() {
        return payerId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    /** The recorder's optional free-text note, or "" when they didn't write one. */
    public String getNote() {
        return note;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
