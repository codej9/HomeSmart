package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Light;
import com.homesmart.model.Provenance;
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
    private static final int DOOR = 3;

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
            // alert rows point at their unlock row, so they go first
            for (String filter : new String[] {"ref_log_id IS NOT NULL", "1 = 1"}) {
                try (Connection c = open(); PreparedStatement s = c.prepareStatement(
                        "DELETE FROM activity_log WHERE log_id > ? AND " + filter)) {
                    s.setInt(1, lastLogIdBefore);
                    s.executeUpdate();
                }
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

    private static final LocalDateTime T0 = LocalDateTime.of(2000, 1, 1, 10, 0);

    private long record(int deviceId, ActivityType type, Integer value, LocalDateTime at) {
        return dao.record(new CommandResult(deviceId, "ignored name", type.name(), type, value, at), Provenance.USER);
    }

    /** Entries this test added (all dated year 2000), newest first. A new DAO reads them as a restarted app would. */
    private List<ActivityEntry> added() {
        return new MySqlActivityLogDAO(url(), config.getProperty("db.user"), password()).all().stream()
                .filter(e -> e.timestamp().getYear() == 2000)
                .toList();
    }

    @Test
    void savesEntryAndReadsItBackWithDeviceName() {
        LocalDateTime when = LocalDateTime.of(2000, 1, 2, 3, 4, 5, 123_456_000);
        long id = dao.record(new CommandResult(THERMOSTAT, "ignored name", "Set temperature to 22 °C (schedule: Night)",
                ActivityType.SET_TEMPERATURE, 22, when), new Provenance(ActivitySource.SCHEDULE, "Night"));

        List<ActivityEntry> entries = added();
        assertEquals(1, entries.size());
        ActivityEntry entry = entries.get(0);
        assertEquals(id, entry.logId());
        assertEquals(THERMOSTAT, entry.deviceId());
        assertEquals(new MySqlDeviceDAO(url(), config.getProperty("db.user"), password())
                .findById(THERMOSTAT).orElseThrow().getName(), entry.deviceName());
        assertEquals("Set temperature to 22 °C (schedule: Night)", entry.action());
        assertEquals(ActivityType.SET_TEMPERATURE, entry.type());
        assertEquals(22, entry.value());
        assertEquals(ActivitySource.SCHEDULE, entry.source());
        assertEquals("Night", entry.detail());
        assertEquals(null, entry.refLogId());
        assertEquals(when, entry.timestamp());
    }

    @Test
    void newestFirstAndRecentIsLimited() {
        record(LIGHT, ActivityType.TURN_ON, null, T0);
        record(THERMOSTAT, ActivityType.SET_TEMPERATURE, 20, T0.plusHours(2));
        record(LIGHT, ActivityType.TURN_OFF, null, T0.plusHours(1));

        assertEquals(List.of(ActivityType.SET_TEMPERATURE, ActivityType.TURN_OFF, ActivityType.TURN_ON),
                added().stream().map(ActivityEntry::type).toList());
        assertEquals(2, dao.recent(2).size());
    }

    @Test
    void sameInstantKeepsInsertionOrder() {
        record(LIGHT, ActivityType.TURN_ON, null, T0);
        record(LIGHT, ActivityType.TURN_OFF, null, T0);

        assertEquals(List.of(ActivityType.TURN_OFF, ActivityType.TURN_ON),
                added().stream().map(ActivityEntry::type).toList());
    }

    @Test
    void commandRunThroughServiceIsLoggedUnderItsDevice() {
        DeviceService service = new DeviceService(
                new MySqlDeviceDAO(url(), config.getProperty("db.user"), password()), dao);
        boolean wasOn = ((Light) service.getDevice(LIGHT)).isOn();
        try {
            CommandResult result = service.execute(LIGHT, "toggle", null);

            ActivityEntry newest = dao.recent(1).get(0);
            assertEquals(LIGHT, newest.deviceId());
            assertEquals(result.deviceName(), newest.deviceName());
            assertEquals(result.action(), newest.action());
            assertEquals(wasOn ? ActivityType.TURN_OFF : ActivityType.TURN_ON, newest.type());
        } finally {
            service.execute(LIGHT, wasOn ? "on" : "off", null);
        }
    }

    @Test
    void unknownDeviceIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> dao.record(new CommandResult(99999, "Missing", "Turned on", ActivityType.TURN_ON, null), Provenance.USER));
        assertTrue(dao.all().stream().noneMatch(e -> e.deviceId() == 99999));
    }
}
