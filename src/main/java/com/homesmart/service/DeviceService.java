package com.homesmart.service;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.dao.DeviceDAO;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Provenance;
import com.homesmart.model.Thermostat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Device control logic for lights, thermostats and door locks.
 *
 * <p>Every command looks the device up, checks it is the right type, changes its state,
 * saves it through the {@link DeviceDAO} and records it in the {@link ActivityLogDAO}.
 * A command that leaves the device as it was saves and records nothing.
 * Invalid commands throw {@link IllegalArgumentException} with a message that can be shown to the user.
 *
 * <p>Command methods are synchronized because Tomcat handles requests on many threads
 * and two users could change the same device at the same time. Logging inside the lock
 * keeps the activity log in command order.
 */
public class DeviceService {

    private static final Logger LOGGER = Logger.getLogger(DeviceService.class.getName());

    private final DeviceDAO deviceDAO;
    private final ActivityLogDAO activityLog;

    public DeviceService(DeviceDAO deviceDAO, ActivityLogDAO activityLog) {
        this.deviceDAO = deviceDAO;
        this.activityLog = activityLog;
    }

    public List<Device> getAllDevices() {
        return deviceDAO.findAll();
    }

    public Device getDevice(int id) {
        return deviceDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No device with id " + id));
    }

    // ---- Light ----

    public synchronized CommandResult turnOn(int lightId) {
        return setLight(getDevice(lightId, Light.class), true, Provenance.USER);
    }

    public synchronized CommandResult turnOff(int lightId) {
        return setLight(getDevice(lightId, Light.class), false, Provenance.USER);
    }

    public synchronized CommandResult toggleLight(int lightId) {
        Light light = getDevice(lightId, Light.class);
        return setLight(light, !light.isOn(), Provenance.USER);
    }

    private CommandResult setLight(Light light, boolean on, Provenance provenance) {
        CommandResult result = new CommandResult(light.getId(), light.getName(),
                provenance.describe(on ? "Turned on" : "Turned off"),
                on ? ActivityType.TURN_ON : ActivityType.TURN_OFF, null);
        boolean changed = light.isOn() != on;
        light.setOn(on);
        deviceDAO.update(light);
        recordIfChanged(changed, result, provenance);
        return result;
    }

    // ---- Thermostat ----

    public synchronized CommandResult setTemperature(int thermostatId, int celsius) {
        return applyTemperature(getDevice(thermostatId, Thermostat.class), celsius, Provenance.USER);
    }

    /** Raises or lowers the target temperature by {@code delta} degrees. */
    public synchronized CommandResult adjustTemperature(int thermostatId, int delta) {
        Thermostat thermostat = getDevice(thermostatId, Thermostat.class);
        return adjust(thermostat, delta, Provenance.USER);
    }

    /** Eco preset: sets the thermostat to the energy-saving {@link Thermostat#ECO_TEMPERATURE} in one step. */
    public synchronized CommandResult setEcoMode(int thermostatId) {
        return eco(getDevice(thermostatId, Thermostat.class), Provenance.USER);
    }

    private CommandResult eco(Thermostat thermostat, Provenance provenance) {
        return applyTemperature(thermostat, Thermostat.ECO_TEMPERATURE,
                "Eco mode: set temperature to " + Thermostat.ECO_TEMPERATURE + " °C", provenance);
    }

    private CommandResult applyTemperature(Thermostat thermostat, int celsius, Provenance provenance) {
        return applyTemperature(thermostat, celsius, "Set temperature to " + celsius + " °C", provenance);
    }

    private CommandResult applyTemperature(Thermostat thermostat, int celsius, String action, Provenance provenance) {
        CommandResult result = new CommandResult(thermostat.getId(), thermostat.getName(),
                provenance.describe(action), ActivityType.SET_TEMPERATURE, celsius);
        boolean changed = thermostat.getTemperature() != celsius;
        thermostat.setTemperature(celsius); // rejects values outside the allowed range
        deviceDAO.update(thermostat);
        recordIfChanged(changed, result, provenance);
        return result;
    }

    // ---- Door lock ----

    public synchronized CommandResult lock(int lockId) {
        return setLock(getDevice(lockId, DoorLock.class), true, Provenance.USER);
    }

