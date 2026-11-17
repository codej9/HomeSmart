package com.homesmart.service;

import com.homesmart.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refuses sign-in attempts after repeated failures, kept in memory.
 * 5 failures in 15 minutes lock one username and address pair for 15 minutes.
 * 20 failures in 15 minutes lock one address the same way.
 */
public class LoginThrottle {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int KEY_LIMIT = 5;
    private static final int ADDRESS_LIMIT = 20;
    private static final int MAX_USERNAME_LENGTH = 50;

    private final Clock clock;
    private final Failures byKey = new Failures(KEY_LIMIT);
    private final Failures byAddress = new Failures(ADDRESS_LIMIT);

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isRefused(String username, String address) {
        Instant now = clock.instant();
        return byKey.isLocked(key(username, address), now) || byAddress.isLocked(address, now);
    }

    public synchronized void recordFailure(String username, String address) {
        Instant now = clock.instant();
        byKey.add(key(username, address), now);
        byAddress.add(address, now);
    }

    /** Clears the failures of this username and address pair. The address total stays. */
    public synchronized void recordSuccess(String username, String address) {
        byKey.clear(key(username, address));
    }

    /** A real username is at most 50 characters, so a longer one only needs its start to stay distinct. */
    private static String key(String username, String address) {
        String name = User.normalize(username);
        return (name.length() > MAX_USERNAME_LENGTH ? name.substring(0, MAX_USERNAME_LENGTH) : name) + "\n" + address;
    }

    private static final class Failures {
        private final int limit;
        private final Map<String, List<Instant>> times = new HashMap<>();
        private final Map<String, Instant> lockedUntil = new HashMap<>();

        Failures(int limit) {
            this.limit = limit;
        }

        boolean isLocked(String key, Instant now) {
            Instant until = lockedUntil.get(key);
            return until != null && now.isBefore(until);
        }

        void add(String key, Instant now) {
            Instant cutoff = now.minus(WINDOW);
            times.values().forEach(list -> list.removeIf(t -> t.isBefore(cutoff)));
            times.values().removeIf(List::isEmpty);
            lockedUntil.values().removeIf(until -> !now.isBefore(until));
            List<Instant> list = times.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(now);
            if (list.size() >= limit) {
                lockedUntil.put(key, now.plus(WINDOW));
            }
        }

        void clear(String key) {
            times.remove(key);
            lockedUntil.remove(key);
        }
    }
}
