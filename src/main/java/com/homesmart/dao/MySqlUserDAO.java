package com.homesmart.dao;

import com.homesmart.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.homesmart.dao.MySqlConnector.databaseError;

/** Stores accounts in the MySQL {@code users} table (see homesmart.sql). */
public class MySqlUserDAO implements UserDAO {

    private static final int DUPLICATE_ENTRY = 1062;

    private static final String COLUMNS = "user_id, username, password_hash, created_at";

    private final MySqlConnector db;

    public MySqlUserDAO(String url, String user, String password) {
        this.db = new MySqlConnector(url, user, password);
    }

    /** Fails with {@link IllegalStateException} when the table or one of its columns is missing, or MySQL is unreachable. */
    public void checkTable() {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM users LIMIT 1");
             ResultSet ignored = statement.executeQuery()) {
            // table exists
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE username = ?", username);
    }

    @Override
    public long count() {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet row = statement.executeQuery()) {
            row.next();
            return row.getLong(1);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public User insert(String username, String passwordHash) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users (username, password_hash) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return queryOne("SELECT " + COLUMNS + " FROM users WHERE user_id = ?", keys.getInt(1)).orElseThrow();
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == DUPLICATE_ENTRY) {
                throw new IllegalStateException("Username already exists: " + username, e);
            }
            throw databaseError(e);
        }
    }

    private Optional<User> queryOne(String sql, Object parameter) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new User(row.getInt("user_id"), row.getString("username"),
                        row.getString("password_hash"), row.getObject("created_at", LocalDateTime.class)));
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }
}
