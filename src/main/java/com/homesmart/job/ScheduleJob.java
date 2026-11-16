package com.homesmart.job;

import com.homesmart.service.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs the schedules that are due. */
public class ScheduleJob implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ScheduleJob.class.getName());

    private final ScheduleService scheduleService;

    public ScheduleJob(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Override
    public void run() {
        try {
            scheduleService.runDue();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Schedule check failed", e);
        }
    }
}
