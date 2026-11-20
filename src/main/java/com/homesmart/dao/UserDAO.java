package com.homesmart.dao;

import com.homesmart.model.User;

import java.util.Optional;

/**
 * Storage contract for user accounts.
 * Reads and writes throw {@link IllegalStateException} when the storage cannot be reached.
 */
public interface UserDAO {

    /** Looks up an account by its normalized username. */
    Optional<User> findByUsername(String username);

    long count();

    /** Saves a new account. Throws {@link IllegalStateException} when the username is already taken. */
    User insert(String username, String passwordHash);
}
