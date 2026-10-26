package edu.usc.splitsmart.fake;

import java.util.Locale;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.logic.UserSignUpHandler;
import edu.usc.splitsmart.model.User;

/** Stand-in for the real UserSignUpHandler. Replace in AppServices when role 2's version lands. */
public final class FakeUserSignUpHandler implements UserSignUpHandler {
    private final FakeStore store;

    public FakeUserSignUpHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void register(String name, String email, String uscId, String password,
                         Callback<User> callback) {
        if (name.trim().isEmpty() || email.trim().isEmpty() || uscId.trim().isEmpty()
                || password.isEmpty()) {
            store.fail(callback, SplitSmartException.Code.INVALID_INPUT);
            return;
        }
        if (store.findByEmail(email) != null) {
            store.fail(callback, SplitSmartException.Code.DUPLICATE_EMAIL);
            return;
        }
        for (User existing : store.users.values()) {
            if (existing.getUscId().equals(uscId.trim())) {
                store.fail(callback, SplitSmartException.Code.DUPLICATE_USC_ID);
                return;
            }
        }
        String id = FakeStore.newId();
        User user = new User(id, name.trim(), email.trim().toLowerCase(Locale.US), uscId.trim());
        store.users.put(id, user);
        store.passwords.put(id, password);
        store.succeed(callback, user);
    }
}
