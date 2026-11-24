package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.List;

import edu.usc.splitsmart.model.Reminder;

/**
 * Reads and writes payment reminders. Like the other repositories, every method blocks on the
 * database, runs off the main thread, and checks no permissions.
 */
public interface ReminderRepository {

    /** Saves a new, unread reminder and returns it with the sender's and group's names. */
    Reminder saveReminder(String groupId, String senderId, String recipientId, long amountCents,
                          Instant sentAt);

    @Nullable
    Reminder findReminderById(String reminderId);

    /** Every reminder sent to the user, newest first. */
    List<Reminder> findRemindersByRecipient(String recipientId);

    int countUnread(String recipientId);

    void setRead(String reminderId, boolean read);
}
