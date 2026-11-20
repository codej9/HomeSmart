package com.homesmart.dao;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Connection details of the MySQL test database ({@code homesmart_test}), read from db.properties.
 *
 * <p>MySQL tests use {@code db.test.url} and never the app's own {@code db.url}. Without that line,
 * or when MySQL cannot be reached, {@link #config()} skips the test. With {@code -Ddb.test.required=true}
 * it fails instead, so a build cannot pass by skipping every MySQL test.
 */
public final class TestDatabase {

    private static final String REQUIRED = "db.test.required";

    public record Config(String url, String user, String password) {
    }

    private TestDatabase() {
    }

    public static Config config() {
        Properties properties = new Properties();
        try (InputStream in = TestDatabase.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            return unavailable("cannot read db.properties: " + e.getMessage());
        }
        return config(properties);
    }

    static Config config(Properties properties) {
        String url = properties.getProperty("db.test.url");
        if (url == null) {
            return unavailable("db.properties has no db.test.url");
        }
        Config config = new Config(url, properties.getProperty("db.user"), properties.getProperty("db.password", ""));
        try (Connection ignored = DriverManager.getConnection(config.url(), config.user(), config.password())) {
            return config;
        } catch (SQLException e) {
            return unavailable("cannot connect to " + url + ": " + e.getMessage());
        }
    }

    private static Config unavailable(String reason) {
        if (!Boolean.getBoolean(REQUIRED)) {
            assumeTrue(false, reason);
        }
        return fail(reason + " (" + REQUIRED + " is set)");
    }
}
