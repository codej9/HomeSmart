package com.homesmart.service;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Estimates from the activity log, not from measurements.
 *
 * <p>The tracked window of a device starts at the period start when an earlier row tells the state there.
 * Otherwise it starts at the device's first row in the period, and the time before it is left out.
 */
public final class EnergyEstimator {

    /** Power of the assumed LED bulb. It gives the light of a 60 W incandescent bulb (NRCan, 2025b). */
    public static final double LIGHT_WATTS = 9;

    public static final Set<ActivityType> LIGHT_TYPES = Set.of(ActivityType.TURN_ON, ActivityType.TURN_OFF);
    public static final Set<ActivityType> THERMOSTAT_TYPES = Set.of(ActivityType.SET_TEMPERATURE);

    public record LightEstimate(double wattHours, LocalDateTime trackedStart) {
    }

    public record SetpointAverage(double averageCelsius, Duration tracked, LocalDateTime trackedStart) {
    }

    private EnergyEstimator() {
    }

    /**
     * Watt-hours a light used in the period.
     *
     * @param baseline the newest {@code TURN_ON} or {@code TURN_OFF} row at or before the period start
     * @param changes  the rows after the period start, up to {@code now}, oldest first
     */
    public static Optional<LightEstimate> light(Optional<ActivityEntry> baseline, List<ActivityEntry> changes,
                                                LocalDateTime periodStart, LocalDateTime now) {
        LocalDateTime start = trackedStart(baseline, changes, periodStart);
        if (start == null) {
            return Optional.empty();
        }
        boolean on = baseline.map(e -> e.type() == ActivityType.TURN_ON).orElse(false);
        LocalDateTime cursor = start;
        Duration onTime = Duration.ZERO;
        for (ActivityEntry change : changes) {
            if (on) {
                onTime = onTime.plus(Duration.between(cursor, change.timestamp()));
            }
            on = change.type() == ActivityType.TURN_ON;
            cursor = change.timestamp();
        }
        if (on) {
            onTime = onTime.plus(Duration.between(cursor, now));
        }
        return Optional.of(new LightEstimate(onTime.toMillis() / 3_600_000.0 * LIGHT_WATTS, start));
    }

    /**
     * Time-weighted average setpoint of a thermostat in the period.
     *
     * @param baseline the newest {@code SET_TEMPERATURE} row at or before the period start
     * @param changes  the rows after the period start, up to {@code now}, oldest first
     */
    public static Optional<SetpointAverage> thermostat(Optional<ActivityEntry> baseline, List<ActivityEntry> changes,
                                                       LocalDateTime periodStart, LocalDateTime now) {
        LocalDateTime start = trackedStart(baseline, changes, periodStart);
        if (start == null) {
            return Optional.empty();
        }
        int setpoint = baseline.isPresent() ? baseline.get().value() : changes.get(0).value();
        LocalDateTime cursor = start;
        double degreeMillis = 0;
        for (ActivityEntry change : changes) {
            degreeMillis += (double) setpoint * Duration.between(cursor, change.timestamp()).toMillis();
            setpoint = change.value();
            cursor = change.timestamp();
        }
        degreeMillis += (double) setpoint * Duration.between(cursor, now).toMillis();
        Duration tracked = Duration.between(start, now);
        double average = tracked.isZero() ? setpoint : degreeMillis / tracked.toMillis();
        return Optional.of(new SetpointAverage(average, tracked, start));
    }

    /** The period start with a baseline, the first change without one, or null with neither. */
    private static LocalDateTime trackedStart(Optional<ActivityEntry> baseline, List<ActivityEntry> changes,
                                              LocalDateTime periodStart) {
        if (baseline.isPresent()) {
            return periodStart;
        }
        return changes.isEmpty() ? null : changes.get(0).timestamp();
    }

    /** Watt-hours with 1 decimal, for example "4.5 Wh". */
    public static String formatWattHours(double wattHours) {
        return String.format(Locale.ROOT, "%.1f Wh", wattHours);
    }

    /** Hours and minutes, for example "4 h 10 min", or "35 min" below 1 hour. */
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
    }
}
