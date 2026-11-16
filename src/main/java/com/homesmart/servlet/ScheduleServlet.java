package com.homesmart.servlet;

import com.homesmart.listener.AppContextListener;
import com.homesmart.model.Device;
import com.homesmart.model.Schedule;
import com.homesmart.model.Thermostat;
import com.homesmart.service.DeviceService;
import com.homesmart.service.ScheduleService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schedules page.
 *
 * <ul>
 *   <li>GET  /schedules – lists the schedules and the form to add one.</li>
 *   <li>POST /schedules – {@code op} is create, enable, disable or delete; the last 3 also take
 *       {@code scheduleId}. Redirects back to GET, like {@link DeviceServlet}.</li>
 * </ul>
 */
@WebServlet("/schedules")
public class ScheduleServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/schedules.jsp";

    private DeviceService deviceService;
    /** Null when the {@code schedules} table is not available. */
    private ScheduleService scheduleService;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
        scheduleService = (ScheduleService) getServletContext().getAttribute(AppContextListener.SCHEDULE_SERVICE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        DeviceServlet.moveFlashToRequest(request);
        if (scheduleService != null) {
            try {
                request.setAttribute("schedules", scheduleService.all());
                loadDevices(request);
            } catch (IllegalStateException e) {
                log("Could not load schedules", e);
                request.setAttribute("schedules", List.of());
                request.setAttribute(DeviceServlet.FLASH_ERROR, DeviceServlet.DATABASE_UNAVAILABLE);
            }
        }
        request.setAttribute("zone", ZoneId.systemDefault().getId());
        request.setAttribute("minTemperature", Thermostat.MIN_TEMPERATURE);
        request.setAttribute("maxTemperature", Thermostat.MAX_TEMPERATURE);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (scheduleService != null) {
            HttpSession session = request.getSession();
            try {
                session.setAttribute(DeviceServlet.FLASH_MESSAGE, run(request));
            } catch (IllegalArgumentException e) {
                session.setAttribute(DeviceServlet.FLASH_ERROR, e.getMessage());
            } catch (IllegalStateException e) {
                log("Could not change schedules", e);
                session.setAttribute(DeviceServlet.FLASH_ERROR, DeviceServlet.DATABASE_UNAVAILABLE);
            }
        }
        response.sendRedirect(request.getContextPath() + "/schedules");
    }

    /** Runs the operation and returns the message to show. */
    private String run(HttpServletRequest request) {
        String op = request.getParameter("op");
        if (op == null) {
            throw new IllegalArgumentException("No operation was given");
        }
        return switch (op) {
            case "create" -> {
                Schedule schedule = scheduleService.create(request.getParameter("name"),
                        request.getParameter("deviceId"), request.getParameter("activityType"),
                        request.getParameter("value"), request.getParameter("runAt"),
                        request.getParameterValues("days") == null
                                ? List.of() : Arrays.asList(request.getParameterValues("days")));
                yield "Schedule added: " + schedule.name();
            }
            case "enable" -> {
                scheduleService.setEnabled(parseScheduleId(request), true);
                yield "Schedule turned on.";
            }
            case "disable" -> {
                scheduleService.setEnabled(parseScheduleId(request), false);
                yield "Schedule turned off.";
            }
            case "delete" -> {
                scheduleService.delete(parseScheduleId(request));
                yield "Schedule deleted.";
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + op);
        };
    }

    private static int parseScheduleId(HttpServletRequest request) {
        try {
            return Integer.parseInt(request.getParameter("scheduleId"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid schedule id");
        }
    }

    private void loadDevices(HttpServletRequest request) {
        List<Device> devices = deviceService.getAllDevices();
        Map<Integer, String> names = new LinkedHashMap<>();
        devices.forEach(device -> names.put(device.getId(), device.getName()));
        request.setAttribute("devices", devices);
        request.setAttribute("deviceNames", names);
    }
}
