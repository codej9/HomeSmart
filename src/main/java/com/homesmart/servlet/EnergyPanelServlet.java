package com.homesmart.servlet;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.listener.AppContextListener;
import com.homesmart.model.ActivityEntry;
import com.homesmart.model.ActivityType;
import com.homesmart.model.Device;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;
import com.homesmart.service.DeviceService;
import com.homesmart.service.EnergyEstimator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** GET /panels/energy: estimated light energy and average thermostat setpoint, for today and the last 7 days. */
@WebServlet("/panels/energy")
public class EnergyPanelServlet extends PanelServlet {

    static final String VIEW = "/WEB-INF/views/panels/energy.jsp";

    /** One table cell: the figure, and a note such as "Tracked since ..." (empty when there is none). */
    public record Cell(String text, String note) {
    }

    /** One table row: a device with its figures for both periods. */
    public record Row(String deviceName, Cell today, Cell week) {
    }

    private DeviceService deviceService;
    private ActivityLogDAO activityLog;
    private DateTimeFormatter timeFormat;

    @Override
    public void init() {
        deviceService = (DeviceService) getServletContext().getAttribute(AppContextListener.DEVICE_SERVICE);
        activityLog = (ActivityLogDAO) getServletContext().getAttribute(AppContextListener.ACTIVITY_LOG);
        timeFormat = (DateTimeFormatter) getServletContext().getAttribute(AppContextListener.TIME_FORMAT);
    }

    @Override
    protected String view() {
        return VIEW;
    }

    @Override
    protected void load(HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        List<Row> rows = new ArrayList<>();
        for (Device device : deviceService.getAllDevices()) {
            if (device instanceof Light light) {
                rows.add(new Row(light.getName(), lightCell(light, todayStart, now), lightCell(light, weekStart, now)));
            } else if (device instanceof Thermostat thermostat) {
                rows.add(new Row(thermostat.getName(), thermostatCell(thermostat, todayStart, now),
                        thermostatCell(thermostat, weekStart, now)));
            }
        }
        request.setAttribute("energyRows", rows);
    }

    private Cell lightCell(Light light, LocalDateTime periodStart, LocalDateTime now) {
        Optional<EnergyEstimator.LightEstimate> estimate = EnergyEstimator.light(
                baseline(light, EnergyEstimator.LIGHT_TYPES, periodStart),
                changes(light, EnergyEstimator.LIGHT_TYPES, periodStart, now), periodStart, now);
        if (estimate.isEmpty()) {
            return new Cell("No light changes recorded yet.", "");
        }
        return new Cell(EnergyEstimator.formatWattHours(estimate.get().wattHours()),
                trackedNote(estimate.get().trackedStart(), periodStart));
    }

    private Cell thermostatCell(Thermostat thermostat, LocalDateTime periodStart, LocalDateTime now) {
        Optional<EnergyEstimator.SetpointAverage> average = EnergyEstimator.thermostat(
                baseline(thermostat, EnergyEstimator.THERMOSTAT_TYPES, periodStart),
                changes(thermostat, EnergyEstimator.THERMOSTAT_TYPES, periodStart, now), periodStart, now);
        if (average.isEmpty()) {
            return new Cell("Current setpoint " + thermostat.getTemperature() + " °C. No changes recorded yet.", "");
        }
        String text = String.format(Locale.ROOT, "%.1f °C over %s", average.get().averageCelsius(),
                EnergyEstimator.formatDuration(average.get().tracked()));
        return new Cell(text, trackedNote(average.get().trackedStart(), periodStart));
    }

    private Optional<ActivityEntry> baseline(Device device, Set<ActivityType> types,
                                             LocalDateTime periodStart) {
        return activityLog.latestOfTypesAtOrBefore(device.getId(), types, periodStart);
    }

    private List<ActivityEntry> changes(Device device, Set<ActivityType> types,
                                        LocalDateTime periodStart, LocalDateTime now) {
        return activityLog.ofTypesBetween(device.getId(), types, periodStart, now);
    }

    private String trackedNote(LocalDateTime trackedStart, LocalDateTime periodStart) {
        return trackedStart.isAfter(periodStart) ? "Tracked since " + timeFormat.format(trackedStart) : "";
    }
}
