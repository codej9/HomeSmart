package com.homesmart.servlet;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginServletTest {

    private static final String CONTEXT = "/homesmart";

    @Test
    void acceptsPathsInsideTheContext() {
        assertEquals(Optional.of("/homesmart/activity"), LoginServlet.safeNext("/homesmart/activity", CONTEXT));
        assertEquals(Optional.of("/homesmart/panels/alerts"), LoginServlet.safeNext("/homesmart/panels/alerts", CONTEXT));
    }

    @Test
    void rejectsAnythingElse() {
        for (String next : new String[] {null, "", "/homesmart", "/other", "//evil.example/homesmart/x",
                "https://evil.example/homesmart/x", "/homesmart//evil.example", "/homesmart/\\evil.example",
                "/homesmart/x\r\nSet-Cookie: a=b", "homesmart/activity"}) {
            assertEquals(Optional.empty(), LoginServlet.safeNext(next, CONTEXT), String.valueOf(next));
        }
    }
}
