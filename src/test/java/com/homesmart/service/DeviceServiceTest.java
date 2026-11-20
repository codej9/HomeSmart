package com.homesmart.service;

import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Provenance;
import com.homesmart.model.Thermostat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private InMemoryActivityLogDAO log;

    @BeforeEach
    void setUp() {
        log = new InMemoryActivityLogDAO();
        service = new DeviceService(new InMemoryDeviceDAO(), log);
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
            var e = assertThrows(IllegalArgumentException.class, () -> service.adjustTemperature(THERMOSTAT, 1));
            assertEquals("Main Thermostat is already at the maximum of 32 °C", e.getMessage());
            assertEquals(Thermostat.MAX_TEMPERATURE, thermostat().getTemperature());
        }

        @Test
        void cannotDecreasePastMinimum() {
            service.setTemperature(THERMOSTAT, Thermostat.MIN_TEMPERATURE);
            var e = assertThrows(IllegalArgumentException.class, () -> service.adjustTemperature(THERMOSTAT, -1));
            assertEquals("Main Thermostat is already at the minimum of 10 °C", e.getMessage());
            assertEquals(Thermostat.MIN_TEMPERATURE, thermostat().getTemperature());
        }
    }

    @Nested
    class EcoMode {

        @Test
        void setsTheEcoTemperature() {
            CommandResult result = service.setEcoMode(THERMOSTAT);
            assertEquals(Thermostat.ECO_TEMPERATURE, thermostat().getTemperature());
            assertTrue(thermostat().isEcoMode());
            assertEquals("20 °C · Eco", thermostat().getStatus());
            assertEquals("Main Thermostat: Eco mode: set temperature to 20 °C", result.message());
        }

        @Test
        void worksFromEitherLimit() {
            service.setTemperature(THERMOSTAT, Thermostat.MAX_TEMPERATURE);
            service.execute(THERMOSTAT, "eco", null);
            assertEquals(Thermostat.ECO_TEMPERATURE, thermostat().getTemperature());

            service.setTemperature(THERMOSTAT, Thermostat.MIN_TEMPERATURE);
            service.execute(THERMOSTAT, "eco", null);
            assertEquals(Thermostat.ECO_TEMPERATURE, thermostat().getTemperature());
        }

        @Test
        void pressingAgainKeepsEcoMode() {
            service.setEcoMode(THERMOSTAT);
            service.setEcoMode(THERMOSTAT);
            assertEquals(Thermostat.ECO_TEMPERATURE, thermostat().getTemperature());
        }

        @Test
        void leavesEcoModeWhenTheTemperatureChanges() {
            service.setEcoMode(THERMOSTAT);
            service.adjustTemperature(THERMOSTAT, 1);
            assertFalse(thermostat().isEcoMode());
            assertEquals("21 °C", thermostat().getStatus());
        }

        @Test
        void onlyThermostatsHaveEcoMode() {
            var e = assertThrows(IllegalArgumentException.class, () -> service.execute(LIGHT, "eco", null));
            assertEquals("Living Room Light does not support this action", e.getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.execute(DOOR_LOCK, "eco", null));
            assertFalse(light().isOn());
            assertTrue(doorLock().isLocked());
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
            service.execute(THERMOSTAT, "eco", null);
            assertEquals(Thermostat.ECO_TEMPERATURE, thermostat().getTemperature());

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

        /** Each bad "set" value gets a message that says what to fix, and the temperature stays at 21 °C. */
        @Test
        void explainsWhatIsWrongWithATemperature() {
            assertEquals("Enter a target temperature", setError(""));
            assertEquals("Enter a target temperature", setError("   "));
            assertEquals("Enter a target temperature", setError(null));
            assertEquals("Temperature must be a whole number", setError("21.5"));
            assertEquals("Temperature must be a whole number", setError("warm"));
            assertEquals("Temperature must be between 10 and 32 °C", setError("40"));
            assertEquals("Temperature must be between 10 and 32 °C", setError("-5"));
            // Too large for an int: still a whole number, so report the range rather than "whole number"
            assertEquals("Temperature must be between 10 and 32 °C", setError("99999999999"));
            assertEquals(21, thermostat().getTemperature());
        }

        @Test
        void reportsTheWrongDeviceBeforeABadValue() {
            var e = assertThrows(IllegalArgumentException.class, () -> service.execute(LIGHT, "set", "warm"));
            assertEquals("Living Room Light does not support this action", e.getMessage());
        }

        private String setError(String value) {
            return assertThrows(IllegalArgumentException.class,
                    () -> service.execute(THERMOSTAT, "set", value)).getMessage();
        }
    }

    @Nested
    class Logging {

        @Test
        void everyStateChangeIsRecordedWithItsType() {
            service.turnOn(LIGHT);
            service.setTemperature(THERMOSTAT, 23);
            service.unlock(DOOR_LOCK);
            service.lock(DOOR_LOCK);
            service.turnOff(LIGHT);

            List<ActivityEntry> entries = log.all();
            assertEquals(List.of(ActivityType.TURN_OFF, ActivityType.LOCK, ActivityType.UNLOCK,
                            ActivityType.SET_TEMPERATURE, ActivityType.TURN_ON),
                    entries.stream().map(ActivityEntry::type).toList());
            ActivityEntry setpoint = entries.get(3);
            assertEquals(23, setpoint.value());
            assertEquals(THERMOSTAT, setpoint.deviceId());
            assertEquals("Set temperature to 23 °C", setpoint.action());
            assertNull(entries.get(0).value());
            assertEquals(ActivitySource.USER, entries.get(0).source());
        }

        @Test
        void increaseRecordsOneRowWithTheNewSetpoint() {
            service.execute(THERMOSTAT, "increase", null);
            assertEquals(1, log.all().size());
            assertEquals(22, log.all().get(0).value());
        }

        @Test
        void ecoIsRecordedLikeOtherThermostatCommands() {
            service.execute(THERMOSTAT, "set", "25");
            service.execute(THERMOSTAT, "eco", null);

            ActivityEntry eco = log.all().get(0);
            assertEquals(2, log.all().size());
            assertEquals(ActivityType.SET_TEMPERATURE, eco.type());
            assertEquals(20, eco.value());
            assertEquals("Eco mode: set temperature to 20 °C", eco.action());
            assertEquals(ActivitySource.USER, eco.source());
        }

        @Test
        void repeatedCommandsThatChangeNothingRecordNothing() {
            service.execute(DOOR_LOCK, "unlock", null);
            service.execute(DOOR_LOCK, "unlock", null);
            service.execute(LIGHT, "on", null);
            CommandResult again = service.execute(LIGHT, "on", null);
            service.execute(THERMOSTAT, "set", "21");

            assertEquals("Turned on", again.action());
            assertEquals(List.of(ActivityType.TURN_ON, ActivityType.UNLOCK),
                    log.all().stream().map(ActivityEntry::type).toList());
        }

        @Test
        void rejectedCommandRecordsNothing() {
            assertThrows(IllegalArgumentException.class, () -> service.setTemperature(THERMOSTAT, 99));
            assertEquals(0, log.all().size());
        }

        @Test
        void scheduleCommandIsLabelledWithTheScheduleName() {
            CommandResult result = service.execute(LIGHT, "on", null, Provenance.schedule("Evening light"));

            ActivityEntry entry = log.all().get(0);
            assertEquals("Turned on (schedule: Evening light)", entry.action());
            assertEquals(result.action(), entry.action());
            assertEquals(ActivitySource.SCHEDULE, entry.source());
            assertEquals("Evening light", entry.detail());
        }

        @Test
        void failedLogInsertDoesNotFailTheCommand() {
            DeviceService broken = new DeviceService(new InMemoryDeviceDAO(), new InMemoryActivityLogDAO() {
                @Override
                public long record(CommandResult result, Provenance provenance) {
                    throw new IllegalStateException("log is down");
                }
            });
            broken.turnOn(LIGHT);
            assertTrue(((Light) broken.getDevice(LIGHT)).isOn());
        }
    }
}
