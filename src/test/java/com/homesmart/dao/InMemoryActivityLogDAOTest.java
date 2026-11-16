package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                ActivityType.SET_TEMPERATURE, 20, T0), new Provenance(ActivitySource.SCHEDULE, "Night"));
        ActivityEntry entry = log.all().get(0);
        assertEquals(20, entry.value());
        assertEquals(ActivitySource.SCHEDULE, entry.source());
        assertEquals("Night", entry.detail());
    }

    @Test
    void keepsMoreThanTwoHundredEntries() {
        for (int i = 0; i < 250; i++) {
            record(1, ActivityType.TURN_ON, T0.plusSeconds(i));
        }
        assertEquals(250, log.all().size());
    }
}
