package com.homesmart.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Base for the servlets at {@code /panels/<feature>}. The dashboard includes them, and a script can fetch
 * them again with the header {@code X-HomeSmart-Poll: 1}, which sets the request attribute {@code panelPoll}.
 *
 * <p>The view is included, never forwarded: a forward inside {@code <jsp:include>} drops the page around it.
 */
abstract class PanelServlet extends HttpServlet {

    static final String POLL_HEADER = "X-HomeSmart-Poll";

    /** Path of the JSP that renders the panel. */
    protected abstract String view();

    /** Sets the request attributes the view needs. Throws {@link IllegalStateException} when the database fails. */
    protected abstract void load(HttpServletRequest request);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        boolean poll = "1".equals(request.getHeader(POLL_HEADER));
        request.setAttribute("panelPoll", poll);
        try {
            load(request);
        } catch (IllegalStateException e) {
            log("Could not load " + request.getRequestURI(), e);
            if (poll) {
                // The script keeps the current panel
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            request.setAttribute("databaseError", DeviceServlet.DATABASE_UNAVAILABLE);
        }
        response.setContentType("text/html;charset=UTF-8");
        request.getRequestDispatcher(view()).include(request, response);
    }
}
