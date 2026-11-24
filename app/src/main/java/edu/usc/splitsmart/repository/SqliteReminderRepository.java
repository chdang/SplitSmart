package edu.usc.splitsmart.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.usc.splitsmart.model.Reminder;

/** Stores reminders in the reminders table of {@link SplitSmartDatabase}. */
public final class SqliteReminderRepository implements ReminderRepository {

    /** Joins in the sender's and group's names, which the inbox shows on every row. */
    private static final String SELECT = "SELECT r.*, u.name AS sender_name, g.name AS group_name"
            + " FROM " + SplitSmartDatabase.REMINDERS + " r"
            + " JOIN " + SplitSmartDatabase.USERS + " u ON u.id = r.sender_id"
            + " JOIN " + SplitSmartDatabase.GROUPS + " g ON g.id = r.group_id ";

    private final SplitSmartDatabase database;

    public SqliteReminderRepository(SplitSmartDatabase database) {
        this.database = database;
    }

    @Override
    public Reminder saveReminder(String groupId, String senderId, String recipientId,
                                 long amountCents, Instant sentAt) {
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("group_id", groupId);
        values.put("sender_id", senderId);
        values.put("recipient_id", recipientId);
        values.put("amount_cents", amountCents);
        values.put("sent_at", sentAt.toEpochMilli());
        values.put("is_read", 0);
        database.getWritableDatabase().insertOrThrow(SplitSmartDatabase.REMINDERS, null, values);
        return findReminderById(id);
    }

    @Nullable
    @Override
    public Reminder findReminderById(String reminderId) {
        try (Cursor c = database.getReadableDatabase().rawQuery(SELECT + "WHERE r.id = ?",
                new String[]{reminderId})) {
            return c.moveToFirst() ? readReminder(c) : null;
        }
    }

    @Override
    public List<Reminder> findRemindersByRecipient(String recipientId) {
        List<Reminder> reminders = new ArrayList<>();
        // rowid breaks ties between reminders sent in the same millisecond.
        try (Cursor c = database.getReadableDatabase().rawQuery(SELECT
                        + "WHERE r.recipient_id = ? ORDER BY r.sent_at DESC, r.rowid DESC",
                new String[]{recipientId})) {
            while (c.moveToNext()) {
                reminders.add(readReminder(c));
            }
        }
        return reminders;
    }

    @Override
    public int countUnread(String recipientId) {
        return (int) DatabaseUtils.queryNumEntries(database.getReadableDatabase(),
                SplitSmartDatabase.REMINDERS, "recipient_id = ? AND is_read = 0",
                new String[]{recipientId});
    }

    @Override
    public void setRead(String reminderId, boolean read) {
        ContentValues values = new ContentValues();
        values.put("is_read", read ? 1 : 0);
        database.getWritableDatabase().update(SplitSmartDatabase.REMINDERS, values, "id = ?",
                new String[]{reminderId});
    }

    private static Reminder readReminder(Cursor c) {
        return new Reminder(c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("group_id")),
                c.getString(c.getColumnIndexOrThrow("group_name")),
                c.getString(c.getColumnIndexOrThrow("sender_id")),
                c.getString(c.getColumnIndexOrThrow("sender_name")),
                c.getString(c.getColumnIndexOrThrow("recipient_id")),
                c.getLong(c.getColumnIndexOrThrow("amount_cents")),
                Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("sent_at"))),
                c.getInt(c.getColumnIndexOrThrow("is_read")) != 0);
    }
}
