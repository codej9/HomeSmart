package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.homesmart.dao.MySqlConnector.databaseError;

/**
 * Stores the activity log in the MySQL {@code activity_log} table (see homesmart.sql).
 * The device name is read from {@code devices}, so a renamed device shows its new name in old entries.
 */
public class MySqlActivityLogDAO implements ActivityLogDAO {

    private static final int DUPLICATE_KEY = 1062;

    private static final String INSERT =
            "INSERT INTO activity_log (device_id, action, activity_type, `value`, source, detail, ref_log_id, logged_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT =
            "SELECT a.log_id, a.device_id, d.device_name, a.action, a.activity_type, a.`value`, a.source, "
                    + "a.detail, a.ref_log_id, a.logged_at "
                    + "FROM activity_log a JOIN devices d ON d.device_id = a.device_id ";

    // log_id breaks ties between entries logged in the same instant
    private static final String NEWEST_FIRST = " ORDER BY a.logged_at DESC, a.log_id DESC";

    private final MySqlConnector db;

    public MySqlActivityLogDAO(String url, String user, String password) {
        this.db = new MySqlConnector(url, user, password);
    }

    /** Fails with {@link IllegalStateException} when the table is missing a column or MySQL is unreachable. */
    public void checkTable() {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT log_id, device_id, action, activity_type, `value`, source, detail, ref_log_id, logged_at "
                             + "FROM activity_log LIMIT 1");
             ResultSet ignored = statement.executeQuery()) {
            // table is current
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public long record(CommandResult result, Provenance provenance) {
        return insert(result.deviceId(), result.action(), result.type(), result.value(),
                provenance.source(), provenance.detail(), null, result.timestamp());
    }

    @Override
    public boolean recordDoorAlert(int deviceId, long unlockLogId, int minutes, LocalDateTime at) {
        try {
            insert(deviceId, "Security alert: left unlocked for " + minutes + " min",
                    ActivityType.DOOR_LEFT_UNLOCKED, null, ActivitySource.SYSTEM, null, unlockLogId, at);
            return true;
        } catch (IllegalStateException e) {
            // uq_activity_log_ref is the only unique key an insert can break
            if (e.getCause() instanceof SQLException cause && cause.getErrorCode() == DUPLICATE_KEY) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<ActivityEntry> recent(int limit) {
        return query(SELECT + NEWEST_FIRST + " LIMIT ?", limit);
    }

    @Override
    public List<ActivityEntry> all() {
        return query(SELECT + NEWEST_FIRST);
    }

    @Override
    public Optional<ActivityEntry> latestOfTypes(int deviceId, Set<ActivityType> types) {
        return query(SELECT + "WHERE a.device_id = ? AND a.activity_type IN (" + marks(types) + ")"
                + NEWEST_FIRST + " LIMIT 1", args(deviceId, types)).stream().findFirst();
    }

    private long insert(int deviceId, String action, ActivityType type, Integer value, ActivitySource source,
                        String detail, Long refLogId, LocalDateTime at) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, deviceId);
            statement.setString(2, action);
            statement.setString(3, type.name());
            statement.setObject(4, value, Types.INTEGER);
            statement.setString(5, source.name());
            statement.setString(6, detail);
            statement.setObject(7, refLogId, Types.INTEGER);
            statement.setObject(8, at);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private List<ActivityEntry> query(String sql, Object... params) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i] instanceof ActivityType type ? type.name() : params[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<ActivityEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    entries.add(toEntry(rows));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private static ActivityEntry toEntry(ResultSet row) throws SQLException {
        Integer value = row.getObject("value", Integer.class);
        Long refLogId = row.getObject("ref_log_id", Long.class);
        return new ActivityEntry(row.getLong("log_id"), row.getInt("device_id"), row.getString("device_name"),
                row.getString("action"), ActivityType.valueOf(row.getString("activity_type")), value,
                ActivitySource.valueOf(row.getString("source")), row.getString("detail"), refLogId,
                row.getObject("logged_at", LocalDateTime.class));
    }

    private static String marks(Set<ActivityType> types) {
        return String.join(", ", Collections.nCopies(types.size(), "?"));
    }

    private static Object[] args(int deviceId, Set<ActivityType> types, Object... after) {
        List<Object> all = new ArrayList<>();
        all.add(deviceId);
        all.addAll(types);
        all.addAll(List.of(after));
        return all.toArray();
    }
}
