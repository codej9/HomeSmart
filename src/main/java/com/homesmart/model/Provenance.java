package com.homesmart.model;

/** Where a command came from, with the schedule name when a schedule ran it. */
public record Provenance(ActivitySource source, String detail) {

    public static final Provenance USER = new Provenance(ActivitySource.USER, null);

    /** The text to log for {@code action}, e.g. "Turned on (schedule: Evening light)". */
    public String describe(String action) {
        return source == ActivitySource.SCHEDULE ? action + " (schedule: " + detail + ")" : action;
    }
}
