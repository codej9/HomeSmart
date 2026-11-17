package com.homesmart.filter;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Request, response, session and context stand-ins built with {@link Proxy}, so the filters run without a server. */
final class Fakes {

    private Fakes() {
    }

    static final class Session {
        final Map<String, Object> attributes = new HashMap<>();
        boolean invalidated;
        final HttpSession proxy = proxy(HttpSession.class, (p, m, args) -> switch (m.getName()) {
            case "getAttribute" -> attributes.get((String) args[0]);
            case "setAttribute" -> attributes.put((String) args[0], args[1]);
            case "invalidate" -> invalidated = true;
            default -> defaultValue(m.getReturnType());
        });
    }

    static final class Context {
        final Map<String, Object> attributes = new HashMap<>();
        final ServletContext proxy = proxy(ServletContext.class, (p, m, args) -> switch (m.getName()) {
            case "getAttribute" -> attributes.get((String) args[0]);
            default -> defaultValue(m.getReturnType());
        });
    }

    static final class Request {
        String method = "GET";
        String servletPath = "/devices";
        String pathInfo;
        final Map<String, String> headers = new HashMap<>();
        final Map<String, String> params = new HashMap<>();
        final Context context = new Context();
        Session session;
        final HttpServletRequest proxy = proxy(HttpServletRequest.class, (p, m, args) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getServletPath" -> servletPath;
            case "getPathInfo" -> pathInfo;
            case "getContextPath" -> "/homesmart";
            case "getRemoteAddr" -> "127.0.0.1";
            case "getHeader" -> headers.get((String) args[0]);
            case "getParameter" -> params.get((String) args[0]);
            case "getServletContext" -> context.proxy;
            case "getSession" -> {
                boolean create = args == null || (Boolean) args[0];
                if (session == null && create) {
                    session = new Session();
                }
                yield session == null ? null : session.proxy;
            }
            default -> defaultValue(m.getReturnType());
        });

        Request(String method, String servletPath) {
            this.method = method;
            this.servletPath = servletPath;
        }
    }

    static final class Response {
        int status = 200;
        String redirect;
        final Map<String, String> headers = new HashMap<>();
        final StringWriter body = new StringWriter();
        final HttpServletResponse proxy = proxy(HttpServletResponse.class, (p, m, args) -> switch (m.getName()) {
            case "setStatus" -> status = (Integer) args[0];
            case "sendRedirect" -> redirect = (String) args[0];
            case "setHeader" -> headers.put((String) args[0], (String) args[1]);
            case "getWriter" -> new PrintWriter(body, true);
            default -> defaultValue(m.getReturnType());
        });
    }

    /** Records whether the next filter or servlet ran. */
    static final class Chain {
        boolean called;
        final jakarta.servlet.FilterChain proxy = (request, response) -> called = true;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
