package com.homesmart.job;

import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoorAlertJobTest {

    private static final int DOOR = 3;
    private static final Duration THRESHOLD = Duration.ofMinutes(10);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 18, 10, 0);

    private InMemoryDeviceDAO devices;
    private InMemoryActivityLogDAO log;
    private MutableClock clock;
    private DoorAlertJob job;

    /** A clock the test moves forward. */
    private static final class MutableClock extends Clock {
        private Instant now = T0.toInstant(ZoneOffset.UTC);

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceDAO();
        log = new InMemoryActivityLogDAO();
        clock = new MutableClock();
        job = newJob(log);
    }

    private DoorAlertJob newJob(InMemoryActivityLogDAO activityLog) {
        return new DoorAlertJob(new DeviceService(devices, activityLog), activityLog, clock, THRESHOLD);
    }

    private void setLocked(boolean locked) {
        DoorLock door = (DoorLock) devices.findById(DOOR).orElseThrow();
        door.setLocked(locked);
        devices.update(door);
    }

    private void logEvent(ActivityType type, LocalDateTime at) {
        log.record(new CommandResult(DOOR, "Front Door Lock", type.name(), type, null, at), Provenance.USER);
    }

    private List<ActivityEntry> alerts() {
        return log.all().stream().filter(e -> e.type() == ActivityType.DOOR_LEFT_UNLOCKED).toList();
    }

    @Test
    void writesNothingBeforeTheThreshold() {
        setLocked(false);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(Duration.ofMinutes(9));
        job.run();
        assertEquals(0, alerts().size());
    }

    @Test
    void writesOneAlertPerUnlockAndNoSecondOneOnTheNextTick() {
        setLocked(false);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(THRESHOLD);

        job.run();
        clock.advance(Duration.ofSeconds(30));
        job.run();

        assertEquals(1, alerts().size());
        assertEquals("Security alert: left unlocked for 10 min", alerts().get(0).action());
        assertEquals(LocalDateTime.of(2026, 11, 18, 10, 10), alerts().get(0).timestamp());
    }

    @Test
    void aNewUnlockGetsItsOwnAlert() {
        setLocked(false);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(THRESHOLD);
        job.run();

        logEvent(ActivityType.LOCK, T0.plusMinutes(11));
        logEvent(ActivityType.UNLOCK, T0.plusMinutes(20));
        clock.advance(Duration.ofMinutes(20));
        job.run();

        assertEquals(2, alerts().size());
    }

    @Test
    void lockedDoorGetsNoAlertEvenWhenItsLatestRowIsUnlock() {
        setLocked(true);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(Duration.ofHours(1));
        job.run();
        assertEquals(0, alerts().size());
    }

    @Test
    void unlockedDoorWithNoUnlockRowGetsNoAlert() {
        setLocked(false);
        clock.advance(Duration.ofHours(1));
        job.run();
        assertEquals(0, alerts().size());
    }

    @Test
    void aNewJobInstanceWritesNoDuplicate() {
        setLocked(false);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(THRESHOLD);
        job.run();

        newJob(log).run();

        assertEquals(1, alerts().size());
    }

    @Test
    void aFailingTickDoesNotStopTheNextOne() {
        setLocked(false);
        logEvent(ActivityType.UNLOCK, T0);
        clock.advance(THRESHOLD);
        boolean[] failing = {true};
        InMemoryActivityLogDAO flaky = new InMemoryActivityLogDAO() {
            @Override
            public boolean recordDoorAlert(int deviceId, long unlockLogId, int minutes, LocalDateTime at) {
                if (failing[0]) {
                    throw new IllegalStateException("database is down");
                }
                return super.recordDoorAlert(deviceId, unlockLogId, minutes, at);
            }
        };
        flaky.record(new CommandResult(DOOR, "Front Door Lock", "Unlocked", ActivityType.UNLOCK, null, T0), Provenance.USER);
        DoorAlertJob flakyJob = newJob(flaky);

        flakyJob.run();
        failing[0] = false;
        flakyJob.run();

        assertEquals(1, flaky.all().stream().filter(e -> e.type() == ActivityType.DOOR_LEFT_UNLOCKED).count());
    }
}
