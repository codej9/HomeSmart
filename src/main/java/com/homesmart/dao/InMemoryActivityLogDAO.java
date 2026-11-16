package com.homesmart.dao;

import com.homesmart.service.CommandResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the activity log in memory when the MySQL {@code activity_log} table is not available.
 * History clears on restart; the cap stops it growing forever.
 */
public class InMemoryActivityLogDAO implements ActivityLogDAO {

    static final int MAX_ENTRIES = 200;

    private final Deque<CommandResult> entries = new ArrayDeque<>();

    @Override
    public synchronized void record(CommandResult result) {
        entries.addFirst(result);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    @Override
    public synchronized List<CommandResult> recent(int limit) {
        return entries.stream().limit(limit).toList();
    }

    @Override
    public synchronized List<CommandResult> all() {
        return List.copyOf(entries);
    }
}
