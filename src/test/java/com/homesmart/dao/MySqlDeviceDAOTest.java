package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests: device commands are saved in the MySQL {@code devices} table (homesmart.sql)
 * and read back from it.
 *
 * <p>Runs only when db.properties exists and MySQL is reachable, otherwise every test is skipped,
 * so the build still passes without a database. The original rows are put back after each test.
 */
class MySqlDeviceDAOTest {

    private Properties config;
    private MySqlDeviceDAO dao;
    private DeviceService service;
    private List<Device> originalDevices;

    @BeforeEach
    void connect() throws IOException {
        config = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            assumeTrue(in != null, "db.properties not found");
            config.load(in);
        }
        dao = newDao();
        try {
            dao.checkConnection();
        } catch (IllegalStateException e) {
            assumeTrue(false, "MySQL not reachable: " + e.getMessage());
        }
        service = new DeviceService(dao);
        originalDevices = dao.findAll();
    }

    @AfterEach
    void restoreOriginalRows() {
        if (originalDevices != null) {
            originalDevices.forEach(dao::update);
        }
    }

    private MySqlDeviceDAO newDao() {
        return new MySqlDeviceDAO(
                config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password", ""));
    }

    /** Reads the row with a brand-new DAO, exactly as the app would after a restart. */
    private <T extends Device> T reload(T device, Class<T> type) {
        return type.cast(newDao().findById(device.getId()).orElseThrow());
    }

    /** Writes a value straight into the temperature column, as someone editing the table by hand could. */
    private void writeRawTemperature(int deviceId, String value) throws SQLException {
        try (Connection connection = DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password", ""));
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE devices SET temperature = ? WHERE device_id = ?")) {
            statement.setObject(1, value == null ? null : new BigDecimal(value), Types.DECIMAL);
            statement.setInt(2, deviceId);
            statement.executeUpdate();
        }
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
        service.execute(light.getId(), "toggle", null);
        assertEquals(!light.isOn(), reload(light, Light.class).isOn());
    }

    @Test
    void savesThermostatTemperature() {
        Thermostat thermostat = first(Thermostat.class);
        int target = thermostat.getTemperature() == 20 ? 22 : 20;
        service.execute(thermostat.getId(), "set", String.valueOf(target));
        assertEquals(target, reload(thermostat, Thermostat.class).getTemperature());

        service.execute(thermostat.getId(), "increase", null);
        assertEquals(target + 1, reload(thermostat, Thermostat.class).getTemperature());
    }

    @Test
    void savesDoorLockState() {
        DoorLock doorLock = first(DoorLock.class);
        service.execute(doorLock.getId(), doorLock.isLocked() ? "unlock" : "lock", null);
        assertEquals(!doorLock.isLocked(), reload(doorLock, DoorLock.class).isLocked());
    }

    @Test
    void rejectedCommandLeavesRowUnchanged() {
        Thermostat thermostat = first(Thermostat.class);
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(thermostat.getId(), "set", String.valueOf(Thermostat.MAX_TEMPERATURE + 1)));
        assertEquals(thermostat.getTemperature(), reload(thermostat, Thermostat.class).getTemperature());
    }

    @Test
    void updatingUnknownDeviceFails() {
        assertThrows(IllegalArgumentException.class, () -> dao.update(new Light(99999, "Missing Light", true)));
    }

    // ---- Release 2 ----

    /** All nine dashboard commands reach MySQL and are still there for a freshly started app. */
    @Test
    void everyCommandIsSavedAndSurvivesARestart() {
        Light light = first(Light.class);
        Thermostat thermostat = first(Thermostat.class);
        DoorLock doorLock = first(DoorLock.class);

        service.execute(light.getId(), "on", null);
        assertTrue(reload(light, Light.class).isOn());
        service.execute(light.getId(), "off", null);
        assertFalse(reload(light, Light.class).isOn());
        service.execute(light.getId(), "toggle", null);
        assertTrue(reload(light, Light.class).isOn());

        service.execute(thermostat.getId(), "set", "25");
        assertEquals(25, reload(thermostat, Thermostat.class).getTemperature());
        service.execute(thermostat.getId(), "increase", null);
        assertEquals(26, reload(thermostat, Thermostat.class).getTemperature());
        service.execute(thermostat.getId(), "decrease", null);
        assertEquals(25, reload(thermostat, Thermostat.class).getTemperature());
        service.execute(thermostat.getId(), "eco", null);
        assertEquals(Thermostat.ECO_TEMPERATURE, reload(thermostat, Thermostat.class).getTemperature());

        service.execute(doorLock.getId(), "unlock", null);
        assertFalse(reload(doorLock, DoorLock.class).isLocked());
        service.execute(doorLock.getId(), "lock", null);
        assertTrue(reload(doorLock, DoorLock.class).isLocked());
    }

    /** Saving a value the row already has must not be mistaken for "no such device". */
    @Test
    void pressingEcoTwiceIsSavedWithoutError() {
        Thermostat thermostat = first(Thermostat.class);
        service.execute(thermostat.getId(), "eco", null);
        assertDoesNotThrow(() -> service.execute(thermostat.getId(), "eco", null));
        assertTrue(reload(thermostat, Thermostat.class).isEcoMode());
    }

    /**
     * Bug found in Release 2 testing: a bad temperature in the table (empty, or outside 10–32 °C) made every
     * dashboard page fail with HTTP 500. The devices must still load, with the value kept inside the range.
     */
    @ParameterizedTest(name = "saved {0} °C is shown as {1} °C")
    @CsvSource(value = {"40, 32", "32.5, 32", "9.4, 10", "-3, 10", "21.5, 22", "NULL, 20"}, nullValues = "NULL")
    void badSavedTemperatureStillLoads(String saved, int shown) throws SQLException {
        Thermostat thermostat = first(Thermostat.class);
        writeRawTemperature(thermostat.getId(), saved);

        List<Device> devices = assertDoesNotThrow(() -> newDao().findAll());
        assertEquals(originalDevices.size(), devices.size());
        assertEquals(shown, reload(thermostat, Thermostat.class).getTemperature());

        // The next command saves a valid value again
        service.execute(thermostat.getId(), "eco", null);
        assertEquals(Thermostat.ECO_TEMPERATURE, reload(thermostat, Thermostat.class).getTemperature());
    }
}
