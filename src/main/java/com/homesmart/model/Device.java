package com.homesmart.model;

import java.time.Instant;

/**
 * Common data shared by every smart-home device.
 * Each subclass holds its own state (on/off, temperature, locked/unlocked).
 */
public abstract class Device {

    private final int id;
    private final String name;
    private final DeviceType type;
    private volatile Instant lastUpdated;

    protected Device(int id, String name, DeviceType type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Device name is required");
        }
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DeviceType getType() {
        return type;
    }

    /** The last successful update, or null if the device has no recorded update. */
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Short, human-readable state shown on the dashboard, e.g. "On", "21 °C" or "Locked".
     */
    public abstract String getStatus();
}
