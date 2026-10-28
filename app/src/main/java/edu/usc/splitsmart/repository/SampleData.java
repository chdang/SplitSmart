package edu.usc.splitsmart.repository;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.Duration;
import java.time.Instant;

/**
 * Loads a few accounts and groups into a brand-new database, so there is something to log into
 * and look at before anyone has created real data.
 *
 * <p>The user ids here match the ones {@code FakeStore} uses for the same email addresses. That
 * matters during the handover: once login is real but the group, expense, and balance handlers
 * are still fakes, a real login has to produce an id the fakes recognise, or every screen after
 * login looks empty. Keep the two in step until the last fake is gone, then delete this class.
 */
final class SampleData {

    /** The password every sample account uses. Documented in the README and on the login screen. */
    private static final String PASSWORD = "password";

    private SampleData() {
    }

    static void insertInto(SQLiteDatabase db) {
        // One hash for all five, because they genuinely share the same demo password and
        // hashing is slow. Real sign-ups each get their own salt.
        String passwordHash = PasswordHasher.hash(PASSWORD);

        addUser(db, "u-tommy", "Tommy Trojan", "tommy@usc.edu", "1234567890", passwordHash);
        addUser(db, "u-alex", "Alex Kim", "alex@usc.edu", "2345678901", passwordHash);
        addUser(db, "u-sam", "Sam Rivera", "sam@usc.edu", "3456789012", passwordHash);
        addUser(db, "u-jordan", "Jordan Lee", "jordan@usc.edu", "4567890123", passwordHash);
        addUser(db, "u-priya", "Priya Patel", "priya@usc.edu", "5678901234", passwordHash);

        addGroup(db, "g-apartment", "Apartment 4B", "Rent, utilities, and groceries", 60,
                "u-tommy", "u-alex", "u-sam", "u-jordan");
        // Sam owns this one, so Tommy can't edit it (R18).
        addGroup(db, "g-ramen", "Ramen Night", "Fridays after class", 30,
                "u-sam", "u-tommy", "u-priya");
    }

    private static void addUser(SQLiteDatabase db, String id, String name, String email,
                                String uscId, String passwordHash) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("email", email);
        values.put("usc_id", uscId);
        values.put("password_hash", passwordHash);
        db.insertOrThrow(SplitSmartDatabase.USERS, null, values);
    }

    /** The first member id is the owner. Expenses come with roles 3 to 5's repositories. */
    private static void addGroup(SQLiteDatabase db, String id, String name, String description,
                                 int daysAgo, String... memberIds) {
        long createdAt = Instant.now().minus(Duration.ofDays(daysAgo)).toEpochMilli();
        ContentValues group = new ContentValues();
        group.put("id", id);
        group.put("name", name);
        group.put("description", description);
        group.put("owner_id", memberIds[0]);
        group.put("created_at", createdAt);
        db.insertOrThrow(SplitSmartDatabase.GROUPS, null, group);

        for (String memberId : memberIds) {
            ContentValues member = new ContentValues();
            member.put("group_id", id);
            member.put("user_id", memberId);
            member.put("joined_at", createdAt);
            db.insertOrThrow(SplitSmartDatabase.GROUP_MEMBERS, null, member);
        }
    }
}
