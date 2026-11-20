package com.homesmart.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks that a missing test database skips the MySQL tests, or fails them when {@code db.test.required} is set. */
class TestDatabaseTest {

    private static final String REQUIRED = "db.test.required";

    private String required;

    @BeforeEach
    void rememberSetting() {
        required = System.getProperty(REQUIRED);
    }

    @AfterEach
    void restoreSetting() {
        if (required == null) {
            System.clearProperty(REQUIRED);
        } else {
            System.setProperty(REQUIRED, required);
        }
    }

    /** Nothing listens on port 1, so the connection check fails at once. */
    private static Properties unreachable() {
        Properties properties = new Properties();
        properties.setProperty("db.test.url", "jdbc:mysql://127.0.0.1:1/homesmart_test");
        properties.setProperty("db.user", "homesmart");
        return properties;
    }

    @Test
    void skipsWhenTheTestDatabaseIsMissingOrUnreachable() {
        System.clearProperty(REQUIRED);
        assertThrows(TestAbortedException.class, () -> TestDatabase.config(new Properties()));
        assertThrows(TestAbortedException.class, () -> TestDatabase.config(unreachable()));
    }

    @Test
    void failsInsteadOfSkippingWhenRequired() {
        System.setProperty(REQUIRED, "true");
        assertThrows(AssertionFailedError.class, () -> TestDatabase.config(new Properties()));
        assertThrows(AssertionFailedError.class, () -> TestDatabase.config(unreachable()));
    }
}
