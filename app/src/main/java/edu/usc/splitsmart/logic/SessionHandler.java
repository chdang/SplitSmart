package edu.usc.splitsmart.logic;

import androidx.annotation.Nullable;

import edu.usc.splitsmart.model.User;

/** Tracks who is logged in (R1-R4). */
public interface SessionHandler {

    /**
     * Checks the email and password and, if they match, makes that user the current user.
     * Fails with INVALID_CREDENTIALS if they don't match.
     */
    void login(String email, String password, Callback<User> callback);

    /** Clears the current user (R4). */
    void logout();

    /** The logged-in user, or null if nobody is logged in. */
    @Nullable
    User getCurrentUser();

    /** The logged-in user. Throws NOT_LOGGED_IN if nobody is logged in. */
    User requireCurrentUser() throws SplitSmartException;
}
