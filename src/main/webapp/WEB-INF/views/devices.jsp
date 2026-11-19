
<%--
  Basic backend test page for the device controls.
  The styled dashboard (frontend) can replace this file: the servlet provides
  ${devices}, ${activity}, ${flashMessage}, ${flashError} and ${applicationScope.usingSampleData},
  and each form posts
  deviceId + action (+ value for "set") to /devices, with the _csrf field from csrf-field.jspf.
  The thermostat's Eco button posts action "eco".
--%>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | Device Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
</head>
<body>
<div class="dashboard-shell">

    <%@ include file="/WEB-INF/views/topbar.jspf" %>

    <main class="main-content">
        <div class="hero">
            <p class="eyebrow">YOUR CONNECTED SPACE</p>
            <h1>Welcome home.</h1>
            <p class="hero-subtitle">
                A simpler way to manage your everyday devices.
            </p>
        </div>

        <c:if test="${applicationScope.usingSampleData}">
            <div class="alert alert-notice" role="status">
                Demo mode: database not connected. Changes will reset when the server restarts.
            </div>
        </c:if>

        <c:if test="${not empty flashMessage}">
            <div class="alert alert-success" role="status">
                <c:out value="${flashMessage}"/>
            </div>
        </c:if>

        <c:if test="${not empty flashError}">
            <div class="alert alert-error" role="alert">
                <c:out value="${flashError}"/>
            </div>
        </c:if>
        <jsp:include page="/panels/alerts"/>

        <div class="section-heading">
            <div>
                <p class="eyebrow">DASHBOARD</p>
                <h2>My devices</h2>
            </div>
            <span class="section-note">Light · Climate · Security</span>
        </div>

        <div class="device-grid">
            <c:forEach var="device" items="${devices}">
                <article class="device-card glass">

                    <div class="card-top">
                        <c:choose>
                            <c:when test="${device.type == 'LIGHT'}">
                                <span class="device-icon" aria-hidden="true">
                                    <img src="${pageContext.request.contextPath}/icons/lightbulb-filament-duotone.svg" alt="">
                                </span>
                                <span class="category">LIGHTING</span>
                            </c:when>
                            <c:when test="${device.type == 'THERMOSTAT'}">
                                <span class="device-icon" aria-hidden="true">
                                    <img src="${pageContext.request.contextPath}/icons/thermometer-duotone.svg" alt="">
                                </span>
                                <span class="category">CLIMATE</span>
                            </c:when>
                            <c:when test="${device.type == 'DOOR_LOCK'}">
                                <span class="device-icon" aria-hidden="true">
                                    <img src="${pageContext.request.contextPath}/icons/lock-key-duotone.svg" alt="">
                                </span>
                                <span class="category">SECURITY</span>
                            </c:when>
                        </c:choose>
                    </div>

                    <h3><c:out value="${device.name}"/></h3>

                    <c:if test="${device.type != 'THERMOSTAT'}">
                    <div class="status-line">
                        <span class="status-dot ${device.type == 'LIGHT' && device.on ? 'is-active' : ''}"></span>
                        <span>Status: <strong><c:out value="${device.status}"/></strong></span>
                    </div>
                    </c:if>

                    <div class="card-controls">
                        <c:choose>

                            <c:when test="${device.type == 'LIGHT'}">
                                <form method="post" action="${pageContext.request.contextPath}/devices">
                                    <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                    <input type="hidden" name="deviceId" value="${device.id}">
                                    <button class="action-button" type="submit"
                                            name="action" value="${device.on ? 'off' : 'on'}">
                                        ${device.on ? 'Turn light off' : 'Turn light on'}
                                        <img src="${pageContext.request.contextPath}/icons/arrow-right-duotone.svg"
                                             alt="" aria-hidden="true">
                                    </button>
                                </form>
                                <a class="schedule-link"
                                   href="${pageContext.request.contextPath}/schedules?deviceId=${device.id}"
                                   aria-label="Schedule <c:out value="${device.name}"/>">Schedule</a>
                            </c:when>

                            <c:when test="${device.type == 'THERMOSTAT'}">
                                <div class="temp-caption">Current setpoint</div>
                                <div class="temp-adjust">
                                    <form method="post" action="${pageContext.request.contextPath}/devices">
                                        <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                        <input type="hidden" name="deviceId" value="${device.id}">
                                        <button class="square-button" type="submit"
                                                name="action" value="decrease"
                                                aria-label="Decrease temperature by one degree">−</button>
                                    </form>

                                    <span class="temp-display">
                                        <c:out value="${device.temperature}"/>°C
                                    </span>

                                    <form method="post" action="${pageContext.request.contextPath}/devices">
                                        <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                        <input type="hidden" name="deviceId" value="${device.id}">
                                        <button class="square-button" type="submit"
                                                name="action" value="increase"
                                                aria-label="Increase temperature by one degree">+</button>
                                    </form>
                                </div>

                                <form class="target-form" method="post"
                                      action="${pageContext.request.contextPath}/devices">
                                    <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                    <input type="hidden" name="deviceId" value="${device.id}">
                                    <label for="target-${device.id}">Target temperature</label>
                                    <div class="target-row">
                                        <input id="target-${device.id}" type="number"
                                               name="value" value="${device.temperature}"
                                               min="${device.minTemperature}"
                                               max="${device.maxTemperature}"
                                               step="1" required
                                               aria-label="Target temperature in Celsius">
                                        <button class="small-button" type="submit"
                                                name="action" value="set">Set</button>
                                    </div>
                                </form>

                                <%-- Eco preset --%>
                                <form class="eco-form" method="post"
                                      action="${pageContext.request.contextPath}/devices">
                                    <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                    <input type="hidden" name="deviceId" value="${device.id}">
                                    <button class="eco-button ${device.ecoMode ? 'is-active' : ''}"
                                            type="submit" name="action" value="eco"
                                            ${device.ecoMode ? 'disabled' : ''}>
                                        <span class="eco-leaf" aria-hidden="true">
                                            <img src="${pageContext.request.contextPath}/icons/leaf-duotone.svg" alt="">
                                        </span>
                                        <span>
                                            ${device.ecoMode ? 'Eco mode on' : 'Eco mode'} ·
                                            ${device.ecoTemperature} °C
                                        </span>
                                    </button>
                                </form>
                            </c:when>

                            <c:when test="${device.type == 'DOOR_LOCK'}">
                                <form method="post" action="${pageContext.request.contextPath}/devices">
                                    <%@ include file="/WEB-INF/views/csrf-field.jspf" %>
                                    <input type="hidden" name="deviceId" value="${device.id}">
                                    <button class="action-button" type="submit"
                                            name="action" value="${device.locked ? 'unlock' : 'lock'}">
                                        ${device.locked ? 'Unlock door' : 'Lock door'}
                                        <img src="${pageContext.request.contextPath}/icons/arrow-right-duotone.svg"
                                             alt="" aria-hidden="true">
                                    </button>
                                </form>
                            </c:when>

                        </c:choose>
                    </div>
                </article>
            </c:forEach>
        </div>
        <jsp:include page="/panels/energy"/>
        <jsp:include page="/panels/schedules"/>

        <div class="section-heading">
            <div>
                <p class="eyebrow">HISTORY</p>
                <h2>Recent activity</h2>
            </div>
            <a class="text-link" href="${pageContext.request.contextPath}/activity">
                View full log
                <img src="${pageContext.request.contextPath}/icons/arrow-right-duotone.svg"
                     alt="" aria-hidden="true">
            </a>
        </div>

        <div class="activity-panel glass">
            <%@ include file="activity-table.jspf" %>
        </div>
        <jsp:include page="/panels/account"/>
    </main>

    <%@ include file="/WEB-INF/views/footer.jspf" %>
</div>
</body>
</html>
