package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.UUID;

import java.time.YearMonth;
import java.util.List;
import java.time.Instant;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;

/**
 * Where roles 3, 4, and 5 write their SQL. Every method is a stub until its owner fills it in,
 * so the project compiles while the three of them work in parallel.
 *
 * <p>Fill in your own block and leave the others alone. The table and column names are in
 * {@link SplitSmartDatabase}. Use {@code database.getWritableDatabase()} for writes, and wrap
 * anything that touches two tables in {@code beginTransaction} / {@code endTransaction} so a
 * half-saved expense can't happen.
 */
public final class SqliteGroupRepository implements GroupRepository {

    private final SplitSmartDatabase database;
    public SqliteGroupRepository(SplitSmartDatabase database) {
        this.database = database;
    }

    // -------------------------------------------------------------------------------------
    // Role 3: groups, membership, settlements
    // -------------------------------------------------------------------------------------

    @Override
    public Group saveGroup(String name, String description, String ownerId, Instant createdAt, List<String> memberIds) {
        // save group info
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("description", description);
        values.put("owner_id", ownerId);
        values.put("created_at", createdAt.toEpochMilli());
        database.getWritableDatabase().insertOrThrow(SplitSmartDatabase.GROUPS, null, values);

        // then add members to group
        addMembers(id, memberIds);

        return new Group(id, name, description, ownerId, createdAt);
    }

    @Override
    public Group updateGroup(String groupId, String name, String description) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("description", description);

        database.getWritableDatabase().update(
                SplitSmartDatabase.GROUPS,
                values,
                "id = ?",
                new String[]{ groupId }
        );

