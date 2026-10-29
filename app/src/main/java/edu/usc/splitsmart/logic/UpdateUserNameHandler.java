package edu.usc.splitsmart.logic;

import edu.usc.splitsmart.model.User;

/** Changes a user's display name (R5). */
public interface UpdateUserNameHandler {

    /**
     * Saves the new name and returns the updated user. Fails with INVALID_INPUT if the name is
     * blank, and with NOT_LOGGED_IN unless {@code userId} is the signed-in user: you can only
     * rename your own account (R5).
     */
    void updateName(String userId, String name, Callback<User> callback);
}
