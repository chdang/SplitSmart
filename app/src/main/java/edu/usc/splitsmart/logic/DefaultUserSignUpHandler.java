package edu.usc.splitsmart.logic;

import java.util.Locale;

import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.PasswordHasher;
import edu.usc.splitsmart.repository.UserRepository;

/** Creates real accounts, with the password hashed before it is saved (R1, R2). */
public final class DefaultUserSignUpHandler implements UserSignUpHandler {

    /** R1: SplitSmart accounts belong to USC students. */
    private static final String REQUIRED_EMAIL_SUFFIX = "@usc.edu";

    private final UserRepository users;

    public DefaultUserSignUpHandler(UserRepository users) {
        this.users = users;
    }

    @Override
    public void register(String name, String email, String uscId, String password,
                         Callback<User> callback) {
        String trimmedName = name.trim();
        // Stored lowercase so "Tommy@usc.edu" and "tommy@usc.edu" are one account, not two.
        String normalizedEmail = email.trim().toLowerCase(Locale.US);
        String trimmedUscId = uscId.trim();

        Async.run(() -> {
            if (trimmedName.isEmpty() || trimmedUscId.isEmpty() || password.isEmpty()
                    || !normalizedEmail.endsWith(REQUIRED_EMAIL_SUFFIX)) {
                throw new SplitSmartException(SplitSmartException.Code.INVALID_INPUT);
            }
            // R2: say which field is taken, so the screen can mark the right one.
            if (users.findByEmail(normalizedEmail) != null) {
                throw new SplitSmartException(SplitSmartException.Code.DUPLICATE_EMAIL);
            }
            if (users.findByUscId(trimmedUscId) != null) {
                throw new SplitSmartException(SplitSmartException.Code.DUPLICATE_USC_ID);
            }
            return users.save(trimmedName, normalizedEmail, trimmedUscId,
                    PasswordHasher.hash(password));
        }, callback);
    }
}
