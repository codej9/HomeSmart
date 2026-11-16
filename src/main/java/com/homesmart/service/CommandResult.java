package com.homesmart.service;

import com.homesmart.model.ActivityType;

import java.time.LocalDateTime;

/**
 * Outcome of a device command. The fields line up with the activity log columns;
 * {@code deviceName} is read from the device and {@code value} is the new setpoint for thermostat commands.
 */
public record CommandResult(int deviceId, String deviceName, String action, ActivityType type, Integer value,
                            LocalDateTime timestamp) {

    public CommandResult(int deviceId, String deviceName, String action, ActivityType type, Integer value) {
        this(deviceId, deviceName, action, type, value, LocalDateTime.now());
    }

    /** Message shown to the user after the command, e.g. "Front Door Lock: Unlocked". */
    public String message() {
        return deviceName + ": " + action;
    }
}
