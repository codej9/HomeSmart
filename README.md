# HomeSmart – Release 1

Java/JSP web application (Jakarta Servlet 6, Tomcat 10.1, Java 17+).
Release 1 covers three devices: a light, a thermostat and a door lock.

## Dashboard

Release 1 includes a functional JSP dashboard with CSS styling and working controls for the light, thermostat, and door lock. Device statuses update after each action.

The dashboard is connected to the team's MySQL `devices` table (`src/homesmart.sql`): every light, thermostat and door lock
change is saved there and is still there after the server restarts. When the database is not set up, the app runs on sample data instead.

## Database setup (MySQL)

Run these from the `HomeSmart` folder. Each `mysql` command asks for your MySQL root password.

1. Create the database and a MySQL login for the app (user `homesmart`, password `homesmart`):
   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS homesmart; CREATE USER IF NOT EXISTS 'homesmart'@'localhost' IDENTIFIED BY 'homesmart'; GRANT ALL PRIVILEGES ON homesmart.* TO 'homesmart'@'localhost';"
   ```
2. Import the `devices` table and its starting data (this replaces an existing `devices` table):
   ```bash
   mysql -u root -p homesmart < src/homesmart.sql
   ```
3. Create your settings file. The example already uses the login from step 1, so a plain copy works:
   ```bash
   cp src/main/resources/db.properties.example src/main/resources/db.properties
   ```
   (On Windows: `copy src\main\resources\db.properties.example src\main\resources\db.properties`.)
   To use a different MySQL user, edit `db.user` and `db.password` in that file.
4. Start the app (see below). The console should print `[HomeSmart] Connected to MySQL at ...`.

If `db.properties` is missing or MySQL is not running, the app still starts using in-memory sample data,
logs a warning, and shows a notice on the page. If MySQL stops while the app is running, the dashboard shows
"Could not reach the database" instead of an error page; start MySQL again and refresh.

## Run it

```bash
mvn package          # compiles, runs the tests, builds target/homesmart.war
mvn jetty:run        # quick local run at http://localhost:8080/homesmart (no Tomcat needed)
```

For Tomcat 10.1, deploy `target/homesmart.war` (or add it as an artifact in IntelliJ's Tomcat run configuration).

`DeviceServiceTest` and `InMemoryDeviceTimestampTest` check device logic and timestamps on sample data.
`ActivityLogTest` checks the log order and size limit. `MySqlDeviceDAOTest` checks state and timestamp persistence
using a fresh DAO for reads and restores the original values after each test. It is skipped unless
`HOMESMART_TEST_DB_URL` is set. Set that URL and `HOMESMART_TEST_DB_USER` and `HOMESMART_TEST_DB_PASSWORD` for a
disposable test database with the current schema and seed rows. The test account needs SELECT, UPDATE and
CREATE TEMPORARY TABLES privileges.

For an existing installation, apply `src/migrations/001_add_device_last_updated.sql` before deploying this version.
It adds a nullable column and preserves the existing rows. Each successful device command saves its update time in
UTC with microsecond precision, including commands that request the current state.

## Structure

| Package | Purpose |
|---|---|
| `model` | `Device` base class and `Light`, `Thermostat`, `DoorLock` |
| `dao` | `DeviceDAO` storage interface; `MySqlDeviceDAO` (MySQL `devices` table) and `InMemoryDeviceDAO` (sample data fallback) |
| `service` | `DeviceService` – all device control logic and validation; `CommandResult`; `ActivityLog` (in-memory command history) |
| `servlet` | `DeviceServlet` at `/devices`; `ActivityServlet` at `/activity` |
| `listener` | `AppContextListener` – reads `db.properties`, picks MySQL or sample data, creates the shared `DeviceService` and `ActivityLog` |

## Backend contract (for the dashboard, database and activity log)

**GET `/devices`** forwards to `WEB-INF/views/devices.jsp` with these request attributes:

- `devices` – list of devices. Every device has `id`, `name`, `type` (`LIGHT`, `THERMOSTAT`, `DOOR_LOCK`) and `status` (e.g. "On", "21 °C", "Locked").
  Lights also have `on`; thermostats have `temperature`, `minTemperature`, `maxTemperature`; door locks have `locked`.
- `flashMessage` / `flashError` – result of the last command (shown once).
- `activity` – the 5 most recent commands as `CommandResult` items, newest first.

**POST `/devices`** with form fields:

| Device | `action` | `value` |
|---|---|---|
| Light | `on`, `off`, `toggle` | – |
| Thermostat | `increase`, `decrease` (±1 °C), `set` | target °C for `set` (10–32) |
| Door lock | `lock`, `unlock` | – |

plus `deviceId`. The servlet redirects back to `GET /devices` afterwards.

**Database:** `MySqlDeviceDAO` reads and updates the `devices` table from `homesmart.sql`
(`power_on` for lights, `temperature` for thermostats, `locked` for door locks).

**Activity log:** every successful command is recorded as a `CommandResult(deviceName, action, timestamp)`.
The log is kept in memory, holds the last 200 commands, and clears when the server restarts.

**GET `/activity`** forwards to `WEB-INF/views/activity.jsp` with `activity`, the full log, newest first.

