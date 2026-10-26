package edu.usc.splitsmart.model;

import java.util.Objects;

/**
 * A SplitSmart account. The password hash stays inside UserRepository and is never passed to
 * screens.
 */
public final class User {
    private final String id;
    private final String name;
    private final String email;
    private final String uscId;

    public User(String id, String name, String email, String uscId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.uscId = uscId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getUscId() {
        return uscId;
    }

    /** Returns a copy of this user with a different name. */
    public User withName(String newName) {
        return new User(id, newName, email, uscId);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User && Objects.equals(id, ((User) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
