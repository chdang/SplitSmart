package edu.usc.splitsmart.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and opens the app's SQLite database. One table per model, with foreign keys linking
 * them and unique constraints on email and USC ID, so duplicate accounts are impossible even if
 * a bug slips past the sign-up checks (R2).
 *
 * <p>Repositories are the only classes that touch this. Opening the database runs {@link
 * #onCreate}, which is slow, so every call must happen off the main thread.
 *
 * <p>Money is stored as whole cents in INTEGER columns, never as a floating point number.
 */
public final class SplitSmartDatabase extends SQLiteOpenHelper {

    private static final String NAME = "splitsmart.db";
    private static final int VERSION = 1;

    /** "groups" is a SQLite keyword, so the table is named split_groups. */
    public static final String GROUPS = "split_groups";

    public static final String USERS = "users";
    public static final String GROUP_MEMBERS = "group_members";
    public static final String EXPENSES = "expenses";
    public static final String EXPENSE_SHARES = "expense_shares";
    public static final String SETTLEMENTS = "settlements";

    public SplitSmartDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // Without this, ON DELETE CASCADE does nothing and deleting a group leaves orphan rows.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + USERS + " ("
                + "id TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "email TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                + "usc_id TEXT NOT NULL UNIQUE, "
                + "password_hash TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + GROUPS + " ("
                + "id TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "description TEXT NOT NULL DEFAULT '', "
                + "owner_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + GROUP_MEMBERS + " ("
                + "group_id TEXT NOT NULL REFERENCES " + GROUPS + "(id) ON DELETE CASCADE, "
                + "user_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "joined_at INTEGER NOT NULL, "
                + "PRIMARY KEY (group_id, user_id))");

        db.execSQL("CREATE TABLE " + EXPENSES + " ("
                + "id TEXT PRIMARY KEY, "
                + "group_id TEXT NOT NULL REFERENCES " + GROUPS + "(id) ON DELETE CASCADE, "
                + "creator_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "payer_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "description TEXT NOT NULL, "
                + "total_cents INTEGER NOT NULL, "
                + "category TEXT NOT NULL, "
                + "split_type TEXT NOT NULL, "
                // ISO-8601 yyyy-MM-dd, which sorts the same as it reads. The monthly summary
                // (R17) filters on this, not on created_at.
                + "expense_date TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + EXPENSE_SHARES + " ("
                + "expense_id TEXT NOT NULL REFERENCES " + EXPENSES + "(id) ON DELETE CASCADE, "
                + "user_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "owed_cents INTEGER NOT NULL, "
                // Keeps the participant order the form sent, which the split calculator relies
                // on when it hands out leftover pennies (R9).
                + "position INTEGER NOT NULL, "
                + "PRIMARY KEY (expense_id, user_id))");

        db.execSQL("CREATE TABLE " + SETTLEMENTS + " ("
                + "id TEXT PRIMARY KEY, "
                + "group_id TEXT NOT NULL REFERENCES " + GROUPS + "(id) ON DELETE CASCADE, "
                + "recorder_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "payer_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "receiver_id TEXT NOT NULL REFERENCES " + USERS + "(id), "
                + "amount_cents INTEGER NOT NULL, "
                + "settled_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_members_user ON " + GROUP_MEMBERS + "(user_id)");
        db.execSQL("CREATE INDEX idx_expenses_group ON " + EXPENSES + "(group_id)");
        db.execSQL("CREATE INDEX idx_shares_expense ON " + EXPENSE_SHARES + "(expense_id)");
        db.execSQL("CREATE INDEX idx_settlements_group ON " + SETTLEMENTS + "(group_id)");

        SampleData.insertInto(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // The app hasn't shipped, so there's no data worth migrating yet. Bumping VERSION wipes
        // the database and rebuilds it. Write a real migration here once we have real users.
        db.execSQL("DROP TABLE IF EXISTS " + EXPENSE_SHARES);
        db.execSQL("DROP TABLE IF EXISTS " + EXPENSES);
        db.execSQL("DROP TABLE IF EXISTS " + SETTLEMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + GROUP_MEMBERS);
        db.execSQL("DROP TABLE IF EXISTS " + GROUPS);
        db.execSQL("DROP TABLE IF EXISTS " + USERS);
        onCreate(db);
    }
}
