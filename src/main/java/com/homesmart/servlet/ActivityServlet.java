package com.homesmart.servlet;

import com.homesmart.listener.AppContextListener;
import com.homesmart.service.ActivityLog;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** GET /activity: shows every logged device command, newest first. */
@WebServlet("/activity")
public class ActivityServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/activity.jsp";

    private ActivityLog activityLog;

    @Override
    public void init() {
        activityLog = (ActivityLog) getServletContext().getAttribute(AppContextListener.ACTIVITY_LOG);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("activity", activityLog.all());
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
