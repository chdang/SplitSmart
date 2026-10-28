package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import edu.usc.splitsmart.model.User;

/**
 * Reads and writes accounts. Every method blocks on the database, so handlers call them off the
 * main thread.
 *
 * <p>Password hashes never leave this layer as part of a {@link User}. The only way to see one
 * is {@link #findPasswordHash(String)}, which login uses to check a password.
 */
public interface UserRepository {

    /**
     * Saves a new account with a generated id and returns it. Callers check for duplicates
     * first so they can report which field clashed; the unique constraints on email and USC ID
     * are the backstop, and a clash here throws.
     */
    User save(String name, String email, String uscId, String passwordHash);

    @Nullable
    User findById(String id);

    /** Looks up by email, ignoring case. */
    @Nullable
    User findByEmail(String email);

    @Nullable
    User findByUscId(String uscId);

    /** The stored hash for an account, or null if there's no such account. */
    @Nullable
    String findPasswordHash(String userId);

    /** Saves a new display name and returns the updated account, or null if it doesn't exist. */
    @Nullable
    User updateName(String userId, String name);
}
