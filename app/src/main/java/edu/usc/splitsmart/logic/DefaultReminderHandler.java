package edu.usc.splitsmart.logic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.model.Reminder;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;
import edu.usc.splitsmart.repository.ReminderRepository;

/**
 * Checks that a reminder is warranted, using the same netting as the balance screens, then
 * reads or writes it through the repository.
 */
public final class DefaultReminderHandler implements ReminderHandler {
    private final GroupRepository groups;
    private final ReminderRepository reminders;
    private final SessionHandler session;

    public DefaultReminderHandler(GroupRepository groups, ReminderRepository reminders,
                                  SessionHandler session) {
        this.groups = groups;
        this.reminders = reminders;
        this.session = session;
    }

    @Override
    public void sendReminder(String groupId, String recipientId, Callback<Reminder> callback) {
        withCurrentUser(senderId -> {
            if (groupId == null || groups.findGroupById(groupId) == null) {
                throw error(SplitSmartException.Code.NOT_FOUND);
            }
            if (!groups.isMember(groupId, senderId)) {
                throw error(SplitSmartException.Code.NOT_A_MEMBER);
            }
            if (recipientId == null || recipientId.equals(senderId)
                    || !groups.isMember(groupId, recipientId)) {
                throw error(SplitSmartException.Code.INVALID_INPUT);
            }
            long owed = owedToSender(groupId, senderId, recipientId);
            if (owed <= 0) {
                throw error(SplitSmartException.Code.NOTHING_OWED);
            }
            return reminders.saveReminder(groupId, senderId, recipientId, owed, Instant.now());
        }, callback);
    }

    @Override
    public void getReceivedReminders(Callback<List<Reminder>> callback) {
        withCurrentUser(reminders::findRemindersByRecipient, callback);
    }

    @Override
    public void getUnreadCount(Callback<Integer> callback) {
        withCurrentUser(reminders::countUnread, callback);
    }

    @Override
    public void setRead(String reminderId, boolean read, Callback<Void> callback) {
        withCurrentUser(currentId -> {
            Reminder reminder = reminderId == null ? null : reminders.findReminderById(reminderId);
            // Someone else's reminder looks the same as a missing one.
            if (reminder == null || !reminder.getRecipientId().equals(currentId)) {
                throw error(SplitSmartException.Code.NOT_FOUND);
            }
            reminders.setRead(reminderId, read);
            return null;
        }, callback);
    }

    /** What the recipient owes the sender right now, netted the same way as the balances. */
    private long owedToSender(String groupId, String senderId, String recipientId) {
        List<String> memberIds = new ArrayList<>();
        for (User member : groups.findMembers(groupId)) {
            memberIds.add(member.getId());
        }
        Long net = DefaultUserGroupBalanceHandler.netBalances(senderId, memberIds,
                groups.findExpensesByGroup(groupId), groups.findSettlementsByGroup(groupId))
                .get(recipientId);
        return net == null ? 0 : net;
    }

    private <T> void withCurrentUser(UserWork<T> work, Callback<T> callback) {
        // Read the session on the caller's thread, as DefaultExpenseHandler does.
        User current = session.getCurrentUser();
        Async.run(() -> {
            if (current == null) {
                throw error(SplitSmartException.Code.NOT_LOGGED_IN);
            }
            return work.run(current.getId());
        }, callback);
    }

    private interface UserWork<T> {
        T run(String currentId) throws Exception;
    }

    private static SplitSmartException error(SplitSmartException.Code code) {
        return new SplitSmartException(code);
    }
}
