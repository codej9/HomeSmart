package com.homesmart.service;

import com.homesmart.dao.DeviceDAO;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;

import java.util.List;

/**
 * Device control logic for lights, thermostats and door locks.
 *
 * <p>Every command looks the device up, checks it is the right type, changes its state,
 * and saves it through the {@link DeviceDAO}. Invalid commands throw
 * {@link IllegalArgumentException} with a message that can be shown to the user.
 *
 * <p>Command methods are synchronized because Tomcat handles requests on many threads
 * and two users could change the same device at the same time.
 */
public class DeviceService {

    private final DeviceDAO deviceDAO;

    public DeviceService(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
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
        return setLight(lightId, true);
    }

    public synchronized CommandResult turnOff(int lightId) {
        return setLight(lightId, false);
    }

    public synchronized CommandResult toggleLight(int lightId) {
        Light light = getDevice(lightId, Light.class);
        return setLight(lightId, !light.isOn());
    }

    private CommandResult setLight(int lightId, boolean on) {
        Light light = getDevice(lightId, Light.class);
        light.setOn(on);
        deviceDAO.update(light);
        return new CommandResult(light.getId(), light.getName(), on ? "Turned on" : "Turned off");
    }

    // ---- Thermostat ----

    public synchronized CommandResult setTemperature(int thermostatId, int celsius) {
        Thermostat thermostat = getDevice(thermostatId, Thermostat.class);
        thermostat.setTemperature(celsius); // rejects values outside the allowed range
        deviceDAO.update(thermostat);
        return new CommandResult(thermostat.getId(), thermostat.getName(), "Set temperature to " + celsius + " °C");
    }

    /** Raises or lowers the target temperature by {@code delta} degrees. */
    public synchronized CommandResult adjustTemperature(int thermostatId, int delta) {
        Thermostat thermostat = getDevice(thermostatId, Thermostat.class);
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
        return setTemperature(thermostatId, target);
    }

    /** Eco preset: sets the thermostat to the energy-saving {@link Thermostat#ECO_TEMPERATURE} in one step. */
    public synchronized CommandResult setEcoMode(int thermostatId) {
        Thermostat thermostat = getDevice(thermostatId, Thermostat.class);
        thermostat.setTemperature(Thermostat.ECO_TEMPERATURE);
        deviceDAO.update(thermostat);
        return new CommandResult(thermostat.getId(), thermostat.getName(),
                "Eco mode: set temperature to " + Thermostat.ECO_TEMPERATURE + " °C");
    }

    // ---- Door lock ----

    public synchronized CommandResult lock(int lockId) {
        return setLock(lockId, true);
    }

    public synchronized CommandResult unlock(int lockId) {
        return setLock(lockId, false);
    }

    private CommandResult setLock(int lockId, boolean locked) {
        DoorLock doorLock = getDevice(lockId, DoorLock.class);
        doorLock.setLocked(locked);
        deviceDAO.update(doorLock);
        return new CommandResult(doorLock.getId(), doorLock.getName(), locked ? "Locked" : "Unlocked");
    }

    // ---- Request entry point ----

    /**
     * Runs a command sent from the dashboard form.
     *
     * @param action one of on, off, toggle, increase, decrease, set, eco, lock, unlock
     * @param value  the target temperature for "set"; ignored otherwise
     */
    public synchronized CommandResult execute(int deviceId, String action, String value) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("No action was given");
        }
        return switch (action) {
            case "on" -> turnOn(deviceId);
            case "off" -> turnOff(deviceId);
            case "toggle" -> toggleLight(deviceId);
            case "increase" -> adjustTemperature(deviceId, 1);
            case "decrease" -> adjustTemperature(deviceId, -1);
            case "set" -> {
                // Check the device first, so "set" sent to a light reports the wrong device, not the value
                getDevice(deviceId, Thermostat.class);
                yield setTemperature(deviceId, parseTemperature(value));
            }
            case "eco" -> setEcoMode(deviceId);
            case "lock" -> lock(deviceId);
            case "unlock" -> unlock(deviceId);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    private static int parseTemperature(String value) {
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

    /** Finds a device and checks it is the expected type (e.g. you cannot "lock" a light). */
    private <T extends Device> T getDevice(int id, Class<T> expectedType) {
        Device device = getDevice(id);
        if (!expectedType.isInstance(device)) {
            throw new IllegalArgumentException(device.getName() + " does not support this action");
        }
        return expectedType.cast(device);
    }
}
