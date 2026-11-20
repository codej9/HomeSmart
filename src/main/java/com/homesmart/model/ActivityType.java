package com.homesmart.model;

/** What a logged command did. The name is stored in the {@code activity_type} column. */
public enum ActivityType {
    TURN_ON, TURN_OFF, SET_TEMPERATURE, LOCK, UNLOCK, DOOR_LEFT_UNLOCKED
}
