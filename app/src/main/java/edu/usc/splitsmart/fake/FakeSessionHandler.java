package edu.usc.splitsmart.fake;

import androidx.annotation.Nullable;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.SessionHandler;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.model.User;

/** Stand-in for the real SessionHandler. Replace in AppServices when role 2's version lands. */
public final class FakeSessionHandler implements SessionHandler {
    private final FakeStore store;

    public FakeSessionHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void login(String email, String password, Callback<User> callback) {
        User user = store.findByEmail(email);
        if (user == null || !store.passwords.get(user.getId()).equals(password)) {
            store.fail(callback, SplitSmartException.Code.INVALID_CREDENTIALS);
            return;
        }
        store.currentUserId = user.getId();
        store.succeed(callback, user);
    }

    @Override
    public void logout() {
        store.currentUserId = null;
    }

    @Nullable
    @Override
    public User getCurrentUser() {
        return store.currentUserId == null ? null : store.users.get(store.currentUserId);
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
