package com.homesmart.model;

import java.time.LocalDateTime;

/** One row of the activity log. {@code deviceName} is read from the device, not stored in the log. */
public record ActivityEntry(long logId, int deviceId, String deviceName, String action, ActivityType type,
                            Integer value, ActivitySource source, String detail, Long refLogId,
                            LocalDateTime timestamp) {
}
