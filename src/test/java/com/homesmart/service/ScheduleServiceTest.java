package com.homesmart.service;

import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.dao.InMemoryScheduleDAO;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Schedule;
import com.homesmart.model.Thermostat;
import com.homesmart.model.UpcomingRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seed has the light (1) off, the thermostat (2) at 21 °C and the door (3) locked. 2026-11-18 is a Wednesday. */
class ScheduleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final Set<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);

    private InMemoryDeviceDAO devices;
    private InMemoryActivityLogDAO log;
    private InMemoryScheduleDAO schedules;
    private MutableClock clock;
    private ScheduleService service;

    private static final class MutableClock extends Clock {
        private Instant now;

        void set(LocalDateTime time) {
            now = time.toInstant(ZoneOffset.UTC);
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
        schedules = new InMemoryScheduleDAO();
        clock = new MutableClock();
        clock.set(TODAY.atTime(7, 30, 10));
        service = new ScheduleService(schedules, new DeviceService(devices, log), clock);
    }

    private Schedule add(String name, int deviceId, ActivityType type, Integer value, String time, Set<DayOfWeek> days,
                         boolean enabled, LocalDate lastRunOn) {
        return schedules.create(new Schedule(0, name, deviceId, type, value, LocalTime.parse(time), days, enabled,
                lastRunOn, null));
    }

    private Schedule lightOn(String time) {
        return add("Evening light", 1, ActivityType.TURN_ON, null, time, ALL_DAYS, true, TODAY.minusDays(1));
    }

    private boolean lightIsOn() {
        return ((Light) devices.findById(1).orElseThrow()).isOn();
    }

    private Schedule reload(Schedule schedule) {
        return schedules.find(schedule.id()).orElseThrow();
    }

    private void createFromForm(String name, String device, String type, String value, String runAt, String... days) {
        service.create(name, device, type, value, runAt, List.of(days));
    }

    private String createError(String name, String device, String type, String value, String runAt, String... days) {
        return assertThrows(IllegalArgumentException.class,
                () -> createFromForm(name, device, type, value, runAt, days)).getMessage();
    }

    // ---- runDue ----

    @Test
    void dueScheduleRunsOnceAndIsLogged() {
        Schedule schedule = lightOn("07:30");

        service.runDue();
        service.runDue();

        assertTrue(lightIsOn());
        assertEquals(TODAY, reload(schedule).lastRunOn());
        assertEquals("OK", reload(schedule).lastResult());
        List<ActivityEntry> entries = log.all();
        assertEquals(1, entries.size());
        assertEquals("Turned on (schedule: Evening light)", entries.get(0).action());
        assertEquals("Evening light", entries.get(0).detail());
    }

    @Test
    void doesNotRunBeforeItsTime() {
        lightOn("07:31");
        service.runDue();
        assertFalse(lightIsOn());
    }

    @Test
    void runsUpToTheEndOfTheWindowAndNotAfter() {
        lightOn("07:30");
        clock.set(TODAY.atTime(7, 34, 59));
        service.runDue();
        assertTrue(lightIsOn());

        devices.findById(1).map(d -> (Light) d).ifPresent(l -> l.setOn(false));
        Schedule late = add("Late", 1, ActivityType.TURN_ON, null, "07:00", ALL_DAYS, true, TODAY.minusDays(1));
        clock.set(TODAY.atTime(7, 5, 0));
        service.runDue();
        assertFalse(lightIsOn());
        assertEquals(TODAY.minusDays(1), reload(late).lastRunOn());
    }

    @Test
    void doesNotRunTwiceForTheSameDay() {
        Schedule schedule = add("Once", 1, ActivityType.TURN_ON, null, "07:30", ALL_DAYS, true, TODAY);
        service.runDue();
        assertFalse(lightIsOn());
        assertEquals(TODAY, reload(schedule).lastRunOn());
    }

    @Test
    void disabledScheduleDoesNotRun() {
        add("Off", 1, ActivityType.TURN_ON, null, "07:30", ALL_DAYS, false, TODAY.minusDays(1));
        service.runDue();
        assertFalse(lightIsOn());
    }

    @Test
    void doesNotRunOnADayThatIsNotChosen() {
        add("Mondays", 1, ActivityType.TURN_ON, null, "07:30", EnumSet.of(DayOfWeek.MONDAY), true, TODAY.minusDays(1));
        service.runDue();
        assertFalse(lightIsOn());
    }

    @Test
    void anOccurrenceBeforeMidnightStillRunsJustAfterIt() {
        Schedule schedule = add("Late lock", 3, ActivityType.LOCK, null, "23:58", ALL_DAYS, true, TODAY.minusDays(2));
        ((DoorLock) devices.findById(3).orElseThrow()).setLocked(false);
        clock.set(TODAY.atTime(0, 1, 0));

        service.runDue();

        assertTrue(((DoorLock) devices.findById(3).orElseThrow()).isLocked());
        assertEquals(TODAY.minusDays(1), reload(schedule).lastRunOn());
    }

    @Test
    void invalidTemperatureIsRecordedInTheResultAndNothingChanges() {
        Schedule schedule = add("Too hot", 2, ActivityType.SET_TEMPERATURE, 99, "07:30", ALL_DAYS, true, TODAY.minusDays(1));

        service.runDue();

        assertEquals("Temperature must be between 10 and 32 °C", reload(schedule).lastResult());
        assertEquals(21, ((Thermostat) devices.findById(2).orElseThrow()).getTemperature());
        assertEquals(0, log.all().size());
    }

    @Test
    void aFailingScheduleDoesNotStopTheNext() {
        InMemoryScheduleDAO flaky = new InMemoryScheduleDAO() {
            @Override
            public boolean claim(int scheduleId, LocalDate occurrence) {
                if (scheduleId == 1) {
                    throw new IllegalStateException("database is down");
                }
                return super.claim(scheduleId, occurrence);
            }
        };
        ScheduleService flakyService = new ScheduleService(flaky, new DeviceService(devices, log), clock);
        flaky.create(new Schedule(0, "First", 1, ActivityType.TURN_ON, null, LocalTime.of(7, 30), ALL_DAYS, true,
                TODAY.minusDays(1), null));
        flaky.create(new Schedule(0, "Second", 3, ActivityType.LOCK, null, LocalTime.of(7, 30), ALL_DAYS, true,
                TODAY.minusDays(1), null));
        ((DoorLock) devices.findById(3).orElseThrow()).setLocked(false);

        flakyService.runDue();

        assertFalse(lightIsOn());
        assertTrue(((DoorLock) devices.findById(3).orElseThrow()).isLocked());
    }

    @Test
    void aScheduleCreatedAfterItsTimeTodayDoesNotRunToday() {
        clock.set(TODAY.atTime(7, 31, 0));
        createFromForm("Missed", "1", "TURN_ON", null, "07:30", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        service.runDue();
        assertFalse(lightIsOn());
    }

    @Test
    void aScheduleCreatedBeforeItsTimeTodayRunsToday() {
        clock.set(TODAY.atTime(7, 31, 0));
        createFromForm("Soon", "1", "TURN_ON", null, "07:32", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        clock.set(TODAY.atTime(7, 32, 5));
        service.runDue();
        assertTrue(lightIsOn());
    }

    @Test
    void enablingSkipsAnOccurrenceThatAlreadyPassedToday() {
        Schedule schedule = add("Paused", 1, ActivityType.TURN_ON, null, "07:30", ALL_DAYS, false, TODAY.minusDays(3));
        schedules.recordResult(schedule.id(), "OK");

        service.setEnabled(schedule.id(), true);
        service.runDue();

        assertFalse(lightIsOn());
        assertTrue(reload(schedule).enabled());
        assertEquals(TODAY, reload(schedule).lastRunOn());
        assertNull(reload(schedule).lastResult());
    }

    @Test
    void disablingKeepsLastRunOn() {
        Schedule schedule = add("Keep", 1, ActivityType.TURN_ON, null, "07:30", ALL_DAYS, true, TODAY.minusDays(3));
        service.setEnabled(schedule.id(), false);
        assertFalse(reload(schedule).enabled());
        assertEquals(TODAY.minusDays(3), reload(schedule).lastRunOn());
    }

    @Test
    void unknownScheduleIdIsRejected() {
        assertEquals("No schedule with id 9", assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(9, true)).getMessage());
        assertEquals("No schedule with id 9", assertThrows(IllegalArgumentException.class,
                () -> service.delete(9)).getMessage());
    }

    @Test
    void deleteRemovesTheSchedule() {
        Schedule schedule = lightOn("08:00");
        service.delete(schedule.id());
        assertTrue(service.all().isEmpty());
    }

    // ---- create ----

    @Test
    void createStoresTheScheduleFromFormValues() {
        Schedule schedule = service.create("  Night  ", "2", "SET_TEMPERATURE", " 19 ", "22:15", List.of("MON", "WED"));

        assertEquals("Night", schedule.name());
        assertEquals(2, schedule.deviceId());
        assertEquals(ActivityType.SET_TEMPERATURE, schedule.type());
        assertEquals(19, schedule.value());
        assertEquals(LocalTime.of(22, 15), schedule.runAt());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), schedule.days());
        assertTrue(schedule.enabled());
        assertEquals(1, service.all().size());
    }

    @Test
    void createIgnoresAValueThatTheActionDoesNotUse() {
        assertNull(service.create("Light", "1", "TURN_ON", "abc", "08:00", List.of("MON")).value());
    }

    @Test
    void createRejectsUnlockingTheDoor() {
        assertEquals("A schedule cannot unlock the door.", createError("Open", "3", "UNLOCK", null, "08:00", "MON"));
    }

    @Test
    void createRejectsAnActionThatDoesNotFitTheDevice() {
        String message = "Choose an action that fits the device.";
        assertEquals(message, createError("A", "1", "LOCK", null, "08:00", "MON"));
        assertEquals(message, createError("A", "3", "TURN_ON", null, "08:00", "MON"));
        assertEquals(message, createError("A", "2", "TURN_OFF", null, "08:00", "MON"));
        assertEquals(message, createError("A", "1", "DOOR_LEFT_UNLOCKED", null, "08:00", "MON"));
        assertEquals(message, createError("A", "1", "NOPE", null, "08:00", "MON"));
        assertEquals(message, createError("A", "1", null, null, "08:00", "MON"));
    }

    @Test
    void createRejectsBadFormValues() {
        assertEquals("Enter a name of 1 to 50 characters.", createError("  ", "1", "TURN_ON", null, "08:00", "MON"));
        assertEquals("Enter a name of 1 to 50 characters.", createError("x".repeat(51), "1", "TURN_ON", null, "08:00", "MON"));
        assertEquals("Invalid device id", createError("A", "x", "TURN_ON", null, "08:00", "MON"));
        assertEquals("No device with id 77", createError("A", "77", "TURN_ON", null, "08:00", "MON"));
        assertEquals("Temperature must be a whole number", createError("A", "2", "SET_TEMPERATURE", "warm", "08:00", "MON"));
        assertEquals("Temperature must be between 10 and 32 °C", createError("A", "2", "SET_TEMPERATURE", "33", "08:00", "MON"));
        assertEquals("Enter a time as HH:mm.", createError("A", "1", "TURN_ON", null, "8am", "MON"));
        assertEquals("Enter a time as HH:mm.", createError("A", "1", "TURN_ON", null, "24:00", "MON"));
        assertEquals("Choose at least 1 day.", createError("A", "1", "TURN_ON", null, "08:00"));
        assertEquals("Choose at least 1 day.", createError("A", "1", "TURN_ON", null, "08:00", "MON", "MON"));
        assertEquals(0, service.all().size());
    }

    // ---- days ----

    @Test
    void parsesAndFormatsDays() {
        assertEquals(ALL_DAYS, Schedule.parseDays("DAILY"));
        assertEquals(EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), Schedule.parseDays("WED,MON"));
        assertEquals("DAILY", Schedule.formatDays(ALL_DAYS));
        assertEquals("MON,WED", Schedule.formatDays(EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)));
        assertEquals("SAT,SUN", Schedule.formatDays(Schedule.parseDays("SUN,SAT")));
    }

    @Test
    void rejectsEmptyUnknownAndDuplicateDays() {
        for (String text : new String[] {"", null, "FOO", "MON,MON", "MON,", "daily"}) {
            assertEquals("Choose at least 1 day.",
                    assertThrows(IllegalArgumentException.class, () -> Schedule.parseDays(text)).getMessage(), text);
        }
    }

    @Test
    void allSevenDaysAreStoredAsDaily() {
        createFromForm("All", "1", "TURN_ON", null, "08:00", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        assertEquals("DAILY", Schedule.formatDays(service.all().get(0).days()));
    }

    // ---- nextRuns ----

    @Test
    void nextRunsListsOccurrencesAfterNowInTimeOrder() {
        // now is Wed 07:30:10
        Schedule daily = add("Daily 07:00", 1, ActivityType.TURN_ON, null, "07:00", ALL_DAYS, true, TODAY);
        Schedule friday = add("Friday 06:00", 1, ActivityType.TURN_OFF, null, "06:00",
                EnumSet.of(DayOfWeek.FRIDAY), true, TODAY);
        add("Off", 1, ActivityType.TURN_OFF, null, "05:00", ALL_DAYS, false, TODAY);

        List<UpcomingRun> runs = service.nextRuns(3);

        assertEquals(List.of(daily.id(), friday.id(), daily.id()), runs.stream().map(r -> r.schedule().id()).toList());
        assertEquals(TODAY.plusDays(1).atTime(7, 0), runs.get(0).at());
        assertEquals(TODAY.plusDays(2).atTime(6, 0), runs.get(1).at());
        assertEquals(TODAY.plusDays(2).atTime(7, 0), runs.get(2).at());
    }

    @Test
    void nextRunsIncludesALaterTimeToday() {
        add("Later", 1, ActivityType.TURN_ON, null, "18:00", ALL_DAYS, true, TODAY);
        assertEquals(TODAY.atTime(18, 0), service.nextRuns(1).get(0).at());
    }

    @Test
    void nextRunsIsEmptyWithNoSchedules() {
        assertTrue(service.nextRuns(3).isEmpty());
    }
}
