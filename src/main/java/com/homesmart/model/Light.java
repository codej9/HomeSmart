package com.homesmart.model;

public class Light extends Device {

    private boolean on;

    public Light(int id, String name, boolean on) {
        super(id, name, DeviceType.LIGHT);
        this.on = on;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    @Override
    public String getStatus() {
        return on ? "On" : "Off";
    }
}
