package com.homesmart.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityLogTest {

    private final ActivityLog log = new ActivityLog();

    @Test
    void recordedCommandsAreNewestFirstAndRecentIsLimited() {
        log.record(new CommandResult("Living Room Light", "Turned on"));
        log.record(new CommandResult("Front Door Lock", "Unlocked"));
        log.record(new CommandResult("Thermostat", "Set temperature to 22 °C"));

        List<CommandResult> recent = log.recent(2);
        assertEquals(2, recent.size());
        assertEquals("Set temperature to 22 °C", recent.get(0).action());
        assertEquals("Unlocked", recent.get(1).action());
        assertEquals(3, log.all().size());
    }

    @Test
    void oldestEntriesDropPastTheLimit() {
        for (int i = 0; i <= ActivityLog.MAX_ENTRIES; i++) {
            log.record(new CommandResult("Light " + i, "Turned on"));
        }

        List<CommandResult> entries = log.all();
        assertEquals(ActivityLog.MAX_ENTRIES, entries.size());
        assertEquals("Light " + ActivityLog.MAX_ENTRIES, entries.get(0).deviceName());
        assertEquals("Light 1", entries.get(entries.size() - 1).deviceName());
    }
}
