package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.model.DeviceType;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Stores device state in the MySQL {@code devices} table (see homesmart.sql).
 *
 * <p>Each row uses only the column for its type: {@code power_on} for lights,
 * {@code temperature} for thermostats and {@code locked} for door locks.
 */
public class MySqlDeviceDAO implements DeviceDAO {

    private static final Logger LOG = Logger.getLogger(MySqlDeviceDAO.class.getName());

    private static final String SELECT_DEVICES =
            "SELECT device_id, device_name, device_type, power_on, temperature, locked, last_updated FROM devices";

    private static final String UPDATE_DEVICE =
            "UPDATE devices SET power_on = ?, temperature = ?, locked = ?, last_updated = ? WHERE device_id = ?";

    private final String url;
    private final String user;
    private final String password;

    public MySqlDeviceDAO(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        try {
            // Tomcat does not always auto-register JDBC drivers packaged in WEB-INF/lib
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver is missing from the build", e);
        }
    }

    /** Opens and closes one connection so start-up can tell whether the database is reachable. */
    public void checkConnection() {
        try (Connection ignored = connect()) {
            // connected
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public List<Device> findAll() {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_DEVICES + " ORDER BY device_id");
             ResultSet rows = statement.executeQuery()) {
            List<Device> devices = new ArrayList<>();
            while (rows.next()) {
                devices.add(toDevice(rows));
            }
            return devices;
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public Optional<Device> findById(int id) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_DEVICES + " WHERE device_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(toDevice(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void update(Device device) {
        Boolean powerOn = null;
        Integer temperature = null;
        Boolean locked = null;
        if (device instanceof Light light) {
            powerOn = light.isOn();
        } else if (device instanceof Thermostat thermostat) {
            temperature = thermostat.getTemperature();
        } else if (device instanceof DoorLock doorLock) {
            locked = doorLock.isLocked();
        }

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(UPDATE_DEVICE)) {
            statement.setObject(1, powerOn, Types.BOOLEAN);
            statement.setObject(2, temperature, Types.INTEGER);
            statement.setObject(3, locked, Types.BOOLEAN);
            Instant lastUpdated = Instant.now().truncatedTo(ChronoUnit.MICROS);
            statement.setObject(4, LocalDateTime.ofInstant(lastUpdated, ZoneOffset.UTC));
            statement.setInt(5, device.getId());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("No device with id " + device.getId());
            }
            device.setLastUpdated(lastUpdated);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static Device toDevice(ResultSet row) throws SQLException {
        int id = row.getInt("device_id");
        String name = row.getString("device_name");
        DeviceType type = DeviceType.valueOf(row.getString("device_type"));
        Device device = switch (type) {
            case LIGHT -> new Light(id, name, row.getBoolean("power_on"));
            case THERMOSTAT -> new Thermostat(id, name, storedTemperature(row, name));
            case DOOR_LOCK -> new DoorLock(id, name, row.getBoolean("locked"));
        };
        LocalDateTime lastUpdated = row.getObject("last_updated", LocalDateTime.class);
        device.setLastUpdated(lastUpdated == null ? null : lastUpdated.toInstant(ZoneOffset.UTC));
        return device;
    }

    /**
     * Reads a thermostat's saved temperature as a whole degree inside the allowed range.
     *
     * <p>The column is DECIMAL(4,1) and the database does not enforce the 10–32 °C range, so a row
     * edited by hand can hold any value or none. Before Release 2 such a row made every dashboard
     * page fail with HTTP 500. Now an empty value falls back to the Eco temperature and an
     * out-of-range value is moved to the nearest limit; both are logged so the row can be fixed.
     * The next temperature change saves a valid value again.
     */
    private static int storedTemperature(ResultSet row, String name) throws SQLException {
        BigDecimal stored = row.getBigDecimal("temperature");
        if (stored == null) {
            LOG.warning(name + " has no saved temperature; using " + Thermostat.ECO_TEMPERATURE + " °C");
            return Thermostat.ECO_TEMPERATURE;
        }
        // The app works in whole degrees, so round 21.5 to 22
        int whole = stored.setScale(0, RoundingMode.HALF_UP).intValue();
        int safe = Math.max(Thermostat.MIN_TEMPERATURE, Math.min(Thermostat.MAX_TEMPERATURE, whole));
        if (safe != whole) {
            LOG.warning(name + " has an out-of-range saved temperature (" + stored.toPlainString()
                    + " °C); using " + safe + " °C");
        }
        return safe;
    }

    private IllegalStateException databaseError(SQLException e) {
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
