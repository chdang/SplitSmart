package edu.usc.splitsmart.fake;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.MemberAuthenticationHandler;

/** Stand-in for the real membership check. Replace in AppServices when role 3's version lands. */
public final class FakeMemberAuthenticationHandler implements MemberAuthenticationHandler {
    private final FakeStore store;

    public FakeMemberAuthenticationHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void authenticateMember(String groupId, String userId, Callback<Boolean> callback) {
        store.succeed(callback, store.isMember(groupId, userId));
    }
}
