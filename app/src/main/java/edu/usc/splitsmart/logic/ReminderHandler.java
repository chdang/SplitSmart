package edu.usc.splitsmart.logic;

import java.util.List;

import edu.usc.splitsmart.model.Reminder;

/**
 * Payment reminders inside the app. A member can remind anyone in the group who currently owes
 * them money; the recipient sees it in their inbox and can mark it read or unread. Every call
 * acts as the logged-in user and fails with NOT_LOGGED_IN if nobody is.
 */
public interface ReminderHandler {

    /**
     * Reminds {@code recipientId} that they owe the logged-in user, for what they owe right now
     * after netting expenses and settlements. Fails with NOT_FOUND if the group doesn't exist,
     * NOT_A_MEMBER if the sender isn't in it, INVALID_INPUT if the recipient is the sender or
     * isn't in the group, and NOTHING_OWED if the recipient doesn't owe the sender anything.
     */
    void sendReminder(String groupId, String recipientId, Callback<Reminder> callback);

    /** Every reminder sent to the logged-in user, newest first. */
    void getReceivedReminders(Callback<List<Reminder>> callback);

    /** How many of the logged-in user's reminders are unread. */
    void getUnreadCount(Callback<Integer> callback);

    /**
     * Marks one of the logged-in user's reminders read or unread. Fails with NOT_FOUND if the
     * reminder doesn't exist or was sent to someone else.
     */
    void setRead(String reminderId, boolean read, Callback<Void> callback);
}
