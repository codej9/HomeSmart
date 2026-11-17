package com.homesmart.dao;

import com.homesmart.model.Schedule;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Keeps schedules in memory in sample mode. They are lost when the server restarts. */
public class InMemoryScheduleDAO implements ScheduleDAO {

    private final Map<Integer, Schedule> schedules = new LinkedHashMap<>();
    private int lastId;

    @Override
    public synchronized List<Schedule> all() {
        return schedules.values().stream()
                .sorted(Comparator.comparing(Schedule::runAt).thenComparingInt(Schedule::id))
                .toList();
    }

    @Override
    public synchronized Optional<Schedule> find(int scheduleId) {
        return Optional.ofNullable(schedules.get(scheduleId));
    }

    @Override
    public synchronized Schedule create(Schedule schedule) {
        Schedule stored = new Schedule(++lastId, schedule.name(), schedule.deviceId(), schedule.type(),
                schedule.value(), schedule.runAt(), schedule.days(), schedule.enabled(), schedule.lastRunOn(),
                schedule.lastResult());
        schedules.put(stored.id(), stored);
        return stored;
    }

    @Override
    public synchronized boolean setEnabled(int scheduleId, boolean enabled, LocalDate lastRunOn) {
        return replace(scheduleId, s -> new Schedule(s.id(), s.name(), s.deviceId(), s.type(), s.value(), s.runAt(),
                s.days(), enabled, lastRunOn, enabled ? null : s.lastResult()));
    }

    @Override
    public synchronized boolean delete(int scheduleId) {
        return schedules.remove(scheduleId) != null;
    }

    @Override
    public synchronized boolean claim(int scheduleId, LocalDate occurrence) {
        Schedule schedule = schedules.get(scheduleId);
        if (schedule == null || !schedule.enabled()
                || (schedule.lastRunOn() != null && !schedule.lastRunOn().isBefore(occurrence))) {
            return false;
        }
        return replace(scheduleId, s -> new Schedule(s.id(), s.name(), s.deviceId(), s.type(), s.value(), s.runAt(),
                s.days(), true, occurrence, s.lastResult()));
    }

    @Override
    public synchronized void recordResult(int scheduleId, String result) {
        replace(scheduleId, s -> new Schedule(s.id(), s.name(), s.deviceId(), s.type(), s.value(), s.runAt(),
                s.days(), s.enabled(), s.lastRunOn(), result));
    }

    private boolean replace(int scheduleId, UnaryOperator<Schedule> change) {
        Schedule schedule = schedules.get(scheduleId);
        if (schedule == null) {
            return false;
        }
        schedules.put(scheduleId, change.apply(schedule));
        return true;
    }
}
