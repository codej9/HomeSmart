package com.homesmart.dao;

import com.homesmart.model.User;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Keeps accounts in memory for sample mode. They are lost when the server restarts. */
public class InMemoryUserDAO implements UserDAO {

    private final Map<String, User> users = new HashMap<>();
    private int nextId = 1;

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public synchronized long count() {
        return users.size();
    }

    @Override
    public synchronized User insert(String username, String passwordHash) {
        if (users.containsKey(username)) {
            throw new IllegalStateException("Username already exists: " + username);
        }
        User user = new User(nextId++, username, passwordHash, LocalDateTime.now());
        users.put(username, user);
        return user;
    }
}
