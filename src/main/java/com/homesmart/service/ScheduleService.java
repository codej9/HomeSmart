package com.homesmart.service;

import com.homesmart.dao.ScheduleDAO;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Provenance;
import com.homesmart.model.Schedule;
import com.homesmart.model.Thermostat;
import com.homesmart.model.UpcomingRun;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates schedules and runs the ones that are due. A schedule runs a device command through
 * {@link DeviceService}, so the same validation and activity log apply. Times use the Clock's zone.
 *
 * <p>A schedule runs within 5 minutes after its time and never later, so a restart does not unlock or heat
 * the house hours late. Each occurrence runs at most once, because {@link ScheduleDAO#claim} marks it first.
 */
public class ScheduleService {

    static final Duration RUN_WINDOW = Duration.ofMinutes(5);
    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_RESULT_LENGTH = 200;

    private static final Logger LOGGER = Logger.getLogger(ScheduleService.class.getName());
    private static final DateTimeFormatter TIME_INPUT =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final String BAD_ACTION = "Choose an action that fits the device.";

    private final ScheduleDAO scheduleDAO;
    private final DeviceService deviceService;
    private final Clock clock;

    public ScheduleService(ScheduleDAO scheduleDAO, DeviceService deviceService, Clock clock) {
        this.scheduleDAO = scheduleDAO;
        this.deviceService = deviceService;
        this.clock = clock;
    }

    public List<Schedule> all() {
        return scheduleDAO.all();
    }

    /**
     * Saves a new schedule from the raw form values. It does not run for an occurrence that has already
     * passed today.
     *
     * @throws IllegalArgumentException with the text to show on the form
     */
    public Schedule create(String name, String deviceId, String activityType, String value, String runAt,
                           List<String> days) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Enter a name of 1 to 50 characters.");
        }
        Device device = deviceService.getDevice(parseDeviceId(deviceId));
        ActivityType type = parseType(activityType, device);
        Integer temperature = null;
        if (type == ActivityType.SET_TEMPERATURE) {
            temperature = DeviceService.parseTemperature(value);
            Thermostat.requireInRange(temperature);
        }
        LocalTime time = parseTime(runAt);
        Set<DayOfWeek> chosen = Schedule.parseDays(days == null ? "" : String.join(",", days));
        return scheduleDAO.create(new Schedule(0, trimmedName, device.getId(), type, temperature, time, chosen,
                true, lastRunOnForNew(time), null));
    }

    /** Enabling skips an occurrence that already passed today. Disabling keeps {@code last_run_on}. */
    public void setEnabled(int scheduleId, boolean enabled) {
        Schedule schedule = find(scheduleId);
        LocalDate lastRunOn = enabled ? lastRunOnForNew(schedule.runAt()) : schedule.lastRunOn();
        if (!scheduleDAO.setEnabled(scheduleId, enabled, lastRunOn)) {
            throw noSchedule(scheduleId);
        }
    }

    public void delete(int scheduleId) {
        if (!scheduleDAO.delete(scheduleId)) {
            throw noSchedule(scheduleId);
        }
    }

    /** The next {@code count} occurrences of the enabled schedules after now, in time order. */
    public List<UpcomingRun> nextRuns(int count) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UpcomingRun> runs = new ArrayList<>();
        for (Schedule schedule : scheduleDAO.all()) {
            if (!schedule.enabled()) {
                continue;
            }
            int found = 0;
            // 7 days per wanted run is always enough, because a schedule has at least 1 day a week
            for (int offset = 0; offset < 7 * count && found < count; offset++) {
                LocalDate date = now.toLocalDate().plusDays(offset);
                LocalDateTime at = date.atTime(schedule.runAt());
                if (schedule.days().contains(date.getDayOfWeek()) && at.isAfter(now)) {
                    runs.add(new UpcomingRun(schedule, at));
                    found++;
                }
            }
        }
        return runs.stream()
                .sorted(Comparator.comparing(UpcomingRun::at).thenComparingInt(run -> run.schedule().id()))
                .limit(count)
                .toList();
    }

    /** Runs every schedule that is due now. One failing schedule never stops the others. */
    public void runDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Schedule schedule : scheduleDAO.all()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                dueOccurrence(schedule, now).ifPresent(date -> runIfClaimed(schedule, date));
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Schedule " + schedule.id() + " failed", e);
            }
        }
    }

    /** The date of the occurrence to run now, checking today and yesterday so 23:58 still runs at 00:01. */
    private Optional<LocalDate> dueOccurrence(Schedule schedule, LocalDateTime now) {
        if (!schedule.enabled()) {
            return Optional.empty();
        }
        for (LocalDate date : new LocalDate[] {now.toLocalDate().minusDays(1), now.toLocalDate()}) {
            LocalDateTime at = date.atTime(schedule.runAt());
            boolean inWindow = !at.isAfter(now) && now.isBefore(at.plus(RUN_WINDOW));
            boolean notRun = schedule.lastRunOn() == null || schedule.lastRunOn().isBefore(date);
            if (schedule.days().contains(date.getDayOfWeek()) && inWindow && notRun) {
                return Optional.of(date);
            }
        }
        return Optional.empty();
    }

    private void runIfClaimed(Schedule schedule, LocalDate occurrence) {
        if (!scheduleDAO.claim(schedule.id(), occurrence)) {
            return;
        }
        String result;
        try {
            deviceService.execute(schedule.deviceId(), action(schedule.type()),
                    schedule.value() == null ? null : String.valueOf(schedule.value()),
                    Provenance.schedule(schedule.name()));
            result = "OK";
        } catch (IllegalArgumentException | IllegalStateException e) {
            result = e.getMessage();
        }
        scheduleDAO.recordResult(schedule.id(), result.length() > MAX_RESULT_LENGTH
                ? result.substring(0, MAX_RESULT_LENGTH) : result);
    }

    private static String action(ActivityType type) {
        return switch (type) {
            case TURN_ON -> "on";
            case TURN_OFF -> "off";
            case LOCK -> "lock";
            case SET_TEMPERATURE -> "set";
            default -> throw new IllegalArgumentException(BAD_ACTION);
        };
    }

    private LocalDate lastRunOnForNew(LocalTime runAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        return runAt.isAfter(now.toLocalTime()) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    private Schedule find(int scheduleId) {
        return scheduleDAO.find(scheduleId).orElseThrow(() -> noSchedule(scheduleId));
    }

    private static IllegalArgumentException noSchedule(int scheduleId) {
        return new IllegalArgumentException("No schedule with id " + scheduleId);
    }

    private static int parseDeviceId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid device id");
        }
    }

    /** Checks the action fits the device: on or off for a light, a setpoint for a thermostat, lock for a door. */
    private static ActivityType parseType(String text, Device device) {
        if (ActivityType.UNLOCK.name().equals(text)) {
            throw new IllegalArgumentException("A schedule cannot unlock the door.");
        }
        ActivityType type;
        try {
            type = ActivityType.valueOf(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(BAD_ACTION);
        }
        boolean fits = switch (type) {
            case TURN_ON, TURN_OFF -> device instanceof Light;
            case SET_TEMPERATURE -> device instanceof Thermostat;
            case LOCK -> device instanceof DoorLock;
            default -> false;
        };
        if (!fits) {
            throw new IllegalArgumentException(BAD_ACTION);
        }
        return type;
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text == null ? "" : text.trim(), TIME_INPUT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Enter a time as HH:mm.");
        }
    }
}
