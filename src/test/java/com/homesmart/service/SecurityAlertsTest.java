package com.homesmart.service;

import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.DoorLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAlertsTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final LocalDateTime UNLOCKED_AT = LocalDateTime.of(2026, 11, 18, 10, 0);

    private static Optional<ActivityEntry> event(ActivityType type) {
        return Optional.of(new ActivityEntry(1, 3, "Front Door Lock", type.name(), type, null,
                ActivitySource.USER, null, null, UNLOCKED_AT));
    }

    private static Optional<String> evaluate(boolean locked, Optional<ActivityEntry> latest, long minutesLater) {
        return SecurityAlerts.evaluate(new DoorLock(3, "Front Door Lock", locked), latest,
                UNLOCKED_AT.plusMinutes(minutesLater), TEN_MINUTES);
    }

    @Test
    void lockedDoorShowsNothing() {
        assertTrue(evaluate(true, event(ActivityType.UNLOCK), 60).isEmpty());
    }

    @Test
    void unlockedBelowThresholdShowsNothing() {
        assertTrue(evaluate(false, event(ActivityType.UNLOCK), 9).isEmpty());
    }

    @Test
    void unlockedAtThresholdShowsTheMinutes() {
        assertEquals("Front Door Lock has been unlocked for 10 min.", evaluate(false, event(ActivityType.UNLOCK), 10).orElseThrow());
    }

    @Test
    void unlockedAboveThresholdRoundsMinutesDown() {
        Optional<String> text = SecurityAlerts.evaluate(new DoorLock(3, "Front Door Lock", false),
                event(ActivityType.UNLOCK), UNLOCKED_AT.plusMinutes(12).plusSeconds(59), TEN_MINUTES);
        assertEquals("Front Door Lock has been unlocked for 12 min.", text.orElseThrow());
    }

    @Test
    void unlockedWithNoEventShowsAtOnce() {
        assertEquals("Front Door Lock is unlocked.", evaluate(false, Optional.empty(), 0).orElseThrow());
    }

    @Test
    void unlockedWhoseLatestEventIsLockShowsAtOnce() {
        assertEquals("Front Door Lock is unlocked.", evaluate(false, event(ActivityType.LOCK), 0).orElseThrow());
    }
}
