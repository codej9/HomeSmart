package com.homesmart.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A command that runs at {@code runAt} on the chosen days. {@code lastRunOn} is the last day the schedule
 * ran or was skipped, and {@code lastResult} is "OK" or the error of that run.
 */
public record Schedule(int id, String name, int deviceId, ActivityType type, Integer value, LocalTime runAt,
                       Set<DayOfWeek> days, boolean enabled, LocalDate lastRunOn, String lastResult) {

    private static final String DAILY = "DAILY";
    private static final String CHOOSE_A_DAY = "Choose at least 1 day.";

    /** Reads the {@code days} column: {@code DAILY}, or day tokens such as {@code MON,WED}. */
    public static Set<DayOfWeek> parseDays(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(CHOOSE_A_DAY);
        }
        if (text.equals(DAILY)) {
            return EnumSet.allOf(DayOfWeek.class);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : text.split(",", -1)) {
            DayOfWeek day = fromToken(token);
            if (day == null || !days.add(day)) {
                throw new IllegalArgumentException(CHOOSE_A_DAY);
            }
        }
        return days;
    }

    /** Writes the {@code days} column: {@code DAILY}, or tokens in week order. */
    public static String formatDays(Set<DayOfWeek> days) {
        if (days.size() == DayOfWeek.values().length) {
            return DAILY;
        }
        return EnumSet.copyOf(days).stream().map(Schedule::token).collect(Collectors.joining(","));
    }

    private static String token(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    private static DayOfWeek fromToken(String token) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (token(day).equals(token)) {
                return day;
            }
        }
        return null;
    }

    /** The days for the page, for example "Every day" or "Mon, Wed, Fri". */
    public String daysText() {
        if (days.size() == DayOfWeek.values().length) {
            return "Every day";
        }
        List<String> names = new ArrayList<>();
        for (DayOfWeek day : EnumSet.copyOf(days)) {
            names.add(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        }
        return String.join(", ", names);
    }

    /** What the schedule does, for example "Turn on" or "Set to 22 °C". */
    public String actionText() {
        return switch (type) {
            case TURN_ON -> "Turn on";
            case TURN_OFF -> "Turn off";
            case LOCK -> "Lock";
            case SET_TEMPERATURE -> "Set to " + value + " °C";
            default -> type.name();
        };
    }
}
