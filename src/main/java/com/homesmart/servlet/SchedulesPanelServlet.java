package com.homesmart.servlet;

import com.homesmart.listener.AppContextListener;
import com.homesmart.model.Device;
import com.homesmart.service.DeviceService;
import com.homesmart.service.ScheduleService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /panels/schedules: the next 3 scheduled runs and a link to the schedules page. */
@WebServlet("/panels/schedules")
public class SchedulesPanelServlet extends PanelServlet {

    static final String VIEW = "/WEB-INF/views/panels/schedules.jsp";

    static final int UPCOMING_RUNS = 3;

    private DeviceService deviceService;
    /** Null when the {@code schedules} table is not available. */
    private ScheduleService scheduleService;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
        scheduleService = (ScheduleService) getServletContext().getAttribute(AppContextListener.SCHEDULE_SERVICE);
    }

    @Override
    protected String view() {
        return VIEW;
    }

    @Override
    protected void load(HttpServletRequest request) {
        if (scheduleService == null) {
            return;
        }
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Device device : deviceService.getAllDevices()) {
            names.put(device.getId(), device.getName());
        }
        request.setAttribute("deviceNames", names);
        request.setAttribute("upcomingRuns", scheduleService.nextRuns(UPCOMING_RUNS));
    }
}
