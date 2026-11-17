<%--
  First-account page. SetupServlet provides ${username} (the last entry), ${flashMessage} and ${flashError}.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | Set up</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>
<div class="dashboard-shell">
    <%@ include file="/WEB-INF/views/topbar.jspf" %>
    <main class="main-content">
        <div class="login-panel glass">
            <h1>Create the first account</h1>
            <p class="login-hint">HomeSmart prints a 12-character setup code when it starts. Find the line that starts with "Setup code:" in the server log.</p>
            <c:if test="${not empty flashError}">
                <div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/setup">
                <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                <label for="code">Setup code</label>
                <input id="code" name="code" type="text" autocomplete="off" required autofocus>
                <label for="username">Username</label>
                <input id="username" name="username" type="text" autocomplete="username" value="<c:out value="${username}"/>" required>
                <p class="login-hint">3 to 50 characters: a-z, 0-9, dot, underscore, or hyphen.</p>
                <label for="password">Password</label>
                <input id="password" name="password" type="password" autocomplete="new-password" required>
                <p class="login-hint">15 to 64 characters.</p>
                <button class="action-button" type="submit">Create account <span aria-hidden="true">→</span></button>
            </form>
        </div>
    </main>
    <%@ include file="/WEB-INF/views/footer.jspf" %>
</div>
</body>
</html>
