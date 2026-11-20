package com.homesmart.dao;

import com.homesmart.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests: accounts are saved in the MySQL {@code users} table (homesmart.sql) and read back.
 *
 * <p>Runs on the test database from {@code db.test.url}, never on the app's own database. Without it every
 * test is skipped (see {@link TestDatabase}). Each test starts and ends with an empty users table.
 */
class MySqlUserDAOTest {

    private TestDatabase.Config database;
    private MySqlUserDAO dao;

    @BeforeEach
    void connect() throws SQLException {
        database = TestDatabase.config();
        dao = new MySqlUserDAO(database.url(), database.user(), database.password());
        dao.checkTable();
        clearUsers();
    }

    @AfterEach
    void clearRows() throws SQLException {
        if (dao != null) {
            clearUsers();
        }
    }

    private void clearUsers() throws SQLException {
        try (Connection c = DriverManager.getConnection(database.url(), database.user(), database.password());
             Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM users");
        }
    }

    @Test
    void insertedUserIsReadBackWithItsHash() {
        User saved = dao.insert("matt", "pbkdf2-sha256$600000$c2FsdA==$aGFzaA==");

        User found = dao.findByUsername("matt").orElseThrow();
        assertEquals(saved, found);
        assertEquals("pbkdf2-sha256$600000$c2FsdA==$aGFzaA==", found.passwordHash());
    }

    @Test
    void unknownUsernameIsEmpty() {
        assertTrue(dao.findByUsername("nobody").isEmpty());
    }

    @Test
    void countFollowsTheInserts() {
        assertEquals(0, dao.count());

        dao.insert("one", "h1");
        dao.insert("two", "h2");

        assertEquals(2, dao.count());
    }

    @Test
    void duplicateUsernameFailsAndKeepsTheFirstAccount() {
        dao.insert("matt", "first");

        assertThrows(IllegalStateException.class, () -> dao.insert("matt", "second"));
        assertEquals("first", dao.findByUsername("matt").orElseThrow().passwordHash());
        assertEquals(1, dao.count());
    }

    @Test
    void idsAreDistinct() {
        assertTrue(dao.insert("one", "h").id() != dao.insert("two", "h").id());
    }
}
