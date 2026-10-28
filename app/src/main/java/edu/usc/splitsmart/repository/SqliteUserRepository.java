package edu.usc.splitsmart.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import java.util.UUID;

import edu.usc.splitsmart.model.User;

/** The SQLite implementation of {@link UserRepository}. */
public final class SqliteUserRepository implements UserRepository {

    /** Deliberately leaves out password_hash, so it can't end up in a User by accident. */
    private static final String[] COLUMNS = {"id", "name", "email", "usc_id"};

    private final SplitSmartDatabase database;

    public SqliteUserRepository(SplitSmartDatabase database) {
        this.database = database;
    }

    @Override
    public User save(String name, String email, String uscId, String passwordHash) {
        String id = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("email", email);
        values.put("usc_id", uscId);
        values.put("password_hash", passwordHash);
        database.getWritableDatabase().insertOrThrow(SplitSmartDatabase.USERS, null, values);
        return new User(id, name, email, uscId);
    }

    @Nullable
    @Override
    public User findById(String id) {
        return findOne("id = ?", id);
    }

    @Nullable
    @Override
    public User findByEmail(String email) {
        // The column is COLLATE NOCASE, so this matches regardless of how it was typed.
        return findOne("email = ?", email);
    }

    @Nullable
    @Override
    public User findByUscId(String uscId) {
        return findOne("usc_id = ?", uscId);
    }

    @Nullable
    @Override
    public String findPasswordHash(String userId) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(SplitSmartDatabase.USERS, new String[]{"password_hash"},
                "id = ?", new String[]{userId}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    @Nullable
    @Override
    public User updateName(String userId, String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        int rows = database.getWritableDatabase().update(SplitSmartDatabase.USERS, values,
                "id = ?", new String[]{userId});
        return rows == 0 ? null : findById(userId);
    }

    @Nullable
    private User findOne(String where, String argument) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(SplitSmartDatabase.USERS, COLUMNS, where,
                new String[]{argument}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    private static User read(Cursor cursor) {
        return new User(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3));
    }
}
