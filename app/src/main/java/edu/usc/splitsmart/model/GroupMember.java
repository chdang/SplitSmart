package edu.usc.splitsmart.model;

import java.time.Instant;

/** Links a user to a group they belong to. */
public final class GroupMember {
    private final String groupId;
    private final String userId;
    private final Instant joinedAt;

    public GroupMember(String groupId, String userId, Instant joinedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
