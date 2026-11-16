package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Keeps the activity log in memory when the MySQL {@code activity_log} table is not available.
 * History clears on restart.
 */
public class InMemoryActivityLogDAO implements ActivityLogDAO {

    private static final Comparator<ActivityEntry> OLDEST_FIRST =
            Comparator.comparing(ActivityEntry::timestamp).thenComparingLong(ActivityEntry::logId);

    private final List<ActivityEntry> entries = new ArrayList<>();
    private long lastLogId;

    @Override
    public synchronized long record(CommandResult result, Provenance provenance) {
        return add(result.deviceId(), result.deviceName(), result.action(), result.type(), result.value(),
                provenance.source(), provenance.detail(), null, result.timestamp()).logId();
    }

    @Override
    public synchronized boolean recordDoorAlert(int deviceId, long unlockLogId, int minutes, LocalDateTime at) {
        ActivityEntry unlock = entries.stream().filter(e -> e.logId() == unlockLogId).findFirst()
                .orElseThrow(() -> new IllegalStateException("No log entry with id " + unlockLogId));
        if (entries.stream().anyMatch(e -> e.refLogId() != null && e.refLogId() == unlockLogId)) {
            return false;
        }
        add(deviceId, unlock.deviceName(), "Security alert: left unlocked for " + minutes + " min",
                ActivityType.DOOR_LEFT_UNLOCKED, null, ActivitySource.SYSTEM, null, unlockLogId, at);
        return true;
    }

    @Override
    public synchronized List<ActivityEntry> recent(int limit) {
        return newestFirst(e -> true).stream().limit(limit).toList();
    }

    @Override
    public synchronized List<ActivityEntry> all() {
        return newestFirst(e -> true);
    }

    @Override
    public synchronized Optional<ActivityEntry> latestOfTypes(int deviceId, Set<ActivityType> types) {
        return newestFirst(e -> e.deviceId() == deviceId && types.contains(e.type())).stream().findFirst();
    }

    @Override
    public synchronized Optional<ActivityEntry> latestOfTypesAtOrBefore(
            int deviceId, Set<ActivityType> types, LocalDateTime at) {
        return newestFirst(e -> e.deviceId() == deviceId && types.contains(e.type()) && !e.timestamp().isAfter(at))
                .stream().findFirst();
    }

    @Override
    public synchronized List<ActivityEntry> ofTypesBetween(
            int deviceId, Set<ActivityType> types, LocalDateTime from, LocalDateTime to) {
        return entries.stream()
                .filter(e -> e.deviceId() == deviceId && types.contains(e.type())
                        && e.timestamp().isAfter(from) && !e.timestamp().isAfter(to))
                .sorted(OLDEST_FIRST)
                .toList();
    }

    private ActivityEntry add(int deviceId, String deviceName, String action, ActivityType type, Integer value,
                              ActivitySource source, String detail, Long refLogId, LocalDateTime timestamp) {
        ActivityEntry entry = new ActivityEntry(++lastLogId, deviceId, deviceName, action, type, value,
                source, detail, refLogId, timestamp);
        entries.add(entry);
        return entry;
    }

    private List<ActivityEntry> newestFirst(Predicate<ActivityEntry> filter) {
        return entries.stream().filter(filter).sorted(OLDEST_FIRST.reversed()).toList();
    }
}
