<%--
  Account panel, included by the dashboard. With ${panelPoll} it renders only the body fragment.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${panelPoll}">
        <%@ include file="account-body.jspf" %>
    </c:when>
    <c:otherwise>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/account.css">
        <section class="hs-panel glass" id="panel-account">
            <%@ include file="account-body.jspf" %>
        </section>
    </c:otherwise>
</c:choose>
