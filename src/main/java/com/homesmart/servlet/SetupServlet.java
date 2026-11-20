package com.homesmart.servlet;

import com.homesmart.dao.UserDAO;
import com.homesmart.listener.AppContextListener;
import com.homesmart.model.User;
import com.homesmart.service.LoginThrottle;
import com.homesmart.service.SetupService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * /setup creates the first account with the setup code from the server log.
 * It answers 404 once an account exists.
 */
@WebServlet("/setup")
public class SetupServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/setup.jsp";

    private UserDAO userDAO;
    private SetupService setupService;
    private LoginThrottle throttle;

    @Override
    public void init() {
        userDAO = (UserDAO) getServletContext().getAttribute(AppContextListener.USER_DAO);
        setupService = (SetupService) getServletContext().getAttribute(AppContextListener.SETUP_SERVICE);
        throttle = (LoginThrottle) getServletContext().getAttribute(AppContextListener.LOGIN_THROTTLE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isClosed(request, response)) {
            return;
        }
        DeviceServlet.moveFlashToRequest(request);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isClosed(request, response)) {
            return;
        }
        String username = User.normalize(request.getParameter("username"));
        String address = request.getRemoteAddr();
        if (throttle.isRefused(username, address)) {
            response.setStatus(429);
            fail(request, response, LoginServlet.TOO_MANY);
            return;
        }
        try {
            setupService.createFirstAccount(request.getParameter("code"), username, request.getParameter("password"));
        } catch (IllegalArgumentException e) {
            if (SetupService.WRONG_CODE.equals(e.getMessage())) {
                throttle.recordFailure(username, address);
            }
            fail(request, response, e.getMessage());
            return;
        } catch (IllegalStateException e) {
            log("Could not create the account", e);
            fail(request, response, DeviceServlet.DATABASE_UNAVAILABLE);
            return;
        }
        request.getSession().setAttribute(DeviceServlet.FLASH_MESSAGE, "Account created. Sign in below.");
        response.sendRedirect(request.getContextPath() + "/login");
    }

    /** Sends 404 and returns true once an account exists. A failed lookup shows the database text on the form. */
    private boolean isClosed(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (userDAO.count() > 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return true;
            }
        } catch (IllegalStateException e) {
            log("Could not check for accounts", e);
            request.setAttribute(DeviceServlet.FLASH_ERROR, DeviceServlet.DATABASE_UNAVAILABLE);
        }
        return false;
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute(DeviceServlet.FLASH_ERROR, message);
        request.setAttribute("username", request.getParameter("username"));
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
