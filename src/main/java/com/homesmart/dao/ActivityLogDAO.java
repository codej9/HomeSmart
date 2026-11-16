package com.homesmart.dao;

import com.homesmart.service.CommandResult;

import java.util.List;

/**
 * Storage contract for the activity log: one entry per successful device command.
 * Reads and writes throw {@link IllegalStateException} when the storage cannot be reached.
 */
public interface ActivityLogDAO {

    /** Saves one command. The device must exist. */
    void record(CommandResult result);

    /** The latest {@code limit} entries, newest first. */
    List<CommandResult> recent(int limit);

    /** Every entry the log keeps, newest first. */
    List<CommandResult> all();
}
