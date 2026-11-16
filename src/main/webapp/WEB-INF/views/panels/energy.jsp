<%--
  Energy panel. EnergyPanelServlet provides ${energyRows} or ${databaseError}, and ${panelPoll}.
--%>
<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${panelPoll}"><%@ include file="energy-body.jspf" %></c:when>
    <c:otherwise>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/energy.css">
<section class="hs-panel glass" id="panel-energy">
    <%@ include file="energy-body.jspf" %>
</section>
    </c:otherwise>
</c:choose>
