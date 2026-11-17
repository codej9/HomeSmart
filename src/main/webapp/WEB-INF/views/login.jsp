<%--
  Sign-in page. LoginServlet provides ${next} (a local path or empty), ${flashMessage} and ${flashError}.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | Sign in</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>
<div class="dashboard-shell">
    <header class="topbar glass">
        <div class="brand"><span class="brand-mark" aria-hidden="true">⌂</span><span>Home<span class="brand-accent">Smart</span></span></div>
        <span class="topbar-label">HOME CONTROL</span>
    </header>
    <main class="main-content">
        <div class="login-panel glass">
            <h1>Sign in</h1>
            <c:if test="${not empty flashMessage}">
                <div class="alert alert-success" role="status"><c:out value="${flashMessage}"/></div>
            </c:if>
            <c:if test="${not empty flashError}">
                <div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/login">
                <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                <input type="hidden" name="next" value="<c:out value="${next}"/>">
                <label for="username">Username</label>
                <input id="username" name="username" type="text" autocomplete="username" required autofocus>
                <label for="password">Password</label>
                <input id="password" name="password" type="password" autocomplete="current-password" required>
                <button class="action-button" type="submit">Sign in <span aria-hidden="true">→</span></button>
            </form>
        </div>
    </main>
    <footer class="footer">HomeSmart · TechNest Solutions </footer>
</div>
</body>
</html>
