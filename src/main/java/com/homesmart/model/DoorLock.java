package com.homesmart.model;

public class DoorLock extends Device {

    private boolean locked;

    public DoorLock(int id, String name, boolean locked) {
        super(id, name, DeviceType.DOOR_LOCK);
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public String getStatus() {
        return locked ? "Locked" : "Unlocked";
    }
}
