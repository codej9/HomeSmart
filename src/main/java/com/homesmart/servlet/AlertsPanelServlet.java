package com.homesmart.servlet;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.listener.AppContextListener;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.service.DeviceService;
import com.homesmart.service.SecurityAlerts;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** GET /panels/alerts: the door alert banners. alerts.js fetches it again every 30 seconds. */
@WebServlet("/panels/alerts")
public class AlertsPanelServlet extends PanelServlet {

    static final String VIEW = "/WEB-INF/views/panels/alerts.jsp";

    private DeviceService deviceService;
    private ActivityLogDAO activityLog;
    private Duration threshold;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
        activityLog = (ActivityLogDAO) getServletContext().getAttribute(AppContextListener.ACTIVITY_LOG);
        threshold = (Duration) getServletContext().getAttribute(AppContextListener.DOOR_ALERT_THRESHOLD);
    }

    @Override
    protected String view() {
        return VIEW;
    }

    @Override
    protected void load(HttpServletRequest request) {
        request.setAttribute("alerts", banners());
    }

    private List<SecurityAlerts.Banner> banners() {
        LocalDateTime now = LocalDateTime.now();
        List<SecurityAlerts.Banner> banners = new ArrayList<>();
        for (Device device : deviceService.getAllDevices()) {
            if (device instanceof DoorLock door) {
                SecurityAlerts.evaluate(door, activityLog.latestOfTypes(door.getId(), SecurityAlerts.LOCK_EVENTS),
                                now, threshold)
                        .ifPresent(text -> banners.add(new SecurityAlerts.Banner(door.getId(), text)));
            }
        }
        return banners;
    }
}
