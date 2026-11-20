package com.homesmart.service;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivitySource;
import com.homesmart.model.ActivityType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyEstimatorTest {

    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2026, 11, 18, 0, 0);
    private static final LocalDateTime NOON = MIDNIGHT.plusHours(12);

    private static ActivityEntry entry(ActivityType type, Integer value, LocalDateTime at) {
        return new ActivityEntry(1, 1, "Device", type.name(), type, value, ActivitySource.USER, null, null, at);
    }

    private static ActivityEntry on(LocalDateTime at) {
        return entry(ActivityType.TURN_ON, null, at);
    }

    private static ActivityEntry off(LocalDateTime at) {
        return entry(ActivityType.TURN_OFF, null, at);
    }

    private static ActivityEntry setpoint(int celsius, LocalDateTime at) {
        return entry(ActivityType.SET_TEMPERATURE, celsius, at);
    }

    @Test
    void lightIntervalThatCrossesThePeriodStartCountsOnlyTheTimeInsideThePeriod() {
        // On since 22:00 the day before, off at 02:00: 2 h inside the period
        var estimate = EnergyEstimator.light(Optional.of(on(MIDNIGHT.minusHours(2))),
                List.of(off(MIDNIGHT.plusHours(2))), MIDNIGHT, NOON).orElseThrow();

        assertEquals(18.0, estimate.wattHours(), 1e-9);
        assertEquals(MIDNIGHT, estimate.trackedStart());
    }

    @Test
    void lightStillOnRunsUntilNow() {
        var estimate = EnergyEstimator.light(Optional.empty(), List.of(on(MIDNIGHT.plusHours(10))), MIDNIGHT, NOON)
                .orElseThrow();
        assertEquals(18.0, estimate.wattHours(), 1e-9);
    }

    @Test
    void lightAddsSeveralIntervals() {
        var changes = List.of(on(MIDNIGHT.plusHours(1)), off(MIDNIGHT.plusHours(2)),
                on(MIDNIGHT.plusHours(5)), off(MIDNIGHT.plusHours(5).plusMinutes(30)));
        var estimate = EnergyEstimator.light(Optional.empty(), changes, MIDNIGHT, NOON).orElseThrow();
        assertEquals(13.5, estimate.wattHours(), 1e-9);
    }

    @Test
    void lightWithABaselineThatIsOffCountsNothingUntilItTurnsOn() {
        var estimate = EnergyEstimator.light(Optional.of(off(MIDNIGHT.minusHours(1))),
                List.of(on(MIDNIGHT.plusHours(11))), MIDNIGHT, NOON).orElseThrow();
        assertEquals(9.0, estimate.wattHours(), 1e-9);
    }

    @Test
    void firstEverRowInsideThePeriodSetsTheTrackedStart() {
        LocalDateTime first = MIDNIGHT.plusHours(8);
        var estimate = EnergyEstimator.light(Optional.empty(), List.of(on(first), off(first.plusHours(1))),
                MIDNIGHT, NOON).orElseThrow();

        assertEquals(first, estimate.trackedStart());
        assertEquals(9.0, estimate.wattHours(), 1e-9);
    }

    @Test
    void noRowsGiveNoEstimate() {
        assertTrue(EnergyEstimator.light(Optional.empty(), List.of(), MIDNIGHT, NOON).isEmpty());
        assertTrue(EnergyEstimator.thermostat(Optional.empty(), List.of(), MIDNIGHT, NOON).isEmpty());
    }

    @Test
    void thermostatAveragesTheSetpointOverTime() {
        // 20 for 2 h, 22 for 4 h, 18 for 2 h of the 8 h window that starts at 04:00
        LocalDateTime first = MIDNIGHT.plusHours(4);
        var changes = List.of(setpoint(20, first), setpoint(22, first.plusHours(2)), setpoint(18, first.plusHours(6)));

        var average = EnergyEstimator.thermostat(Optional.empty(), changes, MIDNIGHT, first.plusHours(8)).orElseThrow();

        assertEquals((20 * 2 + 22 * 4 + 18 * 2) / 8.0, average.averageCelsius(), 1e-9);
        assertEquals(Duration.ofHours(8), average.tracked());
        assertEquals(first, average.trackedStart());
    }

    @Test
    void thermostatBaselineComesFromARowBeforeThePeriod() {
        var average = EnergyEstimator.thermostat(Optional.of(setpoint(24, MIDNIGHT.minusDays(2))),
                List.of(setpoint(20, MIDNIGHT.plusHours(6))), MIDNIGHT, NOON).orElseThrow();

        assertEquals((24 * 6 + 20 * 6) / 12.0, average.averageCelsius(), 1e-9);
        assertEquals(MIDNIGHT, average.trackedStart());
        assertEquals(Duration.ofHours(12), average.tracked());
    }

    @Test
    void thermostatChangeAtNowGivesThatSetpoint() {
        var average = EnergyEstimator.thermostat(Optional.empty(), List.of(setpoint(19, NOON)), MIDNIGHT, NOON)
                .orElseThrow();
        assertEquals(19.0, average.averageCelsius(), 1e-9);
        assertEquals(Duration.ZERO, average.tracked());
    }

    @Test
    void formatsWattHoursAndDurations() {
        assertEquals("4.5 Wh", EnergyEstimator.formatWattHours(4.5));
        assertEquals("0.0 Wh", EnergyEstimator.formatWattHours(0));
        assertEquals("4 h 10 min", EnergyEstimator.formatDuration(Duration.ofMinutes(250)));
        assertEquals("35 min", EnergyEstimator.formatDuration(Duration.ofMinutes(35)));
        assertEquals("0 min", EnergyEstimator.formatDuration(Duration.ZERO));
    }
}
