package com.homesmart.dao;

import com.homesmart.model.ActivityEntry;
import com.homesmart.model.Provenance;
import com.homesmart.service.CommandResult;

import java.util.List;

/**
 * Storage contract for the activity log: one entry per device state change.
 * Methods throw {@link IllegalStateException} when the storage cannot be reached.
 */
public interface ActivityLogDAO {

    /** Saves one command. The device must exist. Returns the new log id. */
    long record(CommandResult result, Provenance provenance);

    /** The latest {@code limit} entries, newest first. */
    List<ActivityEntry> recent(int limit);

    /** Every entry, newest first. */
    List<ActivityEntry> all();
}
