<%--
  Door alert panel. AlertsPanelServlet provides ${alerts} or ${databaseError}, and ${panelPoll}.
  A poll gets only the fragment, so alerts.js can swap it into the page.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${panelPoll}"><%@ include file="alerts-body.jspf" %></c:when>
    <c:otherwise>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/alerts.css">
<section class="hs-alerts" id="panel-alerts" role="alert" data-refresh-url="${pageContext.request.contextPath}/panels/alerts">
    <%@ include file="alerts-body.jspf" %>
</section>
<script src="${pageContext.request.contextPath}/js/alerts.js" defer></script>
    </c:otherwise>
</c:choose>
