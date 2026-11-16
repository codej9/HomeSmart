package com.homesmart.dao;

import com.homesmart.model.Light;
import com.homesmart.service.CommandResult;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests: commands are saved in the MySQL {@code activity_log} table (homesmart.sql)
 * and read back with the device name from {@code devices}.
 *
 * <p>Runs only when db.properties exists, MySQL is reachable and the table is imported, otherwise
 * every test is skipped. Rows that already exist are left alone; only the rows a test adds are removed.
 */
class MySqlActivityLogDAOTest {

    private static final int LIGHT = 1;
    private static final int THERMOSTAT = 2;

    private Properties config;
    private MySqlActivityLogDAO dao;
    private int lastLogIdBefore;

    @BeforeEach
    void connect() throws IOException, SQLException {
        config = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            assumeTrue(in != null, "db.properties not found");
            config.load(in);
        }
        dao = new MySqlActivityLogDAO(url(), config.getProperty("db.user"), password());
        try {
            dao.checkTable();
        } catch (IllegalStateException e) {
            assumeTrue(false, "activity_log not available: " + e.getMessage());
        }
        lastLogIdBefore = scalar("SELECT COALESCE(MAX(log_id), 0) FROM activity_log");
    }

    @AfterEach
    void removeRowsAddedByTest() throws SQLException {
        if (dao != null) {
            try (Connection c = open(); PreparedStatement s = c.prepareStatement("DELETE FROM activity_log WHERE log_id > ?")) {
                s.setInt(1, lastLogIdBefore);
                s.executeUpdate();
            }
        }
    }

    private String url() {
        return config.getProperty("db.url");
    }

    private String password() {
        return config.getProperty("db.password", "");
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url(), config.getProperty("db.user"), password());
    }

    private int scalar(String sql) throws SQLException {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            r.next();
            return r.getInt(1);
        }
    }

    /** Entries this test added (all dated year 2000), newest first. A new DAO reads them as a restarted app would. */
    private List<CommandResult> added() {
        return new MySqlActivityLogDAO(url(), config.getProperty("db.user"), password()).all().stream()
                .filter(e -> e.timestamp().getYear() == 2000)
                .toList();
    }

    @Test
    void savesEntryAndReadsItBackWithDeviceName() {
        LocalDateTime when = LocalDateTime.of(2000, 1, 2, 3, 4, 5, 123_456_000);
        dao.record(new CommandResult(THERMOSTAT, "ignored name", "Set temperature to 22 °C", when));

        List<CommandResult> entries = added();
        assertEquals(1, entries.size());
        CommandResult entry = entries.get(0);
        assertEquals(THERMOSTAT, entry.deviceId());
        assertEquals(new MySqlDeviceDAO(url(), config.getProperty("db.user"), password())
                .findById(THERMOSTAT).orElseThrow().getName(), entry.deviceName());
        assertEquals("Set temperature to 22 °C", entry.action());
        assertEquals(when, entry.timestamp());
    }

    @Test
    void newestFirstAndRecentIsLimited() {
        dao.record(new CommandResult(LIGHT, "x", "Turned on", LocalDateTime.of(2000, 1, 1, 10, 0)));
        dao.record(new CommandResult(THERMOSTAT, "x", "Set temperature to 20 °C", LocalDateTime.of(2000, 1, 1, 12, 0)));
        dao.record(new CommandResult(LIGHT, "x", "Turned off", LocalDateTime.of(2000, 1, 1, 11, 0)));

        List<CommandResult> entries = added();
        assertEquals(List.of("Set temperature to 20 °C", "Turned off", "Turned on"),
                entries.stream().map(CommandResult::action).toList());
        assertEquals(2, dao.recent(2).size());
    }

    @Test
    void sameInstantKeepsInsertionOrder() {
        LocalDateTime when = LocalDateTime.of(2000, 1, 1, 10, 0);
        dao.record(new CommandResult(LIGHT, "x", "Turned on", when));
        dao.record(new CommandResult(LIGHT, "x", "Turned off", when));

        assertEquals(List.of("Turned off", "Turned on"), added().stream().map(CommandResult::action).toList());
    }

    @Test
    void commandRunThroughServiceIsLoggedUnderItsDevice() {
        DeviceService service = new DeviceService(new MySqlDeviceDAO(url(), config.getProperty("db.user"), password()));
        boolean wasOn = ((Light) service.getDevice(LIGHT)).isOn();
        try {
            CommandResult result = service.execute(LIGHT, "toggle", null);
            dao.record(result);

            CommandResult newest = dao.recent(1).get(0);
            assertEquals(LIGHT, newest.deviceId());
            assertEquals(result.deviceName(), newest.deviceName());
            assertEquals(result.action(), newest.action());
        } finally {
            service.execute(LIGHT, wasOn ? "on" : "off", null);
        }
    }

    @Test
    void unknownDeviceIsRejected() {
        assertThrows(IllegalStateException.class, () -> dao.record(new CommandResult(99999, "Missing", "Turned on")));
        assertTrue(dao.all().stream().noneMatch(e -> e.deviceId() == 99999));
    }
}
