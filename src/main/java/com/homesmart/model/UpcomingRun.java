package com.homesmart.model;

import java.time.LocalDateTime;

/** One coming occurrence of a schedule. */
public record UpcomingRun(Schedule schedule, LocalDateTime at) {
}
