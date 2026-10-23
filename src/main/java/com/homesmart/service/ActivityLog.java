package com.homesmart.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** History of device commands, newest first. */
public class ActivityLog {

    // Kept in memory for now, so history clears on restart. The cap stops it growing forever.
    static final int MAX_ENTRIES = 200;

    private final Deque<CommandResult> entries = new ArrayDeque<>();

    public synchronized void record(CommandResult result) {
        entries.addFirst(result);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized List<CommandResult> all() {
        return List.copyOf(entries);
    }

    public synchronized List<CommandResult> recent(int limit) {
        return entries.stream().limit(limit).toList();
    }
}
