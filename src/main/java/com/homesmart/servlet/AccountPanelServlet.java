package com.homesmart.servlet;

import com.homesmart.filter.AuthFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /panels/account: the signed-in user and the log out form, included into the dashboard.
 * Always includes the view, never forwards, because a forward breaks the page around a jsp:include.
 */
@WebServlet("/panels/account")
public class AccountPanelServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/panels/account.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        if (request.getHeader(AuthFilter.POLL_HEADER) != null) {
            request.setAttribute("panelPoll", true);
        }
        request.getRequestDispatcher(VIEW).include(request, response);
    }
}
