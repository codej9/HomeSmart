package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Storage contract for the activity log: one entry per device state change.
 * Methods throw {@link IllegalStateException} when the storage cannot be reached.
 */
public interface ActivityLogDAO {

    /** Saves one command. The device must exist. Returns the new log id. */
    long record(CommandResult result, Provenance provenance);

    /**
     * Saves a "left unlocked" alert for the unlock row {@code unlockLogId}.
     * Returns false when that unlock already has an alert.
     */
    boolean recordDoorAlert(int deviceId, long unlockLogId, int minutes, LocalDateTime at);

    /** The latest {@code limit} entries, newest first. */
    List<ActivityEntry> recent(int limit);

    /** Every entry, newest first. */
    List<ActivityEntry> all();

    /** The newest entry of one of {@code types} for the device. */
    Optional<ActivityEntry> latestOfTypes(int deviceId, Set<ActivityType> types);
}
