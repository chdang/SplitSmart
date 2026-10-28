package edu.usc.splitsmart.logic;

import androidx.annotation.Nullable;

import java.util.Locale;

import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.PasswordHasher;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.UserRepository;

/**
 * Logs people in and out against real accounts (R3, R4). This replaces the stand-in current
 * user from the contract, so every other feature now works with whoever is actually logged in.
 */
public final class DefaultSessionHandler implements SessionHandler {

    private final UserRepository users;
    private final SessionStore sessions;

    public DefaultSessionHandler(UserRepository users, SessionStore sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    public void login(String email, String password, Callback<User> callback) {
        String normalizedEmail = email.trim().toLowerCase(Locale.US);

        Async.run(() -> {
            User user = users.findByEmail(normalizedEmail);
            String storedHash = user == null ? null : users.findPasswordHash(user.getId());
            // One error for both a missing account and a wrong password, so the screen can't be
            // used to find out which emails have accounts.
            if (storedHash == null || !PasswordHasher.matches(password, storedHash)) {
                throw new SplitSmartException(SplitSmartException.Code.INVALID_CREDENTIALS);
            }
            return user;
        }, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                // Back on the main thread, which is the only place the session is touched.
                sessions.setCurrentUser(user);
                callback.onSuccess(user);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void logout() {
        sessions.clear();
    }

    @Nullable
    @Override
    public User getCurrentUser() {
        return sessions.getCurrentUser();
    }

    @Override
    public User requireCurrentUser() throws SplitSmartException {
        User user = getCurrentUser();
        if (user == null) {
            throw new SplitSmartException(SplitSmartException.Code.NOT_LOGGED_IN);
        }
        return user;
    }
}
