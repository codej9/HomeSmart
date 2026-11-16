package com.homesmart.dao;

import com.homesmart.service.CommandResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.homesmart.dao.MySqlConnector.databaseError;

/**
 * Stores the activity log in the MySQL {@code activity_log} table (see homesmart.sql).
 * The device name is read from {@code devices}, so a renamed device shows its new name in old entries.
 */
public class MySqlActivityLogDAO implements ActivityLogDAO {

    private static final String INSERT =
            "INSERT INTO activity_log (device_id, action, logged_at) VALUES (?, ?, ?)";

    // log_id breaks ties between entries logged in the same instant
    private static final String SELECT_NEWEST_FIRST =
            "SELECT a.device_id, d.device_name, a.action, a.logged_at "
                    + "FROM activity_log a JOIN devices d ON d.device_id = a.device_id "
                    + "ORDER BY a.logged_at DESC, a.log_id DESC";

    private final MySqlConnector db;

    public MySqlActivityLogDAO(String url, String user, String password) {
        this.db = new MySqlConnector(url, user, password);
    }

    /** Fails with {@link IllegalStateException} when the table is missing or MySQL is unreachable. */
    public void checkTable() {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM activity_log LIMIT 1");
             ResultSet ignored = statement.executeQuery()) {
            // table exists
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void record(CommandResult result) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setInt(1, result.deviceId());
            statement.setString(2, result.action());
            statement.setObject(3, result.timestamp());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public List<CommandResult> recent(int limit) {
        return query(SELECT_NEWEST_FIRST + " LIMIT ?", limit);
    }

    @Override
    public List<CommandResult> all() {
        return query(SELECT_NEWEST_FIRST, null);
    }

    private List<CommandResult> query(String sql, Integer limit) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (limit != null) {
                statement.setInt(1, limit);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<CommandResult> entries = new ArrayList<>();
                while (rows.next()) {
                    entries.add(new CommandResult(rows.getInt("device_id"), rows.getString("device_name"),
                            rows.getString("action"), rows.getObject("logged_at", LocalDateTime.class)));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }
}
