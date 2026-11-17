package com.homesmart.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

/** GET /panels/account: the signed-in user and the log out form. It reads no database. */
@WebServlet("/panels/account")
public class AccountPanelServlet extends PanelServlet {

    static final String VIEW = "/WEB-INF/views/panels/account.jsp";

    @Override
    protected String view() {
        return VIEW;
    }

    @Override
    protected void load(HttpServletRequest request) {
        // the view reads the username from the session
    }
}
