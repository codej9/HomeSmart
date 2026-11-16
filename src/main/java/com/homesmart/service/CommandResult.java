package com.homesmart.service;

import java.time.LocalDateTime;

/**
 * Outcome of a device command. The fields line up with the activity log
 * columns (device, action performed, date/time); {@code deviceName} is read from the device.
 */
public record CommandResult(int deviceId, String deviceName, String action, LocalDateTime timestamp) {

    public CommandResult(int deviceId, String deviceName, String action) {
        this(deviceId, deviceName, action, LocalDateTime.now());
    }

    /** Message shown to the user after the command, e.g. "Front Door Lock: Unlocked". */
    public String message() {
        return deviceName + ": " + action;
    }
}
