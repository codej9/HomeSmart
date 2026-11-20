package com.homesmart.service;

import com.homesmart.dao.UserDAO;
import com.homesmart.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/** Creates the first account, which needs the setup code the server logs at startup. */
public class SetupService {

    public static final String WRONG_CODE = "Setup code is incorrect.";
    static final String BAD_USERNAME =
            "Username must be 3 to 50 characters: a-z, 0-9, dot, underscore, or hyphen.";
    static final String BAD_PASSWORD = "Password must be 15 to 64 characters.";

    private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int CODE_LENGTH = 12;
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
    private static final int MIN_PASSWORD = 15;
    private static final int MAX_PASSWORD = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserDAO userDAO;
    private final String setupCode;

    /** {@code setupCode} is null once an account exists. */
    public SetupService(UserDAO userDAO, String setupCode) {
        this.userDAO = userDAO;
        this.setupCode = setupCode;
    }

    /** 12 random characters from the Crockford Base32 alphabet (60 bits). */
    public static String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Throws {@link IllegalArgumentException} with the form error text when the code, username or password is rejected.
     * The wrong code and a second account both read as {@link #WRONG_CODE}, because the code works once.
     */
    public synchronized User createFirstAccount(String code, String username, String password) {
        if (!codeMatches(code)) {
            throw new IllegalArgumentException(WRONG_CODE);
        }
        String name = User.normalize(username);
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException(BAD_USERNAME);
        }
        if (password == null || password.codePointCount(0, password.length()) < MIN_PASSWORD
                || password.codePointCount(0, password.length()) > MAX_PASSWORD) {
            throw new IllegalArgumentException(BAD_PASSWORD);
        }
        if (userDAO.count() > 0) {
            throw new IllegalArgumentException(WRONG_CODE);
        }
        return userDAO.insert(name, PasswordHasher.hash(password));
    }

    private boolean codeMatches(String code) {
        if (setupCode == null || code == null) {
            return false;
        }
        return MessageDigest.isEqual(setupCode.getBytes(StandardCharsets.UTF_8),
                code.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
