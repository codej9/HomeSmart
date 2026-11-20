package com.homesmart.servlet;

import com.homesmart.dao.UserDAO;
import com.homesmart.filter.AuthFilter;
import com.homesmart.listener.AppContextListener;
import com.homesmart.model.User;
import com.homesmart.service.LoginThrottle;
import com.homesmart.service.PasswordHasher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * GET /login shows the sign-in form, or sends the visitor to /setup while no account exists.
 * POST /login (username, password, next) checks the password and redirects to {@code next} or to /devices.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    static final String VIEW = "/WEB-INF/views/login.jsp";
    static final String BAD_LOGIN = "Username or password is incorrect.";
    static final String TOO_MANY = "Too many failed attempts. Try again in 15 minutes.";

    private UserDAO userDAO;
    private LoginThrottle throttle;

    @Override
    public void init() {
        userDAO = (UserDAO) getServletContext().getAttribute(AppContextListener.USER_DAO);
        throttle = (LoginThrottle) getServletContext().getAttribute(AppContextListener.LOGIN_THROTTLE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            if (userDAO.count() == 0) {
                response.sendRedirect(request.getContextPath() + "/setup");
                return;
            }
        } catch (IllegalStateException e) {
            log("Could not check for accounts", e);
            request.setAttribute(DeviceServlet.FLASH_ERROR, DeviceServlet.DATABASE_UNAVAILABLE);
        }
        DeviceServlet.moveFlashToRequest(request);
        show(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String username = User.normalize(request.getParameter("username"));
        String password = request.getParameter("password");
        String address = request.getRemoteAddr();
        if (throttle.isRefused(username, address)) {
            response.setStatus(429);
            fail(request, response, TOO_MANY);
            return;
        }
        Optional<User> user;
        try {
            user = userDAO.findByUsername(username);
        } catch (IllegalStateException e) {
            log("Could not look up the account", e);
            fail(request, response, DeviceServlet.DATABASE_UNAVAILABLE);
            return;
        }
        String given = password == null ? "" : password;
        if (user.isEmpty()) {
            PasswordHasher.dummyVerify(given);
        }
        if (user.isEmpty() || !PasswordHasher.verify(given, user.get().passwordHash())) {
            throttle.recordFailure(username, address);
            fail(request, response, BAD_LOGIN);
            return;
        }
        throttle.recordSuccess(username, address);
        signIn(request, user.get());
        response.sendRedirect(safeNext(request.getParameter("next"), request.getContextPath())
                .orElse(request.getContextPath() + "/devices"));
    }

    private static void signIn(HttpServletRequest request, User user) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        Instant now = Instant.now();
        session.setAttribute(AuthFilter.USERNAME, user.username());
        session.setAttribute(AuthFilter.LOGIN_AT, now);
        session.setAttribute(AuthFilter.LAST_SEEN_AT, now);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute(DeviceServlet.FLASH_ERROR, message);
        show(request, response);
    }

    private void show(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("next", safeNext(request.getParameter("next"), request.getContextPath()).orElse(""));
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** Accepts only a path inside this application, so a sign-in link cannot send anyone to another site. */
    static Optional<String> safeNext(String next, String contextPath) {
        if (next == null || !next.startsWith(contextPath + "/") || next.startsWith(contextPath + "//")
                || next.chars().anyMatch(c -> c < ' ' || c == '\\' || c == 127)) {
            return Optional.empty();
        }
        return Optional.of(next);
    }
}
