<%--
  Activity log. ActivityServlet provides ${activity}: CommandResult items, newest first.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | Activity Log</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
</head>
<body>
<div class="dashboard-shell">
    <header class="topbar glass">
        <div class="brand"><span class="brand-mark" aria-hidden="true">⌂</span><span>Home<span class="brand-accent">Smart</span></span></div>
        <span class="topbar-label">HOME CONTROL / RELEASE 1</span>
    </header>
    <main class="main-content">
        <div class="section-heading page-heading">
            <h1>Activity log</h1>
            <a class="text-link" href="${pageContext.request.contextPath}/devices"><span aria-hidden="true">←</span> Dashboard</a>
        </div>
        <div class="activity-panel glass">
            <%@ include file="activity-table.jspf" %>
        </div>
    </main>
    <footer class="footer">HomeSmart · TechNest Solutions </footer>
</div>
</body>
</html>