        return findGroupById(groupId);
    }

    @Override
    public void addMembers(String groupId, List<String> userIds) {
        SQLiteDatabase db = database.getWritableDatabase();
        Instant now = Instant.now();
        db.beginTransaction();
        try {
            for (String userId : userIds) {
                ContentValues values = new ContentValues();
                values.put("group_id", groupId);
                values.put("user_id", userId);
                values.put("joined_at", now.toEpochMilli());
                db.insertOrThrow(SplitSmartDatabase.GROUP_MEMBERS, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void deleteGroup(String groupId) {
        database.getWritableDatabase().delete(
                SplitSmartDatabase.GROUPS,
                "id = ?",
                new String[]{ groupId }
        );
    }

    @Nullable
    @Override
    public Group findGroupById(String groupId) {
        try (Cursor c = database.getReadableDatabase().query(
                SplitSmartDatabase.GROUPS, null, "id = ?",
                new String[]{ groupId },
                null, null, null)) {
            return c.moveToFirst() ? readGroup(c) : null;
        }
    }

    @Override
    public List<Group> findGroupsByUser(String userId) {
        List<Group> groups = new ArrayList<>();

        String sql = "SELECT g.* FROM " + SplitSmartDatabase.GROUPS + " g " +
                "JOIN " + SplitSmartDatabase.GROUP_MEMBERS + " gm ON gm.group_id = g.id " +
                "WHERE gm.user_id = ? " +
                "ORDER BY g.created_at DESC";

        try (Cursor c = database.getReadableDatabase().rawQuery(sql, new String[]{ userId })) {
            while (c.moveToNext()) {
                groups.add(readGroup(c));
            }
        }

        return groups;
    }

    @Override
    public List<User> findMembers(String groupId) {
        List<User> users = new ArrayList<>();

        String sql = "SELECT u.* FROM " + SplitSmartDatabase.USERS + " u " +
                "JOIN " + SplitSmartDatabase.GROUP_MEMBERS + " gm ON gm.user_id = u.id " +
                "WHERE gm.group_id = ? " +
                "ORDER BY gm.joined_at ASC";

        try (Cursor c = database.getReadableDatabase().rawQuery(sql, new String[]{ groupId })) {
            while (c.moveToNext()) {
                users.add(readUser(c));
            }
        }

        return users;
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        try (Cursor c = database.getReadableDatabase().query(
                SplitSmartDatabase.GROUP_MEMBERS,
                new String[]{ "userId" },            // any column works, you only need to know if a row exists
                "group_id = ? AND user_id = ?",
                new String[]{ groupId, userId },
                null, null, null,
                "1")) {                              // LIMIT 1
            return c.moveToFirst();
        }
    }

    @Override
    public boolean isOwner(String groupId, String userId) {
        try (Cursor c = database.getReadableDatabase().query(
                SplitSmartDatabase.GROUPS,
                new String[]{ "id" },
                "id = ? AND owner_id = ?",
                new String[]{ groupId, userId },
                null, null, null,
                "1")) {
            return c.moveToFirst();
        }
    }

    @Override
    public Settlement saveSettlement(String groupId, String recorderId, String payerId, String receiverId, long amountCents, Instant settledAt) {
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("group_id", groupId);
        values.put("recorder_id", recorderId);
        values.put("payer_id", payerId);
        values.put("receiver_id", receiverId);
        values.put("amount_cents", amountCents);
        values.put("settled_at", settledAt.toEpochMilli());
        database.getWritableDatabase().insertOrThrow(SplitSmartDatabase.SETTLEMENTS, null, values);

        return new Settlement(id, groupId, recorderId, payerId, receiverId, amountCents, settledAt);
    }

    @Override
    public List<Settlement> findSettlementsByGroup(String groupId) {
        List<Settlement> settlements = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query(
                SplitSmartDatabase.SETTLEMENTS,
                null,
                "group_id = ?",
                new String[]{ groupId },
                null, null, null)) {
            while(c.moveToNext()) {
                settlements.add(readSettlement(c));
            }
            return settlements;
        }
    }

    // helper to map cursor to group object
    private Group readGroup(Cursor c) {
        String id = c.getString(c.getColumnIndexOrThrow("id"));
        String name = c.getString(c.getColumnIndexOrThrow("name"));
        String description = c.getString(c.getColumnIndexOrThrow("description"));
        String ownerId = c.getString(c.getColumnIndexOrThrow("owner_id"));
        Instant createdAt = Instant.ofEpochMilli(
                c.getLong(c.getColumnIndexOrThrow("created_at")));

        return  new Group(id, name, description, ownerId, createdAt);
    }

    private User readUser(Cursor c) {
        return new User(c.getString(0), c.getString(1), c.getString(2),
                c.getString(3));
    }

    private Settlement readSettlement(Cursor c) {
        String id = c.getString(c.getColumnIndexOrThrow("id"));
        String groupId = c.getString(c.getColumnIndexOrThrow("group_id"));
        String recorderId = c.getString(c.getColumnIndexOrThrow("recorder_id"));
        String payerId = c.getString(c.getColumnIndexOrThrow("payer_id"));
        String receiverId = c.getString(c.getColumnIndexOrThrow("receiver_id"));
        long amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        Instant settledAt = Instant.ofEpochMilli(
                c.getLong(c.getColumnIndexOrThrow("settled_at")));

        return new Settlement(id, groupId, recorderId, payerId, receiverId, amountCents, settledAt);
    }

    // -------------------------------------------------------------------------------------
    // Role 4: expenses and shares
    // -------------------------------------------------------------------------------------

    @Override
    public void saveExpense(Expense expense) {
        throw notImplemented(4);
    }

    @Override
    public void replaceExpense(Expense expense) {
        throw notImplemented(4);
    }

    @Override
    public void deleteExpense(String expenseId) {
        throw notImplemented(4);
    }

    @Nullable
    @Override
    public Expense findExpenseById(String expenseId) {
        throw notImplemented(4);
    }

    @Override
    public List<Expense> findExpensesByGroup(String groupId) {
        throw notImplemented(4);
    }

    // -------------------------------------------------------------------------------------
    // Role 5: reporting reads
    // -------------------------------------------------------------------------------------

    @Override
    public List<Expense> findExpensesByGroupAndMonth(String groupId, YearMonth month) {
        throw notImplemented(5);
    }

    private static UnsupportedOperationException notImplemented(int role) {
        return new UnsupportedOperationException("Role " + role + " still has to write this");
    }
}
