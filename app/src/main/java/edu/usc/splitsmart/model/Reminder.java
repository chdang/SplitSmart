package edu.usc.splitsmart.model;

import java.time.Instant;

/**
 * A nudge from one member to another who owes them money in a group. The amount is what the
 * recipient owed when the reminder was sent; later expenses or settlements don't change it.
 * The sender's and group's names are carried along so the inbox can show them in one read.
 */
public final class Reminder {
    private final String id;
    private final String groupId;
    private final String groupName;
    private final String senderId;
    private final String senderName;
    private final String recipientId;
    private final long amountCents;
    private final Instant sentAt;
    private final boolean read;

    public Reminder(String id, String groupId, String groupName, String senderId,
                    String senderName, String recipientId, long amountCents, Instant sentAt,
                    boolean read) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.amountCents = amountCents;
        this.sentAt = sentAt;
        this.read = read;
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public boolean isRead() {
        return read;
    }
}
