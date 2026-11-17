package com.homesmart.filter;

import com.homesmart.listener.AppContextListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Answers 503 to every page when the {@code users} table is missing, because nobody could sign in. */
public class SchemaGuardFilter implements Filter {

    static final String MESSAGE = "Accounts need the latest database schema. Re-import src/homesmart.sql.";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean missing = Boolean.TRUE.equals(
                request.getServletContext().getAttribute(AppContextListener.USERS_TABLE_MISSING));
        if (!missing || RequestPaths.isAsset(RequestPaths.path((HttpServletRequest) request))) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletResponse http = (HttpServletResponse) response;
        http.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        http.setContentType("text/plain;charset=UTF-8");
        http.getWriter().println(MESSAGE);
    }
}
