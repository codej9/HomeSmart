package com.homesmart.servlet;

import com.homesmart.listener.AppContextListener;
import com.homesmart.service.ActivityLog;
import com.homesmart.service.CommandResult;
import com.homesmart.service.DeviceService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Controller for device controls.
 *
 * <ul>
 *   <li>GET  /devices – loads every device and forwards to the dashboard JSP.</li>
 *   <li>POST /devices – runs a command (deviceId, action, optional value), then
 *       redirects back to GET so refreshing the page does not repeat the command.</li>
 * </ul>
 */
@WebServlet("/devices")
public class DeviceServlet extends HttpServlet {

    /** JSP that renders the device cards. */
    static final String VIEW = "/WEB-INF/views/devices.jsp";

    private static final String FLASH_MESSAGE = "flashMessage";
    private static final String FLASH_ERROR = "flashError";

    /** Shown instead of the raw JDBC error; the full error goes to the server log. */
    static final String DATABASE_UNAVAILABLE =
            "Could not reach the database. Check that MySQL is running, then try again.";

    static final int RECENT_ACTIVITY_LIMIT = 5;

    private DeviceService deviceService;
    private ActivityLog activityLog;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
        activityLog = (ActivityLog) getServletContext().getAttribute(AppContextListener.ACTIVITY_LOG);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        moveFlashToRequest(request);
        try {
            request.setAttribute("devices", deviceService.getAllDevices());
        } catch (IllegalStateException e) {
            // MySQL stopped after the app started: show the dashboard with a message instead of an error 500
            log("Could not load devices", e);
            request.setAttribute("devices", List.of());
            request.setAttribute(FLASH_ERROR, DATABASE_UNAVAILABLE);
        }
        request.setAttribute("activity", activityLog.recent(RECENT_ACTIVITY_LIMIT));
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();
        try {
            int deviceId = parseDeviceId(request.getParameter("deviceId"));
            CommandResult result = deviceService.execute(
                    deviceId, request.getParameter("action"), request.getParameter("value"));
            activityLog.record(result);
            session.setAttribute(FLASH_MESSAGE, result.message());
        } catch (IllegalArgumentException e) {
            // Invalid command; the message explains what was wrong
            session.setAttribute(FLASH_ERROR, e.getMessage());
        } catch (IllegalStateException e) {
            // The database could not save the change
            log("Could not run command", e);
            session.setAttribute(FLASH_ERROR, DATABASE_UNAVAILABLE);
        }
        response.sendRedirect(request.getContextPath() + "/devices");
    }

    private static int parseDeviceId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid device id");
        }
    }

    /** Messages are kept in the session across the redirect, shown once, then removed. */
    private static void moveFlashToRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        for (String key : new String[] {FLASH_MESSAGE, FLASH_ERROR}) {
            Object value = session.getAttribute(key);
            if (value != null) {
                request.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
    }
}
