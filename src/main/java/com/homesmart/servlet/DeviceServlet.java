package com.homesmart.servlet;

import com.homesmart.listener.AppContextListener;
import com.homesmart.service.CommandResult;
import com.homesmart.service.DeviceService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

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

    private DeviceService deviceService;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("devices", deviceService.getAllDevices());
        moveFlashToRequest(request);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();
        try {
            int deviceId = parseDeviceId(request.getParameter("deviceId"));
            CommandResult result = deviceService.execute(
                    deviceId, request.getParameter("action"), request.getParameter("value"));
            session.setAttribute(FLASH_MESSAGE, result.message());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Invalid command, or the database could not save the change
            session.setAttribute(FLASH_ERROR, e.getMessage());
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
