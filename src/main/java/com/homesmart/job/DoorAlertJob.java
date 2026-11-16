package com.homesmart.job;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.service.DeviceService;
import com.homesmart.service.SecurityAlerts;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes one "left unlocked" alert to the activity log for each unlock that lasts past the threshold.
 * The unique {@code ref_log_id} column stops a second alert for the same unlock, even after a restart.
 */
public class DoorAlertJob implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(DoorAlertJob.class.getName());

    private final DeviceService deviceService;
    private final ActivityLogDAO activityLog;
    private final Clock clock;
    private final Duration threshold;

    public DoorAlertJob(DeviceService deviceService, ActivityLogDAO activityLog, Clock clock, Duration threshold) {
        this.deviceService = deviceService;
        this.activityLog = activityLog;
        this.clock = clock;
        this.threshold = threshold;
    }

    @Override
    public void run() {
        try {
            for (Device device : deviceService.getAllDevices()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (device instanceof DoorLock door && !door.isLocked()) {
                    alertIfOverdue(door);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Door alert check failed", e);
        }
    }

    private void alertIfOverdue(DoorLock door) {
        Optional<ActivityEntry> latest = activityLog.latestOfTypes(door.getId(), SecurityAlerts.LOCK_EVENTS);
        LocalDateTime now = LocalDateTime.now(clock);
        if (latest.isPresent() && latest.get().type() == ActivityType.UNLOCK
                && !latest.get().timestamp().plus(threshold).isAfter(now)) {
            activityLog.recordDoorAlert(door.getId(), latest.get().logId(), (int) threshold.toMinutes(), now);
        }
    }
}
