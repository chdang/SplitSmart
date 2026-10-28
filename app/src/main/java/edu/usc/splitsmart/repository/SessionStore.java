package edu.usc.splitsmart.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import edu.usc.splitsmart.model.User;

/**
 * Remembers who is logged in, and keeps remembering after the app is closed and reopened.
 *
 * <p>Screens ask for the current user in {@code onCreate} and need an answer immediately, so
 * this keeps the whole account in memory and mirrors it to preferences rather than reading the
 * database. Nothing secret is stored: no password and no hash.
 *
 * <p>Read and written from the main thread only.
 */
public final class SessionStore {

    private static final String FILE = "splitsmart.session";
    private static final String KEY_ID = "user_id";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_USC_ID = "user_usc_id";

    private final SharedPreferences preferences;

    @Nullable
    private User currentUser;

    public SessionStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        currentUser = readSavedUser();
    }

    @Nullable
    public User getCurrentUser() {
        return currentUser;
    }

    /** Makes this the logged-in user, replacing whoever was there. */
    public void setCurrentUser(User user) {
        currentUser = user;
        preferences.edit()
                .putString(KEY_ID, user.getId())
                .putString(KEY_NAME, user.getName())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_USC_ID, user.getUscId())
                .apply();
    }

    /** Logs the current user out (R4). */
    public void clear() {
        currentUser = null;
        preferences.edit().clear().apply();
    }

    @Nullable
    private User readSavedUser() {
        String id = preferences.getString(KEY_ID, null);
        if (id == null) {
            return null;
        }
        return new User(id,
                preferences.getString(KEY_NAME, ""),
                preferences.getString(KEY_EMAIL, ""),
                preferences.getString(KEY_USC_ID, ""));
    }
}