    public synchronized CommandResult unlock(int lockId) {
        return setLock(getDevice(lockId, DoorLock.class), false, Provenance.USER);
    }

    private CommandResult setLock(DoorLock doorLock, boolean locked, Provenance provenance) {
        CommandResult result = new CommandResult(doorLock.getId(), doorLock.getName(),
                provenance.describe(locked ? "Locked" : "Unlocked"),
                locked ? ActivityType.LOCK : ActivityType.UNLOCK, null);
        boolean changed = doorLock.isLocked() != locked;
        doorLock.setLocked(locked);
        deviceDAO.update(doorLock);
        recordIfChanged(changed, result, provenance);
        return result;
    }

    // ---- Request entry point ----

    /**
     * Runs a command sent from the dashboard form.
     *
     * @param action one of on, off, toggle, increase, decrease, set, eco, lock, unlock
     * @param value  the target temperature for "set"; ignored otherwise
     */
    public synchronized CommandResult execute(int deviceId, String action, String value) {
        return execute(deviceId, action, value, Provenance.USER);
    }

    /** Runs a command for {@code provenance}, for example a schedule. */
    public synchronized CommandResult execute(int deviceId, String action, String value, Provenance provenance) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("No action was given");
        }
        Device device = getDevice(deviceId);
        return switch (action) {
            case "on" -> setLight(as(device, Light.class), true, provenance);
            case "off" -> setLight(as(device, Light.class), false, provenance);
            case "toggle" -> {
                Light light = as(device, Light.class);
                yield setLight(light, !light.isOn(), provenance);
            }
            case "increase" -> adjust(as(device, Thermostat.class), 1, provenance);
            case "decrease" -> adjust(as(device, Thermostat.class), -1, provenance);
            case "set" -> applyTemperature(as(device, Thermostat.class), parseTemperature(value), provenance);
            case "eco" -> eco(as(device, Thermostat.class), provenance);
            case "lock" -> setLock(as(device, DoorLock.class), true, provenance);
            case "unlock" -> setLock(as(device, DoorLock.class), false, provenance);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    private CommandResult adjust(Thermostat thermostat, int delta, Provenance provenance) {
        int target = thermostat.getTemperature() + delta;
        // The user pressed + or −, so say which limit was reached rather than repeating the range
        if (target > Thermostat.MAX_TEMPERATURE) {
            throw new IllegalArgumentException(thermostat.getName()
                    + " is already at the maximum of " + Thermostat.MAX_TEMPERATURE + " °C");
        }
        if (target < Thermostat.MIN_TEMPERATURE) {
            throw new IllegalArgumentException(thermostat.getName()
                    + " is already at the minimum of " + Thermostat.MIN_TEMPERATURE + " °C");
        }
        return applyTemperature(thermostat, target, provenance);
    }

    static int parseTemperature(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a target temperature");
        }
        String trimmed = value.trim();
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            if (trimmed.matches("[+-]?\\d+")) {
                // A whole number, just too large for an int, e.g. 99999999999
                throw new IllegalArgumentException(Thermostat.RANGE_MESSAGE);
            }
            throw new IllegalArgumentException("Temperature must be a whole number");
        }
    }

    /** The device was saved, so a failed log insert must not fail the command. */
    private void record(CommandResult result, Provenance provenance) {
        try {
            activityLog.record(result, provenance);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Command ran but could not be logged: " + result.message(), e);
        }
    }

    /** Every successful command updates the device's last-updated time, but only a change goes in the log. */
    private void recordIfChanged(boolean changed, CommandResult result, Provenance provenance) {
        if (changed) {
            record(result, provenance);
        }
    }

    /** Finds a device and checks it is the expected type (e.g. you cannot "lock" a light). */
    private <T extends Device> T getDevice(int id, Class<T> expectedType) {
        return as(getDevice(id), expectedType);
    }

    private static <T extends Device> T as(Device device, Class<T> expectedType) {
        if (!expectedType.isInstance(device)) {
            throw new IllegalArgumentException(device.getName() + " does not support this action");
        }
        return expectedType.cast(device);
    }
}
