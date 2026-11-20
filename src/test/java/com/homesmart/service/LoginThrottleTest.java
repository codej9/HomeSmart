package com.homesmart.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-18T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    private void fail(String username, String address, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(username, address);
        }
    }

    @Test
    void refusesTheFifthFailureOfOneUsernameAndAddress() {
        fail("matt", "10.0.0.1", 4);
        assertFalse(throttle.isRefused("matt", "10.0.0.1"));

        fail("matt", "10.0.0.1", 1);
        assertTrue(throttle.isRefused("matt", "10.0.0.1"));
        assertFalse(throttle.isRefused("matt", "10.0.0.2"));
        assertFalse(throttle.isRefused("other", "10.0.0.1"));
    }

    @Test
    void keyIgnoresCaseAndSpaces() {
        fail(" Matt ", "10.0.0.1", 5);

        assertTrue(throttle.isRefused("matt", "10.0.0.1"));
    }

    @Test
    void refusalLastsFifteenMinutes() {
        fail("matt", "10.0.0.1", 5);

        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertTrue(throttle.isRefused("matt", "10.0.0.1"));

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertFalse(throttle.isRefused("matt", "10.0.0.1"));
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        fail("matt", "10.0.0.1", 4);
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        fail("matt", "10.0.0.1", 1);

        assertFalse(throttle.isRefused("matt", "10.0.0.1"));
    }

    @Test
    void refusesAnAddressAfterTwentyFailuresAcrossUsernames() {
        for (int i = 0; i < 19; i++) {
            throttle.recordFailure("user" + i, "10.0.0.1");
        }
        assertFalse(throttle.isRefused("fresh", "10.0.0.1"));

        throttle.recordFailure("user19", "10.0.0.1");
        assertTrue(throttle.isRefused("fresh", "10.0.0.1"));
        assertFalse(throttle.isRefused("fresh", "10.0.0.2"));
    }

    @Test
    void successClearsOnlyItsOwnUsernameAndAddress() {
        fail("matt", "10.0.0.1", 4);
        fail("matt", "10.0.0.2", 4);

        throttle.recordSuccess("matt", "10.0.0.1");
        fail("matt", "10.0.0.1", 1);
        fail("matt", "10.0.0.2", 1);

        assertFalse(throttle.isRefused("matt", "10.0.0.1"));
        assertTrue(throttle.isRefused("matt", "10.0.0.2"));
    }
}
