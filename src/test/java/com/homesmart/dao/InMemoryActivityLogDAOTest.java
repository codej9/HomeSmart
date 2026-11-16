package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryActivityLogDAOTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 18, 10, 0);

    private final ActivityLogDAO log = new InMemoryActivityLogDAO();

    private long record(int deviceId, ActivityType type, LocalDateTime at) {
        return log.record(new CommandResult(deviceId, "Device " + deviceId, type.name(), type, null, at),
                Provenance.USER);
    }

    @Test
    void recordedCommandsAreNewestFirstAndRecentIsLimited() {
        record(1, ActivityType.TURN_ON, T0);
        record(3, ActivityType.UNLOCK, T0.plusMinutes(1));
        record(2, ActivityType.SET_TEMPERATURE, T0.plusMinutes(2));

        List<ActivityEntry> recent = log.recent(2);
        assertEquals(2, recent.size());
        assertEquals(ActivityType.SET_TEMPERATURE, recent.get(0).type());
        assertEquals(ActivityType.UNLOCK, recent.get(1).type());
        assertEquals(3, log.all().size());
    }

    @Test
    void sameInstantKeepsInsertionOrder() {
        record(1, ActivityType.TURN_ON, T0);
        record(1, ActivityType.TURN_OFF, T0);
        assertEquals(ActivityType.TURN_OFF, log.all().get(0).type());
    }

    @Test
    void recordKeepsValueSourceAndDetail() {
        log.record(new CommandResult(2, "Thermostat", "Set temperature to 20 °C (schedule: Night)",
                ActivityType.SET_TEMPERATURE, 20, T0), Provenance.schedule("Night"));
        ActivityEntry entry = log.all().get(0);
        assertEquals(20, entry.value());
        assertEquals(ActivitySource.SCHEDULE, entry.source());
        assertEquals("Night", entry.detail());
    }

    @Test
    void latestOfTypesFiltersByDeviceAndType() {
        record(3, ActivityType.UNLOCK, T0);
        record(3, ActivityType.LOCK, T0.plusMinutes(1));
        record(1, ActivityType.TURN_ON, T0.plusMinutes(2));

        assertEquals(ActivityType.LOCK, log.latestOfTypes(3, Set.of(ActivityType.LOCK, ActivityType.UNLOCK))
                .orElseThrow().type());
        assertTrue(log.latestOfTypes(3, Set.of(ActivityType.TURN_ON)).isEmpty());
    }

    @Test
    void latestAtOrBeforeIncludesTheBoundary() {
        record(1, ActivityType.TURN_ON, T0);
        record(1, ActivityType.TURN_OFF, T0.plusHours(1));
        Set<ActivityType> types = Set.of(ActivityType.TURN_ON, ActivityType.TURN_OFF);

        assertEquals(ActivityType.TURN_OFF, log.latestOfTypesAtOrBefore(1, types, T0.plusHours(1)).orElseThrow().type());
        assertEquals(ActivityType.TURN_ON, log.latestOfTypesAtOrBefore(1, types, T0.plusMinutes(59)).orElseThrow().type());
        assertTrue(log.latestOfTypesAtOrBefore(1, types, T0.minusSeconds(1)).isEmpty());
    }

    @Test
    void betweenExcludesFromIncludesToAndIsOldestFirst() {
        record(1, ActivityType.TURN_ON, T0);
        record(1, ActivityType.TURN_OFF, T0.plusHours(1));
        record(1, ActivityType.TURN_ON, T0.plusHours(2));
        Set<ActivityType> types = Set.of(ActivityType.TURN_ON, ActivityType.TURN_OFF);

        List<ActivityEntry> rows = log.ofTypesBetween(1, types, T0, T0.plusHours(2));
        assertEquals(List.of(ActivityType.TURN_OFF, ActivityType.TURN_ON), rows.stream().map(ActivityEntry::type).toList());
    }

    @Test
    void doorAlertIsRecordedOncePerUnlock() {
        long unlock = record(3, ActivityType.UNLOCK, T0);

        assertTrue(log.recordDoorAlert(3, unlock, 10, T0.plusMinutes(10)));
        assertFalse(log.recordDoorAlert(3, unlock, 10, T0.plusMinutes(11)));

        ActivityEntry alert = log.all().get(0);
        assertEquals(ActivityType.DOOR_LEFT_UNLOCKED, alert.type());
        assertEquals(ActivitySource.SYSTEM, alert.source());
        assertEquals(unlock, alert.refLogId());
        assertEquals("Security alert: left unlocked for 10 min", alert.action());
        assertEquals("Device 3", alert.deviceName());
        assertEquals(2, log.all().size());
    }

    @Test
    void doorAlertForUnknownUnlockFails() {
        assertThrows(IllegalStateException.class, () -> log.recordDoorAlert(3, 99, 10, T0));
    }

    @Test
    void keepsMoreThanTwoHundredEntries() {
        for (int i = 0; i < 250; i++) {
            record(1, ActivityType.TURN_ON, T0.plusSeconds(i));
        }
        assertEquals(250, log.all().size());
    }
}
