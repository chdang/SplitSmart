package edu.usc.splitsmart.model;

import java.time.Instant;
import java.util.Objects;

/** A group of users who share expenses. Members are stored separately as GroupMember rows. */
public final class Group {
    private final String id;
    private final String name;
    private final String description;
    private final String ownerId;
    private final Instant createdAt;

    public Group(String id, String name, String description, String ownerId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** May be empty, never null. */
    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Group && Objects.equals(id, ((Group) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
