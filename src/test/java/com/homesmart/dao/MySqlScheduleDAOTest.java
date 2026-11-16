package com.homesmart.dao;

import com.homesmart.model.ActivityType;
import com.homesmart.model.Schedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests: schedules are saved in the MySQL {@code schedules} table (homesmart.sql)
 * and a schedule can be claimed once per day.
 *
 * <p>Runs on the test database from {@code db.test.url}, never on the app's own database. Without it every
 * test is skipped (see {@link TestDatabase}). Each test starts and ends with an empty schedules table.
 */
class MySqlScheduleDAOTest {

    private static final int LIGHT = 1;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);

    private TestDatabase.Config database;
    private MySqlScheduleDAO dao;

    @BeforeEach
    void connect() throws SQLException {
        database = TestDatabase.config();
        dao = new MySqlScheduleDAO(database.url(), database.user(), database.password());
        dao.checkTable();
        clearSchedules();
    }

    @AfterEach
    void clearRows() throws SQLException {
        if (dao != null) {
            clearSchedules();
        }
    }

    private void clearSchedules() throws SQLException {
        try (Connection c = DriverManager.getConnection(database.url(), database.user(), database.password());
             Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM schedules");
        }
    }

    private Schedule add(String name, String time, boolean enabled, LocalDate lastRunOn) {
        return dao.create(new Schedule(0, name, LIGHT, ActivityType.TURN_ON, null, LocalTime.parse(time),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), enabled, lastRunOn, null));
    }

    @Test
    void savesAScheduleAndReadsItBack() {
        Schedule saved = dao.create(new Schedule(0, "Night", 2, ActivityType.SET_TEMPERATURE, 19, LocalTime.of(22, 15),
                EnumSet.allOf(DayOfWeek.class), true, DAY.minusDays(1), null));

        assertTrue(saved.id() > 0);
        Schedule read = dao.find(saved.id()).orElseThrow();
        assertEquals(saved, read);
        assertEquals("Night", read.name());
        assertEquals(19, read.value());
        assertEquals(LocalTime.of(22, 15), read.runAt());
        assertEquals(EnumSet.allOf(DayOfWeek.class), read.days());
        assertEquals(DAY.minusDays(1), read.lastRunOn());
        assertNull(read.lastResult());
    }

    @Test
    void aScheduleWithoutAValueKeepsItNull() {
        assertNull(dao.find(add("Light", "07:00", true, null).id()).orElseThrow().value());
    }

    @Test
    void listsByTimeOfDayThenId() {
        Schedule late = add("Late", "21:00", true, null);
        Schedule early = add("Early", "06:00", true, null);
        Schedule alsoLate = add("Also late", "21:00", true, null);

        assertEquals(List.of(early.id(), late.id(), alsoLate.id()), dao.all().stream().map(Schedule::id).toList());
    }

    @Test
    void enableAndDisableChangeTheRowAndReportAMissingId() {
        Schedule schedule = add("Toggle", "07:00", true, DAY.minusDays(1));

        assertTrue(dao.setEnabled(schedule.id(), false, DAY));
        assertFalse(dao.find(schedule.id()).orElseThrow().enabled());
        assertEquals(DAY, dao.find(schedule.id()).orElseThrow().lastRunOn());
        assertFalse(dao.setEnabled(999_999, true, DAY));
    }

    @Test
    void deleteRemovesTheRowAndReportsAMissingId() {
        Schedule schedule = add("Gone", "07:00", true, null);

        assertTrue(dao.delete(schedule.id()));
        assertTrue(dao.find(schedule.id()).isEmpty());
        assertFalse(dao.delete(schedule.id()));
    }

    @Test
    void recordResultIsSaved() {
        Schedule schedule = add("Result", "07:00", true, null);
        dao.recordResult(schedule.id(), "Temperature must be a whole number");
        assertEquals("Temperature must be a whole number", dao.find(schedule.id()).orElseThrow().lastResult());
    }

    @Test
    void anOccurrenceCanBeClaimedOnlyOnce() {
        Schedule schedule = add("Once", "07:00", true, DAY.minusDays(1));

        assertTrue(dao.claim(schedule.id(), DAY));
        assertFalse(dao.claim(schedule.id(), DAY));
        assertFalse(dao.claim(schedule.id(), DAY.minusDays(1)));
        assertEquals(DAY, dao.find(schedule.id()).orElseThrow().lastRunOn());
    }

    @Test
    void aScheduleThatNeverRanCanBeClaimed() {
        assertTrue(dao.claim(add("New", "07:00", true, null).id(), DAY));
    }

    @Test
    void aDisabledScheduleCannotBeClaimed() {
        Schedule schedule = add("Off", "07:00", false, DAY.minusDays(1));

        assertFalse(dao.claim(schedule.id(), DAY));
        assertEquals(DAY.minusDays(1), dao.find(schedule.id()).orElseThrow().lastRunOn());
    }

    @Test
    void aClaimAfterADisableChangesNothing() {
        Schedule schedule = add("Race", "07:00", true, DAY.minusDays(1));
        dao.setEnabled(schedule.id(), false, DAY.minusDays(1));

        assertFalse(dao.claim(schedule.id(), DAY));
    }

    @Test
    void aMissingScheduleCannotBeClaimed() {
        assertFalse(dao.claim(999_999, DAY));
    }

    @Test
    void ofTwoConcurrentClaimsExactlyOneWins() throws Exception {
        Schedule schedule = add("Race", "07:00", true, DAY.minusDays(1));
        MySqlScheduleDAO other = new MySqlScheduleDAO(database.url(), database.user(), database.password());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (MySqlScheduleDAO claimer : List.of(dao, other)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return claimer.claim(schedule.id(), DAY);
                }));
            }
            start.countDown();
            int wins = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    wins++;
                }
            }
            assertEquals(1, wins);
        } finally {
            pool.shutdownNow();
        }
    }
}
