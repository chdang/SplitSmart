package edu.usc.splitsmart.logic;

/** Answers whether a user belongs to a group. Every group screen checks this first. */
public interface MemberAuthenticationHandler {

    /** Returns true if the user is a member of the group. */
    void authenticateMember(String groupId, String userId, Callback<Boolean> callback);
}
