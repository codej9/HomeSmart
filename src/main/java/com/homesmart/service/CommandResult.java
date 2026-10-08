package com.homesmart.service;

import java.time.LocalDateTime;

/**
 * Outcome of a device command. The fields line up with the activity log
 * columns (device name, action performed, date/time).
 */
public record CommandResult(String deviceName, String action, LocalDateTime timestamp) {

    public CommandResult(String deviceName, String action) {
        this(deviceName, action, LocalDateTime.now());
    }

    /** Message shown to the user after the command, e.g. "Front Door Lock: Unlocked". */
    public String message() {
        return deviceName + ": " + action;
    }
}
