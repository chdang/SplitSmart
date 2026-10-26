package edu.usc.splitsmart.fake;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.logic.UpdateUserNameHandler;
import edu.usc.splitsmart.model.User;

/** Stand-in for the real UpdateUserNameHandler. Replace in AppServices when role 2's version lands. */
public final class FakeUpdateUserNameHandler implements UpdateUserNameHandler {
    private final FakeStore store;

    public FakeUpdateUserNameHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void updateName(String userId, String name, Callback<User> callback) {
        User user = store.users.get(userId);
        if (user == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        if (name.trim().isEmpty()) {
            store.fail(callback, SplitSmartException.Code.INVALID_INPUT);
            return;
        }
        User updated = user.withName(name.trim());
        store.users.put(userId, updated);
        store.succeed(callback, updated);
    }
}
