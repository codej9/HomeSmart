package com.homesmart.filter;

import jakarta.servlet.http.HttpServletRequest;

/** Path checks shared by the filters. */
final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request path inside the context. Built from the servlet path, which the container has already decoded
     * and normalized, because the raw request URI can hide a path such as {@code /css/../devices}.
     */
    static String path(HttpServletRequest request) {
        String info = request.getPathInfo();
        return request.getServletPath() + (info == null ? "" : info);
    }

    static boolean isAsset(String path) {
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/icons/");
    }

    static boolean isLoginOrSetup(String path) {
        return path.equals("/login") || path.equals("/setup");
    }
}
