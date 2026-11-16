package com.homesmart.service;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.DoorLock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/** Rules for the "door left unlocked" alert. */
public final class SecurityAlerts {

    /** The log entries that tell how long a door has been in its current state. */
    public static final Set<ActivityType> LOCK_EVENTS = Set.of(ActivityType.LOCK, ActivityType.UNLOCK);

    /** One banner on the dashboard: the door it is about and the text to show. */
    public record Banner(int deviceId, String text) {
    }

    private SecurityAlerts() {
    }

    /**
     * Banner text for {@code door}, or empty when it needs none.
     *
     * @param latestLockEvent the newest {@code LOCK} or {@code UNLOCK} entry of the door
     */
    public static Optional<String> evaluate(DoorLock door, Optional<ActivityEntry> latestLockEvent,
                                            LocalDateTime now, Duration threshold) {
        if (door.isLocked()) {
            return Optional.empty();
        }
        // Without an unlock entry the time is unknown, so the banner shows at once
        if (latestLockEvent.isEmpty() || latestLockEvent.get().type() != ActivityType.UNLOCK) {
            return Optional.of(door.getName() + " is unlocked.");
        }
        Duration unlocked = Duration.between(latestLockEvent.get().timestamp(), now);
        if (unlocked.compareTo(threshold) < 0) {
            return Optional.empty();
        }
        return Optional.of(door.getName() + " has been unlocked for " + unlocked.toMinutes() + " min.");
    }
}
