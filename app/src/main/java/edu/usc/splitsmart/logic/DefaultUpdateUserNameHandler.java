package edu.usc.splitsmart.logic;

import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.UserRepository;

/** Changes a user's display name and keeps the session in step with it (R5). */
public final class DefaultUpdateUserNameHandler implements UpdateUserNameHandler {

    private final UserRepository users;
    private final SessionStore sessions;

    public DefaultUpdateUserNameHandler(UserRepository users, SessionStore sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    public void updateName(String userId, String name, Callback<User> callback) {
        String trimmedName = name.trim();
        // Read here because the session is only ever touched on the main thread.
        User signedIn = sessions.getCurrentUser();

        Async.run(() -> {
            // R5 is "confirm who's logged in, then save the new name". Without this a caller
            // could rename any account by passing its id, so the check belongs here rather
            // than relying on the profile screen to pass the right one.
            if (signedIn == null || !signedIn.getId().equals(userId)) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_LOGGED_IN);
            }
            if (trimmedName.isEmpty()) {
                throw new SplitSmartException(SplitSmartException.Code.INVALID_INPUT);
            }
            User updated = users.updateName(userId, trimmedName);
            if (updated == null) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_FOUND);
            }
            return updated;
        }, new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                // Otherwise every other screen keeps showing the old name until the next login.
                User current = sessions.getCurrentUser();
                if (current != null && current.getId().equals(updated.getId())) {
                    sessions.setCurrentUser(updated);
                }
                callback.onSuccess(updated);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }
}
