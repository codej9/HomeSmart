package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private MySqlDeviceDAO dao;
    private DeviceService service;
    private List<Device> originalDevices;

    @BeforeEach
    void connect() throws IOException {
        Properties config = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            assumeTrue(in != null, "db.properties not found");
            config.load(in);
        }
        dao = new MySqlDeviceDAO(
                config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password", ""));
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

    /** MySqlDeviceDAO keeps no cache, so this reads the row again exactly as a restarted app would. */
    private <T extends Device> T reload(T device, Class<T> type) {
        return type.cast(dao.findById(device.getId()).orElseThrow());
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
}
