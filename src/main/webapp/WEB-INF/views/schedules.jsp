<%--
  Schedules. ScheduleServlet provides ${schedules}, ${devices}, ${deviceNames}, ${zone}, ${flashMessage} and ${flashError}.
  Without the scheduleService attribute the database schema is old and only a notice shows.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>HomeSmart | Schedules</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/theme.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/schedules.css">
</head>
<body>
<div class="dashboard-shell">
    <header class="topbar glass">
        <div class="brand"><span class="brand-mark" aria-hidden="true">⌂</span><span>Home<span class="brand-accent">Smart</span></span></div>
        <span class="topbar-label">HOME CONTROL</span>
    </header>
    <main class="main-content">
        <div class="section-heading page-heading">
            <h1>Schedules</h1>
            <a class="text-link" href="${pageContext.request.contextPath}/devices"><span aria-hidden="true">←</span> Dashboard</a>
        </div>
        <c:if test="${not empty flashMessage}">
            <div class="alert alert-success" role="status"><c:out value="${flashMessage}"/></div>
        </c:if>
        <c:if test="${not empty flashError}">
            <div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div>
        </c:if>
        <c:choose>
            <c:when test="${empty applicationScope.scheduleService}">
                <div class="alert alert-notice" role="status">Schedules need the latest database schema. Re-import src/homesmart.sql.</div>
            </c:when>
            <c:otherwise>
                <section class="hs-panel glass">
                    <h2 class="hs-schedules-title">New schedule</h2>
                    <form class="hs-schedules-form" method="post" action="${pageContext.request.contextPath}/schedules">
                        <input type="hidden" name="op" value="create">
                        <label for="schedule-name">Name</label>
                        <input id="schedule-name" type="text" name="name" maxlength="50" required>
                        <label for="schedule-device">Device</label>
                        <select id="schedule-device" name="deviceId" required>
                            <c:forEach var="device" items="${devices}">
                                <option value="${device.id}"><c:out value="${device.name}"/></option>
                            </c:forEach>
                        </select>
                        <label for="schedule-action">Action</label>
                        <select id="schedule-action" name="activityType" required>
                            <option value="TURN_ON">Turn light on</option>
                            <option value="TURN_OFF">Turn light off</option>
                            <option value="LOCK">Lock door</option>
                            <option value="SET_TEMPERATURE">Set temperature</option>
                        </select>
                        <label for="schedule-value">Temperature (only for Set temperature)</label>
                        <input id="schedule-value" type="number" name="value" min="${minTemperature}" max="${maxTemperature}" step="1">
                        <label for="schedule-time">Time</label>
                        <input id="schedule-time" type="time" name="runAt" required>
                        <fieldset class="hs-schedules-days">
                            <legend>Days</legend>
                            <label><input type="checkbox" name="days" value="MON" checked> Mon</label>
                            <label><input type="checkbox" name="days" value="TUE" checked> Tue</label>
                            <label><input type="checkbox" name="days" value="WED" checked> Wed</label>
                            <label><input type="checkbox" name="days" value="THU" checked> Thu</label>
                            <label><input type="checkbox" name="days" value="FRI" checked> Fri</label>
                            <label><input type="checkbox" name="days" value="SAT" checked> Sat</label>
                            <label><input type="checkbox" name="days" value="SUN" checked> Sun</label>
                        </fieldset>
                        <button class="hs-schedules-button" type="submit">Add schedule</button>
                    </form>
                    <p class="hs-schedules-note">A schedule cannot unlock the door. Times use the server time zone: <c:out value="${zone}"/>.</p>
                </section>
                <section class="hs-panel glass">
                    <h2 class="hs-schedules-title">Your schedules</h2>
                    <c:choose>
                        <c:when test="${empty schedules}">
                            <p class="hs-schedules-note">No schedules yet.</p>
                        </c:when>
                        <c:otherwise>
                            <table class="hs-schedules-table">
                                <thead>
                                <tr><th scope="col">NAME</th><th scope="col">DEVICE</th><th scope="col">ACTION</th><th scope="col">TIME</th><th scope="col">DAYS</th><th scope="col">LAST RUN</th><th scope="col"><span class="hs-schedules-hidden">CONTROLS</span></th></tr>
                                </thead>
                                <tbody>
                                <c:forEach var="schedule" items="${schedules}">
                                    <tr>
                                        <th scope="row"><c:out value="${schedule.name()}"/><c:if test="${not schedule.enabled()}"> <span class="hs-schedules-note">(off)</span></c:if></th>
                                        <td><c:out value="${deviceNames[schedule.deviceId()]}"/></td>
                                        <td><c:out value="${schedule.actionText()}"/></td>
                                        <td><c:out value="${schedule.runAt()}"/></td>
                                        <td><c:out value="${schedule.daysText()}"/></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${empty schedule.lastRunOn()}">Not yet</c:when>
                                                <c:otherwise><c:out value="${schedule.lastRunOn()}"/>: <c:out value="${schedule.lastResult()}"/></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="hs-schedules-controls">
                                            <form method="post" action="${pageContext.request.contextPath}/schedules">
                                                <input type="hidden" name="scheduleId" value="${schedule.id()}">
                                                <button class="hs-schedules-button" type="submit" name="op" value="${schedule.enabled() ? 'disable' : 'enable'}">${schedule.enabled() ? 'Turn off' : 'Turn on'}</button>
                                                <button class="hs-schedules-button" type="submit" name="op" value="delete">Delete</button>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </c:otherwise>
                    </c:choose>
                </section>
            </c:otherwise>
        </c:choose>
    </main>
    <footer class="footer">HomeSmart · TechNest Solutions </footer>
</div>
</body>
</html>
