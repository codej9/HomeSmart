# HomeSmart – Release 1

[![CI](https://github.com/codej9/HomeSmart/actions/workflows/ci.yml/badge.svg)](https://github.com/codej9/HomeSmart/actions/workflows/ci.yml)

Java/JSP web application (Jakarta Servlet 6, Tomcat 10.1, Java 17+).
Release 1 covers three devices: a light, a thermostat and a door lock.

## Dashboard

Release 1 includes a functional JSP dashboard with CSS styling and working controls for the light, thermostat, and door lock. Device statuses update after each action.

The dashboard is connected to the team's MySQL `devices` table (`src/homesmart.sql`): every light, thermostat and door lock
change is saved there and is still there after the server restarts. The activity log is saved in the `activity_log` table, so it also survives a restart.
When the database is not set up, the app runs on sample data instead.

## Database setup (MySQL)

Run these from the `HomeSmart` folder. Each `mysql` command asks for your MySQL root password.

1. Create the database and a MySQL login for the app (user `homesmart`, password `homesmart`):
   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS homesmart; CREATE USER IF NOT EXISTS 'homesmart'@'localhost' IDENTIFIED BY 'homesmart'; GRANT ALL PRIVILEGES ON homesmart.* TO 'homesmart'@'localhost';"
   ```
2. Import the tables and starting data. This resets the devices and clears the activity log, the schedules and the accounts. Run it again whenever `src/homesmart.sql` changes:
   ```bash
   mysql -u root -p homesmart < src/homesmart.sql
   ```
3. Create the test database for the MySQL tests, give the app login access to it and import the same file:
   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS homesmart_test; GRANT ALL PRIVILEGES ON homesmart_test.* TO 'homesmart'@'localhost';"
   mysql -u root -p homesmart_test < src/homesmart.sql
   ```
   The tests only use `homesmart_test`, so they never change the data in `homesmart`.
   Run the import again in both databases whenever `src/homesmart.sql` changes.
4. Create your settings file. The example already uses the login from step 1, so a plain copy works:
   ```bash
   cp src/main/resources/db.properties.example src/main/resources/db.properties
   ```
   (On Windows: `copy src\main\resources\db.properties.example src\main\resources\db.properties`.)
   To use a different MySQL user, edit `db.user` and `db.password` in that file. `db.test.url` is the test database from step 3.
5. Start the app (see below). The console should print `[HomeSmart] Connected to MySQL at ...`.

If `db.properties` is missing or MySQL is not running, the app still starts using in-memory sample data,
logs a warning, and shows a notice on the page. If the `activity_log` table is missing, the app warns at start-up and keeps the log in memory; re-run step 2 and restart.
If MySQL stops while the app is running, the dashboard shows
"Could not reach the database" instead of an error page; start MySQL again and refresh. The activity log page shows the same message.
In sample data mode the schedules are kept in memory and are lost on restart.
If the `schedules` table is missing or old, the app logs a SEVERE message at start-up, runs no schedules, and asks you to re-import `src/homesmart.sql`.

## Run it

```bash
mvn package          # compiles, runs the tests, builds target/homesmart.war
mvn jetty:run        # quick local run at http://localhost:8080/homesmart (no Tomcat needed)
```

For Tomcat 10.1, deploy `target/homesmart.war` (or add it as an artifact in IntelliJ's Tomcat run configuration).

`DeviceServiceTest` checks the device logic on sample data. `MySqlDeviceDAOTest` checks that each command is saved in
and read back from the MySQL test database; it runs only when `db.test.url` is set in `db.properties` and MySQL is
reachable (otherwise it is skipped) and puts the original rows back afterwards. `MySqlActivityLogDAOTest` and `InMemoryActivityLogDAOTest` check that log entries are saved and read back.
`SecurityAlertsTest` and `DoorAlertJobTest` check the door alert rule with a fixed clock. `EnergyEstimatorTest` checks the energy estimate on made-up log rows.
`ScheduleServiceTest` checks when a schedule runs and which runs come next, with a fixed clock.

With the app running, `bash scripts/http-smoke-test.sh` sends 35 real requests to it (every command plus every kind of
bad request) and checks the HTTP codes and the message the dashboard shows. It changes the device states. On Windows,
run it from Git Bash.

## Build and test

Use the included Maven wrapper so everyone builds with the same Maven version.
Windows uses `mvnw.cmd`; macOS and Linux use `./mvnw`:

```bash
mvnw.cmd verify         # Windows
./mvnw verify           # macOS / Linux
```

The `verify` goal compiles, runs the tests, builds `target/homesmart.war` and writes the
JaCoCo coverage report to `target/site/jacoco/index.html`. GitHub Actions runs it with
`-Ddb.test.required=true` on JDK 17 and 21 against MySQL 8.0, so a skipped MySQL test fails the build.

Mutation testing stays off the normal build. Stop MySQL before the mutation run:

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

PIT targets `com.homesmart.*` except the MySQL DAO, servlets and listener.
The MySQL DAO writes to the database; servlets and listener have no unit-test path.
PIT also excludes MySQL DAO tests so mutations cannot change database rows.

## Structure

| Package | Purpose |
|---|---|
| `model` | `Device` base class and `Light`, `Thermostat`, `DoorLock`; `DeviceType` (kinds of device); `ActivityEntry`, `ActivityType`, `ActivitySource` and `Provenance` for the activity log; `Schedule` and `UpcomingRun`; `User` |
| `dao` | `DeviceDAO` storage interface; `MySqlDeviceDAO` (MySQL `devices` table) and `InMemoryDeviceDAO` (sample data fallback). `MySqlConnector` (shared JDBC connection). `ActivityLogDAO` storage interface; `MySqlActivityLogDAO` (MySQL `activity_log` table) and `InMemoryActivityLogDAO` (fallback). `ScheduleDAO`; `MySqlScheduleDAO` (MySQL `schedules` table) and `InMemoryScheduleDAO` (sample data mode). `UserDAO`; `MySqlUserDAO` (MySQL `users` table) and `InMemoryUserDAO` (sample data mode) |
| `service` | `DeviceService` – all device control logic and validation; `CommandResult` (one command's outcome); `SecurityAlerts` (door alert rule); `EnergyEstimator` (energy estimate from the log); `ScheduleService` (create schedules, find the ones that are due); `PasswordHasher`, `LoginThrottle` and `SetupService` (accounts and sign-in) |
| `job` | `DoorAlertJob` – writes the door alert to the activity log; `ScheduleJob` – runs the due schedules |
| `servlet` | `DeviceServlet` at `/devices`; `ActivityServlet` at `/activity`; `ScheduleServlet` at `/schedules`; `LoginServlet` at `/login`, `SetupServlet` at `/setup` and `LogoutServlet` at `/logout`; the panels `AlertsPanelServlet` at `/panels/alerts`, `EnergyPanelServlet` at `/panels/energy` and `SchedulesPanelServlet` at `/panels/schedules`, built on `PanelServlet`; `AccountPanelServlet` at `/panels/account` |
| `listener` | `AppContextListener` – reads `db.properties`, picks MySQL or sample data, creates the shared `DeviceService`, `ActivityLogDAO`, `ScheduleService` and `UserDAO`, and runs the background jobs |
| `filter` | `SecurityHeadersFilter`, `SchemaGuardFilter`, `AuthFilter` and `CsrfFilter`, in that order (declared in `web.xml`); `RequestPaths` (path checks shared by the filters) |

## Backend contract (for the dashboard, database and activity log)

**GET `/devices`** forwards to `WEB-INF/views/devices.jsp` with these request attributes:

- `devices` – list of devices. Every device has `id`, `name`, `type` (`LIGHT`, `THERMOSTAT`, `DOOR_LOCK`) and `status` (e.g. "On", "21 °C", "20 °C · Eco", "Locked").
  Lights also have `on`; thermostats have `temperature`, `minTemperature`, `maxTemperature`, `ecoTemperature` (20) and
  `ecoMode` (true at 20 °C); door locks have `locked`.
- `flashMessage` / `flashError` – result of the last command (shown once).
- `activity` – the 5 most recent log entries (`ActivityEntry`), newest first. Empty when the log cannot be read; `flashError` then holds the database message.

**POST `/devices`** with form fields:

| Device | `action` | `value` |
|---|---|---|
| Light | `on`, `off`, `toggle` | – |
| Thermostat | `increase`, `decrease` (±1 °C), `set`, `eco` (sets 20 °C) | target °C for `set` (10–32) |
| Door lock | `lock`, `unlock` | – |

plus `deviceId`. The servlet redirects back to `GET /devices` afterwards.

**Errors:** an unknown page (404) or any unexpected server error shows `WEB-INF/views/error.jsp` (set in `web.xml`)
instead of the server's default page, so users never see Java exception details.

**Database:** `MySqlDeviceDAO` reads and updates the `devices` table from `homesmart.sql`
(`power_on` for lights, `temperature` for thermostats, `locked` for door locks).

**Activity log:** `DeviceService` logs every command that changes a device, after saving it; a command that changes nothing logs nothing.
`MySqlActivityLogDAO` stores each entry in the `activity_log` table (columns in `src/homesmart.sql`).

**GET `/activity`** forwards to `WEB-INF/views/activity.jsp` with `activity`, the full log, newest first.

**Panels:** each feature shows a panel on the dashboard through its own servlet at `/panels/<feature>`, and the dashboard adds one line per panel: `<jsp:include page="/panels/<feature>"/>`.
A request with the header `X-HomeSmart-Poll: 1` gets only the panel's content, so a script can refresh it.

**Colors:** panel CSS uses only the `--hs-*` variables in `css/theme.css`, so a light theme only has to set new values for them.

**Door alert:** `DoorAlertJob` checks every door lock every 30 seconds and adds one "Security alert: left unlocked for 10 min" row to the activity log for each unlock that lasts 10 minutes or more.
The panel at `/panels/alerts` shows the alert with a "Lock door" button and refreshes every 30 seconds.
Set the time with the `homesmart.doorAlertMinutes` context-param in `web.xml` and the check interval with `homesmart.jobDelaySeconds`; a JVM option of the same name overrides each, for example `-Dhomesmart.doorAlertMinutes=1`.

**Energy estimate:** the panel at `/panels/energy` shows an estimate for today and the last 7 days, worked out from the activity log.
A light counts 9 W while it is on, a thermostat shows its average setpoint, and the door lock is not estimated.
The panel says the figures are estimates, not measurements.

**Schedules:** `/schedules` lists the schedules and lets the user add, turn off, turn on and delete them; `POST /schedules` takes `op` and the schedule fields, then redirects back with `flashMessage` or `flashError`.
The `schedules` table (columns in `src/homesmart.sql`) starts empty.
`ScheduleJob` checks every 30 seconds and runs a schedule within 5 minutes after its time, so a restart does not run it hours late.
Each schedule runs at most once a day, through `DeviceService`, so the usual checks apply and a schedule can never unlock the door.
The activity log shows the command with the schedule name, for example "Turned on (schedule: Evening light)".
Times use the server's time zone. The panel at `/panels/schedules` shows the next 3 runs.

## Sign-in and remote access

Every page except `/login` and `/setup` needs a signed-in account. Accounts are stored in the `users` table, and in memory in sample data mode.

**First account.** While the table is empty the app logs `Setup code: <12 characters>` at start-up, and `/login` redirects to `/setup`.
Enter that code with a username and a password of 15 to 64 characters. On Tomcat, read the code from the log file:
```bash
grep -o 'Setup code: [0-9A-Z]\{12\}' logs/catalina.out | tail -n 1 | cut -d' ' -f3
```
`mvn jetty:run` prints the line to the console.

**Sessions.** A session ends after 30 minutes without activity or 8 hours after sign-in. Set the idle limit with the context-param
`homesmart.idleMinutes` or the JVM option `-Dhomesmart.idleMinutes=5`.
Five failed sign-ins for one username from one address lock that pair for 15 minutes.

**Forms and scripts.** Every `POST` form must contain the token field, or the server answers 403:
```jsp
<%@ include file="/WEB-INF/views/csrf-field.jspf" %>
```
Pages set a Content-Security-Policy, so inline `<script>` and `style=""` attributes do not work. Put scripts in `js/` and styles in `css/`.

**Remote access.** Deploy the WAR to the `webapps` folder of a Tomcat 10.1 instance, run `tailscale serve --bg 8081` (use your Tomcat port) and open `https://<machine>.<tailnet>.ts.net/homesmart/` with the trailing slash.
`META-INF/context.xml` trusts the `X-Forwarded-*` headers only from the same machine, so `mvn jetty:run` and IDE-managed Tomcat servers, which skip that file, are for this machine only.
Opening the Tomcat port to the local network instead is plain HTTP, so the password and the session cookie cross the network unencrypted.

## Release 2 – Frontend & Integration (Mahad)

Updated the JSP dashboard with Phosphor SVG icons, light/dark themes, and improved styling while keeping the original layout.

Added frontend support for the thermostat's Eco Mode button. The dashboard uses Java servlets to process device actions and displays updated device statuses and activity logs.

Tested the dashboard locally with Jetty and confirmed the MySQL connection.

## Release 2 – Backend & device controls (Omer)

Tasks: verify thermostat validation and error handling; make sure all three device controls communicate with MySQL
correctly; add a thermostat preset button (Eco Mode at 20 °C).

### What changed

- **Eco Mode (new feature).** The thermostat card has an *Eco mode · 20 °C* button. It posts `action=eco`;
  `DeviceService.setEcoMode()` checks the device is a thermostat, sets 20 °C and saves it through the DAO. The status
  reads "20 °C · Eco" and the button shows *Eco mode on* (disabled) while the thermostat is at 20 °C. The command
  appears in the activity log as "Eco mode: set temperature to 20 °C". 20 °C is the setting Natural Resources Canada
  recommends while people are awake and at home ([Smart thermostats](https://natural-resources.canada.ca/energy-efficiency/energy-star/smart-thermostats)).
- **Bug fix: a bad temperature in MySQL crashed the dashboard.** The `temperature` column (DECIMAL(4,1)) does not
  enforce the 10–32 °C range. A thermostat row holding 40, 32.5, 9.4 or NULL (e.g. after editing the table by hand) made
  `GET /devices` fail with HTTP 500 for all three devices, and the default error page showed the Java stack trace.
  `MySqlDeviceDAO` now rounds the stored value, keeps it inside 10–32 °C, uses 20 °C (Eco) when it is empty, and logs a
  warning such as `Thermostat has an out-of-range saved temperature (40.0 °C); using 32 °C`. The next temperature
  change saves a valid value again.
- **Friendly error page.** `web.xml` maps 404, 500 and any uncaught exception to `WEB-INF/views/error.jsp`.
- **Clearer error messages.** Every invalid command is still refused and nothing is saved; only the wording changed:

  | Request | Release 1 message | Release 2 message |
  |---|---|---|
  | `increase` at 32 °C / `decrease` at 10 °C | Temperature must be between 10 and 32 °C | Thermostat is already at the maximum of 32 °C / minimum of 10 °C |
  | `set` with an empty or missing value | Temperature must be a whole number | Enter a target temperature |
  | `set` with `99999999999` | Temperature must be a whole number | Temperature must be between 10 and 32 °C |
  | `set` with `warm` sent to the light | Temperature must be a whole number | Living Room Light does not support this action |

### Testing results (18 Nov 2026)

Run on macOS with Java 26 (compiling for Java 17), Maven 3.9.11, Jetty 12 (`mvn jetty:run`) and MySQL 9.7.1 loaded
from `src/homesmart.sql`.

| Layer | Tests | Result |
|---|---|---|
| Unit – `DeviceServiceTest` (logic on sample data) | 23 (8 new: Eco Mode, limit messages, value messages, check order) | all pass |
| Integration – `MySqlDeviceDAOTest` (real MySQL) | 14 (8 new: all 9 commands read back by a fresh DAO as after a restart, Eco pressed twice, 6 bad stored values) | all pass |
| Unit – `ActivityLogTest` | 2 | all pass |
| End-to-end – `scripts/http-smoke-test.sh` against the running app | 35 checks | 35 passed, 0 failed |

`mvn test` runs 39 tests (23 at the start of Release 2). Without MySQL the 14 integration tests are skipped.

**All three devices ↔ MySQL.** Each command was sent through the dashboard's HTTP form and the row checked in MySQL
(`power_on | temperature | locked`):

| Command | Dashboard message | `devices` row afterwards |
|---|---|---|
| Light `on` / `off` / `toggle` | Living Room Light: Turned on / Turned off / Turned on | `1` / `0` / `1` |
| Thermostat `set 23`, `increase`, `decrease` | Set temperature to 23 / 24 / 23 °C | `23.0` / `24.0` / `23.0` |
| Thermostat `eco` | Eco mode: set temperature to 20 °C | `20.0` |
| Door lock `unlock` / `lock` | Front Door Lock: Unlocked / Locked | `0` / `1` |
| Rejected: `set 40`, `eco` on the light | error message | unchanged |

**Bad value saved in MySQL** (thermostat row edited directly):

| Saved value | Release 1 | Release 2 |
|---|---|---|
| 40 | HTTP 500 + stack trace | page loads, shows 32 °C, warning logged |
| 32.5 / 9.4 | HTTP 500 + stack trace | page loads, shows 32 °C / 10 °C, warning logged |
| NULL | HTTP 500 (NullPointerException) | page loads, shows 20 °C, warning logged |
| 21.5 | shows 22 °C | shows 22 °C (unchanged) |

**Restart and outage (manual):**
- Set light off, 25 °C, door unlocked → stopped and restarted the app → the dashboard showed Off, 25 °C, Unlocked.
- Stopped MySQL with the app running → `GET /devices` returned 200 with "Could not reach the database…" and no Java
  details; an `eco` command sent meanwhile showed the same message and changed nothing.
- Started MySQL again → the dashboard showed the devices again without restarting the app.

### Notes for the team

- **Database (Alfonso):** Eco is a normal `UPDATE devices SET temperature = 20`; no schema change. If `last_updated`
  uses `ON UPDATE CURRENT_TIMESTAMP`, MySQL only bumps it when a value really changes (e.g. not for Eco at 20 °C).
- **Activity log and testing (Matt):** Eco is logged as "Eco mode: set temperature to 20 °C".
  `scripts/http-smoke-test.sh` re-runs the 35 end-to-end checks; it reads the message from the
  `alert alert-success` / `alert alert-error` boxes, so update `flash_message()` there if that markup changes.

