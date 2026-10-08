package com.homesmart.model;

public class Thermostat extends Device {

    /** Allowed target range in degrees Celsius. */
    public static final int MIN_TEMPERATURE = 10;
    public static final int MAX_TEMPERATURE = 32;

    private int temperature;

    public Thermostat(int id, String name, int temperature) {
        super(id, name, DeviceType.THERMOSTAT);
        setTemperature(temperature);
    }

    public int getTemperature() {
        return temperature;
    }

    /**
     * @throws IllegalArgumentException if the value is outside {@link #MIN_TEMPERATURE}..{@link #MAX_TEMPERATURE}
     */
    public void setTemperature(int temperature) {
        if (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            throw new IllegalArgumentException("Temperature must be between "
                    + MIN_TEMPERATURE + " and " + MAX_TEMPERATURE + " °C");
        }
        this.temperature = temperature;
    }

    // Instance getters so JSP pages can read the limits with EL, e.g. ${device.minTemperature}
    public int getMinTemperature() {
        return MIN_TEMPERATURE;
    }

    public int getMaxTemperature() {
        return MAX_TEMPERATURE;
    }

    @Override
    public String getStatus() {
        return temperature + " °C";
    }
}
