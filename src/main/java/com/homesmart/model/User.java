package com.homesmart.model;

import java.time.LocalDateTime;
import java.util.Locale;

/** A person who can sign in. {@code passwordHash} is the stored PBKDF2 string, never the password. */
public record User(int id, String username, String passwordHash, LocalDateTime createdAt) {

    /** Usernames are compared in lowercase with no surrounding spaces. A null value becomes an empty string. */
    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
