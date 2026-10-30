package edu.usc.splitsmart.logic;

import java.util.List;

import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;

public class DefaultMemberAuthenticationHandler implements MemberAuthenticationHandler{

    private final GroupRepository groups;

    public DefaultMemberAuthenticationHandler(GroupRepository groups) {
        this.groups = groups;
    }

    /** Returns true if the user is a member of the group. */
    @Override
    public void authenticateMember(String groupId, String userId, Callback<Boolean> callback) {
        Async.run(() -> {
            List<User> us = groups.findMembers(groupId);

            for (User u : us) {
                if (u.getId().equals(userId)) {
                    return true;
                }
            }
            return false;
        }, callback);
    }
}
