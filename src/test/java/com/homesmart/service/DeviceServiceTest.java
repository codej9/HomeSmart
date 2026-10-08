package com.homesmart.service;

import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the device control logic. Each test starts from the sample data:
 * light 1 (off), thermostat 2 (21 °C), door lock 3 (locked).
 */
class DeviceServiceTest {

    private static final int LIGHT = 1;
    private static final int THERMOSTAT = 2;
    private static final int DOOR_LOCK = 3;

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(new InMemoryDeviceDAO());
    }

    private Light light() {
        return (Light) service.getDevice(LIGHT);
    }

    private Thermostat thermostat() {
        return (Thermostat) service.getDevice(THERMOSTAT);
    }

    private DoorLock doorLock() {
        return (DoorLock) service.getDevice(DOOR_LOCK);
    }

    @Test
    void loadsTheThreeSampleDevicesInOrder() {
        var devices = service.getAllDevices();
        assertEquals(3, devices.size());
        assertEquals(LIGHT, devices.get(0).getId());
        assertEquals(THERMOSTAT, devices.get(1).getId());
        assertEquals(DOOR_LOCK, devices.get(2).getId());
    }

    @Nested
    class LightControls {

        @Test
        void turnOnAndOff() {
            service.turnOn(LIGHT);
            assertTrue(light().isOn());
            assertEquals("On", light().getStatus());

            service.turnOff(LIGHT);
            assertFalse(light().isOn());
            assertEquals("Off", light().getStatus());
        }

        @Test
        void toggleFlipsTheState() {
            service.toggleLight(LIGHT);
            assertTrue(light().isOn());
            service.toggleLight(LIGHT);
            assertFalse(light().isOn());
        }

        @Test
        void resultDescribesTheAction() {
            CommandResult result = service.turnOn(LIGHT);
            assertEquals("Living Room Light", result.deviceName());
            assertEquals("Turned on", result.action());
            assertNotNull(result.timestamp());
            assertEquals("Living Room Light: Turned on", result.message());
        }
    }

    @Nested
    class ThermostatControls {

        @Test
        void setTemperatureWithinRange() {
            service.setTemperature(THERMOSTAT, 24);
            assertEquals(24, thermostat().getTemperature());
            assertEquals("24 °C", thermostat().getStatus());
        }

        @Test
        void increaseAndDecreaseByOneDegree() {
            service.adjustTemperature(THERMOSTAT, 1);
            assertEquals(22, thermostat().getTemperature());
            service.adjustTemperature(THERMOSTAT, -1);
            service.adjustTemperature(THERMOSTAT, -1);
            assertEquals(20, thermostat().getTemperature());
        }

        @Test
        void acceptsTheRangeLimits() {
            service.setTemperature(THERMOSTAT, Thermostat.MIN_TEMPERATURE);
            assertEquals(Thermostat.MIN_TEMPERATURE, thermostat().getTemperature());
            service.setTemperature(THERMOSTAT, Thermostat.MAX_TEMPERATURE);
            assertEquals(Thermostat.MAX_TEMPERATURE, thermostat().getTemperature());
        }

        @Test
        void rejectsOutOfRangeAndKeepsOldValue() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.setTemperature(THERMOSTAT, Thermostat.MAX_TEMPERATURE + 1));
            assertThrows(IllegalArgumentException.class,
                    () -> service.setTemperature(THERMOSTAT, Thermostat.MIN_TEMPERATURE - 1));
            assertEquals(21, thermostat().getTemperature());
        }

        @Test
        void cannotIncreasePastMaximum() {
            service.setTemperature(THERMOSTAT, Thermostat.MAX_TEMPERATURE);
            assertThrows(IllegalArgumentException.class, () -> service.adjustTemperature(THERMOSTAT, 1));
            assertEquals(Thermostat.MAX_TEMPERATURE, thermostat().getTemperature());
        }
    }

    @Nested
    class DoorLockControls {

        @Test
        void unlockAndLock() {
            service.unlock(DOOR_LOCK);
            assertFalse(doorLock().isLocked());
            assertEquals("Unlocked", doorLock().getStatus());

            service.lock(DOOR_LOCK);
            assertTrue(doorLock().isLocked());
            assertEquals("Locked", doorLock().getStatus());
        }
    }

    @Nested
    class Execute {

        @Test
        void runsEachSupportedAction() {
            service.execute(LIGHT, "on", null);
            assertTrue(light().isOn());
            service.execute(LIGHT, "off", null);
            assertFalse(light().isOn());
            service.execute(LIGHT, "toggle", null);
            assertTrue(light().isOn());

            service.execute(THERMOSTAT, "increase", null);
            assertEquals(22, thermostat().getTemperature());
            service.execute(THERMOSTAT, "decrease", null);
            assertEquals(21, thermostat().getTemperature());
            service.execute(THERMOSTAT, "set", " 18 ");
            assertEquals(18, thermostat().getTemperature());

            service.execute(DOOR_LOCK, "unlock", null);
            assertFalse(doorLock().isLocked());
            service.execute(DOOR_LOCK, "lock", null);
            assertTrue(doorLock().isLocked());
        }

        @Test
        void rejectsUnknownDevice() {
            var e = assertThrows(IllegalArgumentException.class, () -> service.execute(99, "on", null));
            assertEquals("No device with id 99", e.getMessage());
        }

        @Test
        void rejectsActionForWrongDeviceType() {
            assertThrows(IllegalArgumentException.class, () -> service.execute(LIGHT, "lock", null));
            assertThrows(IllegalArgumentException.class, () -> service.execute(DOOR_LOCK, "on", null));
            assertThrows(IllegalArgumentException.class, () -> service.execute(THERMOSTAT, "toggle", null));
        }

        @Test
        void rejectsMissingOrUnknownAction() {
            assertThrows(IllegalArgumentException.class, () -> service.execute(LIGHT, null, null));
            assertThrows(IllegalArgumentException.class, () -> service.execute(LIGHT, "explode", null));
        }

        @Test
        void rejectsNonNumericTemperature() {
            assertThrows(IllegalArgumentException.class, () -> service.execute(THERMOSTAT, "set", "warm"));
            assertThrows(IllegalArgumentException.class, () -> service.execute(THERMOSTAT, "set", null));
            assertEquals(21, thermostat().getTemperature());
        }
    }
}
