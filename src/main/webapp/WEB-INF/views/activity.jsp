<%--
  Activity log. ActivityServlet provides ${activity}: CommandResult items, newest first, or ${flashError} when the log cannot be read.
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
    <%@ include file="/WEB-INF/views/topbar.jspf" %>
    <main class="main-content">
        <div class="section-heading page-heading">
            <h1>Activity log</h1>
            <a class="text-link" href="${pageContext.request.contextPath}/devices"><span aria-hidden="true">←</span> Dashboard</a>
        </div>
        <c:choose>
            <c:when test="${not empty flashError}">
                <div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div>
            </c:when>
            <c:otherwise>
                <div class="activity-panel glass">
                    <%@ include file="activity-table.jspf" %>
                </div>
            </c:otherwise>
        </c:choose>
        <jsp:include page="/panels/account"/>
    </main>
    <%@ include file="/WEB-INF/views/footer.jspf" %>
</div>
</body>
</html>
