package com.homesmart.dao;

import com.homesmart.model.ActivityType;
import com.homesmart.model.Schedule;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.homesmart.dao.MySqlConnector.databaseError;

/** Stores schedules in the MySQL {@code schedules} table (see homesmart.sql). */
public class MySqlScheduleDAO implements ScheduleDAO {

    private static final String COLUMNS =
            "schedule_id, name, device_id, activity_type, `value`, run_at, days, enabled, last_run_on, last_result";

    private final MySqlConnector db;

    public MySqlScheduleDAO(String url, String user, String password) {
        this.db = new MySqlConnector(url, user, password);
    }

    /** Fails with {@link IllegalStateException} when the table is missing a column or MySQL is unreachable. */
    public void checkTable() {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM schedules LIMIT 1");
             ResultSet ignored = statement.executeQuery()) {
            // table is current
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public List<Schedule> all() {
        return query("SELECT " + COLUMNS + " FROM schedules ORDER BY run_at, schedule_id");
    }

    @Override
    public Optional<Schedule> find(int scheduleId) {
        return query("SELECT " + COLUMNS + " FROM schedules WHERE schedule_id = ?", scheduleId).stream().findFirst();
    }

    @Override
    public Schedule create(Schedule schedule) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO schedules (name, device_id, activity_type, `value`, run_at, days, enabled, last_run_on, last_result) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, schedule.name());
            statement.setInt(2, schedule.deviceId());
            statement.setString(3, schedule.type().name());
            statement.setObject(4, schedule.value(), Types.INTEGER);
            statement.setObject(5, schedule.runAt());
            statement.setString(6, Schedule.formatDays(schedule.days()));
            statement.setBoolean(7, schedule.enabled());
            statement.setObject(8, schedule.lastRunOn() == null ? null : Date.valueOf(schedule.lastRunOn()), Types.DATE);
            statement.setString(9, schedule.lastResult());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Schedule(keys.getInt(1), schedule.name(), schedule.deviceId(), schedule.type(),
                        schedule.value(), schedule.runAt(), schedule.days(), schedule.enabled(),
                        schedule.lastRunOn(), schedule.lastResult());
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public boolean setEnabled(int scheduleId, boolean enabled, LocalDate lastRunOn) {
        return update("UPDATE schedules SET enabled = ?, last_run_on = ? WHERE schedule_id = ?",
                enabled, lastRunOn == null ? null : Date.valueOf(lastRunOn), scheduleId);
    }

    @Override
    public boolean delete(int scheduleId) {
        return update("DELETE FROM schedules WHERE schedule_id = ?", scheduleId);
    }

    @Override
    public boolean claim(int scheduleId, LocalDate occurrence) {
        Date day = Date.valueOf(occurrence);
        return update("UPDATE schedules SET last_run_on = ? WHERE schedule_id = ? AND enabled = 1 "
                + "AND (last_run_on IS NULL OR last_run_on < ?)", day, scheduleId, day);
    }

    @Override
    public void recordResult(int scheduleId, String result) {
        update("UPDATE schedules SET last_result = ? WHERE schedule_id = ?", result, scheduleId);
    }

    /** Returns true when a row matched. Connector/J counts matched rows, not changed rows. */
    private boolean update(String sql, Object... params) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private List<Schedule> query(String sql, Object... params) {
        try (Connection connection = db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rows = statement.executeQuery()) {
                List<Schedule> schedules = new ArrayList<>();
                while (rows.next()) {
                    schedules.add(toSchedule(rows));
                }
                return schedules;
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static Schedule toSchedule(ResultSet row) throws SQLException {
        Date lastRunOn = row.getDate("last_run_on");
        return new Schedule(row.getInt("schedule_id"), row.getString("name"), row.getInt("device_id"),
                ActivityType.valueOf(row.getString("activity_type")), row.getObject("value", Integer.class),
                row.getObject("run_at", LocalTime.class), Schedule.parseDays(row.getString("days")),
                row.getBoolean("enabled"), lastRunOn == null ? null : lastRunOn.toLocalDate(),
                row.getString("last_result"));
    }
}
