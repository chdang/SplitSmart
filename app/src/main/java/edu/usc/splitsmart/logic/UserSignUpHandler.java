package edu.usc.splitsmart.logic;

import edu.usc.splitsmart.model.User;

/** Creates accounts (R1, R2). */
public interface UserSignUpHandler {

    /**
     * Creates an account. Doesn't log the user in. Fails with DUPLICATE_EMAIL or
     * DUPLICATE_USC_ID if either already belongs to an account, or INVALID_INPUT if a field is
     * blank.
     */
    void register(String name, String email, String uscId, String password,
                  Callback<User> callback);
}
