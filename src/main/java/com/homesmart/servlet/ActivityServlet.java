package com.homesmart.servlet;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.listener.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * GET /activity: shows every logged device command, newest first.
 * Shows the database-unavailable message instead when the log cannot be read.
 */
@WebServlet("/activity")
public class ActivityServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/activity.jsp";

    private ActivityLogDAO activityLog;

    @Override
    public void init() {
        activityLog = (ActivityLogDAO) getServletContext().getAttribute(AppContextListener.ACTIVITY_LOG);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("activity", activityLog.all());
        } catch (IllegalStateException e) {
            log("Could not load the activity log", e);
            request.setAttribute("activity", List.of());
            request.setAttribute(DeviceServlet.FLASH_ERROR, DeviceServlet.DATABASE_UNAVAILABLE);
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
