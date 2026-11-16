package com.homesmart.dao;

import com.homesmart.model.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Storage contract for schedules. Methods throw {@link IllegalStateException}
 * when the storage cannot be reached.
 */
public interface ScheduleDAO {

    /** Every schedule, by time of day, then id. */
    List<Schedule> all();

    Optional<Schedule> find(int scheduleId);

    /** Saves a new schedule and returns it with its new id. */
    Schedule create(Schedule schedule);

    /** Sets {@code enabled} and {@code last_run_on}. Returns false when no schedule has that id. */
    boolean setEnabled(int scheduleId, boolean enabled, LocalDate lastRunOn);

    /** Returns false when no schedule has that id. */
    boolean delete(int scheduleId);

    /**
     * Marks the occurrence on {@code occurrence} as taken. Returns true only when the schedule exists, is
     * enabled, and has not run on or after that day, so a schedule runs at most once per occurrence.
     */
    boolean claim(int scheduleId, LocalDate occurrence);

    void recordResult(int scheduleId, String result);
}
