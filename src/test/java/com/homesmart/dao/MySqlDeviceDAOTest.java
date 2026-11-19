package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests: device commands are saved in the MySQL {@code devices} table (homesmart.sql)
 * and read back from it.
 *
 * <p>Requires explicit configuration for a disposable MySQL database with the current schema.
 * Snapshots the original values and restores them directly after each test.
 */
class MySqlDeviceDAOTest {

    private MySqlDeviceDAO dao;
    private DeviceService service;
    private String url;
    private String user;
    private String password;
    private Connection snapshotConnection;
    private boolean snapshotReady;

    @BeforeEach
    void connect() throws SQLException {
        url = System.getenv("HOMESMART_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Disposable MySQL test database not configured");
        user = System.getenv("HOMESMART_TEST_DB_USER");
        password = System.getenv("HOMESMART_TEST_DB_PASSWORD");
        dao = new MySqlDeviceDAO(url, user, password);
        snapshotConnection = DriverManager.getConnection(url, user, password);
        try (Statement statement = snapshotConnection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TEMPORARY TABLE original_device_state AS
                    SELECT device_id, power_on, temperature, locked, last_updated FROM devices
                    """);
            snapshotReady = true;
        }
        service = new DeviceService(dao);
    }

    @AfterEach
    void restoreOriginalRows() throws SQLException {
        if (snapshotConnection == null) {
            return;
        }
        try (Connection connection = snapshotConnection; Statement statement = connection.createStatement()) {
            if (snapshotReady) {
                statement.executeUpdate("""
                        UPDATE devices AS device
                        JOIN original_device_state AS original USING (device_id)
                        SET device.power_on = original.power_on,
                            device.temperature = original.temperature,
                            device.locked = original.locked,
                            device.last_updated = original.last_updated
                        """);
            }
        }
    }

    private <T extends Device> T reload(T device, Class<T> type) {
        MySqlDeviceDAO freshDao = new MySqlDeviceDAO(url, user, password);
        return type.cast(freshDao.findById(device.getId()).orElseThrow());
    }

    private <T extends Device> T first(Class<T> type) {
        return dao.findAll().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in the devices table"));
    }

    @Test
    void loadsOneOfEachDeviceType() {
        List<Device> devices = dao.findAll();
        assertTrue(devices.stream().anyMatch(Light.class::isInstance));
        assertTrue(devices.stream().anyMatch(Thermostat.class::isInstance));
        assertTrue(devices.stream().anyMatch(DoorLock.class::isInstance));
    }

    @Test
    void savesLightState() {
        Light light = first(Light.class);
        Instant beforeCommand = Instant.now().truncatedTo(ChronoUnit.MICROS);
        service.execute(light.getId(), "toggle", null);
        Light reloaded = reload(light, Light.class);
        assertEquals(!light.isOn(), reloaded.isOn());
        assertNotNull(reloaded.getLastUpdated());
        assertTrue(!reloaded.getLastUpdated().isBefore(beforeCommand));
        assertEquals(reloaded.getLastUpdated(), reload(light, Light.class).getLastUpdated());
    }

    @Test
    void savesThermostatTemperature() {
        Thermostat thermostat = first(Thermostat.class);
        int target = thermostat.getTemperature() == 20 ? 22 : 20;
        service.execute(thermostat.getId(), "set", String.valueOf(target));
        Thermostat reloaded = reload(thermostat, Thermostat.class);
        assertEquals(target, reloaded.getTemperature());
        assertNotNull(reloaded.getLastUpdated());

        service.execute(thermostat.getId(), "increase", null);
        assertEquals(target + 1, reload(thermostat, Thermostat.class).getTemperature());
    }

    @Test
    void savesDoorLockState() {
        DoorLock doorLock = first(DoorLock.class);
        service.execute(doorLock.getId(), doorLock.isLocked() ? "unlock" : "lock", null);
        DoorLock reloaded = reload(doorLock, DoorLock.class);
        assertEquals(!doorLock.isLocked(), reloaded.isLocked());
        assertNotNull(reloaded.getLastUpdated());
    }

    @Test
    void rejectedCommandLeavesRowUnchanged() {
        Thermostat original = first(Thermostat.class);
        service.execute(original.getId(), "set", "22");
        Thermostat thermostat = reload(original, Thermostat.class);
        assertNotNull(thermostat.getLastUpdated());
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(thermostat.getId(), "set", String.valueOf(Thermostat.MAX_TEMPERATURE + 1)));
        Thermostat reloaded = reload(thermostat, Thermostat.class);
        assertEquals(thermostat.getTemperature(), reloaded.getTemperature());
        assertEquals(thermostat.getLastUpdated(), reloaded.getLastUpdated());
    }

    @Test
    void updatingUnknownDeviceFails() {
        assertThrows(IllegalArgumentException.class, () -> dao.update(new Light(99999, "Missing Light", true)));
    }
}
