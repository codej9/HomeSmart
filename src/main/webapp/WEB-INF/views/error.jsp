<%--
  Shown for 404s and any unexpected server error (see web.xml), instead of the server's default
  error page, which can print the Java exception and its message. The full error still goes to the server log.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="notFound" value="${requestScope['jakarta.servlet.error.status_code'] == 404}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | ${notFound ? 'Page not found' : 'Something went wrong'}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
</head>
<body>
<div class="dashboard-shell">
    <%@ include file="/WEB-INF/views/topbar.jspf" %>
    <main class="main-content">
        <div class="hero">
            <p class="eyebrow">${notFound ? 'ERROR 404' : 'ERROR'}</p>
            <h1>${notFound ? 'Page not found.' : 'Something went wrong.'}</h1>
            <p class="hero-subtitle">${notFound ? 'This page does not exist.' : 'The problem has been logged. Go back to the dashboard and try again.'}</p>
        </div>
        <a class="text-link" href="${pageContext.request.contextPath}/devices"><span aria-hidden="true">←</span> Back to the dashboard</a>
    </main>
    <%@ include file="/WEB-INF/views/footer.jspf" %>
</div>
</body>
</html>
