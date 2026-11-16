package com.homesmart.model;

public class Thermostat extends Device {

    /** Allowed target range in degrees Celsius. */
    public static final int MIN_TEMPERATURE = 10;
    public static final int MAX_TEMPERATURE = 32;

    /** Energy-saving preset applied by the Eco mode button. */
    public static final int ECO_TEMPERATURE = 20;

    public static final String RANGE_MESSAGE =
            "Temperature must be between " + MIN_TEMPERATURE + " and " + MAX_TEMPERATURE + " °C";

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
        requireInRange(temperature);
        this.temperature = temperature;
    }

    /**
     * @throws IllegalArgumentException if the value is outside {@link #MIN_TEMPERATURE}..{@link #MAX_TEMPERATURE}
     */
    public static void requireInRange(int temperature) {
        if (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            throw new IllegalArgumentException(RANGE_MESSAGE);
        }
    }

    /** True when the thermostat is at the Eco preset temperature. */
    public boolean isEcoMode() {
        return temperature == ECO_TEMPERATURE;
    }

    // Instance getters so JSP pages can read the limits with EL, e.g. ${device.minTemperature}
    public int getMinTemperature() {
        return MIN_TEMPERATURE;
    }

    public int getMaxTemperature() {
        return MAX_TEMPERATURE;
    }

    public int getEcoTemperature() {
        return ECO_TEMPERATURE;
    }

    @Override
    public String getStatus() {
        return temperature + " °C" + (isEcoMode() ? " · Eco" : "");
    }
}
