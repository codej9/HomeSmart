package com.homesmart.servlet;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.listener.AppContextListener;
import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.service.DeviceService;
import com.homesmart.service.SecurityAlerts;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /panels/alerts: the door alert banners. The dashboard includes it, and alerts.js
 * fetches it again every 30 seconds with the header X-HomeSmart-Poll.
 */
@WebServlet("/panels/alerts")
public class AlertsPanelServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/panels/alerts.jsp";

    static final String POLL_HEADER = "X-HomeSmart-Poll";

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
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        boolean poll = "1".equals(request.getHeader(POLL_HEADER));
        request.setAttribute("panelPoll", poll);
        try {
            request.setAttribute("alerts", banners());
        } catch (IllegalStateException e) {
            log("Could not load alerts", e);
            if (poll) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            request.setAttribute("databaseError", DeviceServlet.DATABASE_UNAVAILABLE);
        }
        response.setContentType("text/html;charset=UTF-8");
        request.getRequestDispatcher(VIEW).include(request, response);
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
