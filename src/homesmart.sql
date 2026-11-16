-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: homesmart
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `devices`
--

DROP TABLE IF EXISTS `devices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `devices` (
  `device_id` int NOT NULL,
  `device_name` varchar(50) NOT NULL,
  `device_type` enum('LIGHT','THERMOSTAT','DOOR_LOCK') NOT NULL,
  `power_on` tinyint(1) DEFAULT NULL,
  `temperature` decimal(4,1) DEFAULT NULL,
  `locked` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `devices`
--

LOCK TABLES `devices` WRITE;
/*!40000 ALTER TABLE `devices` DISABLE KEYS */;
INSERT INTO `devices` VALUES (1,'Living Room Light','LIGHT',1,NULL,NULL),(2,'Thermostat','THERMOSTAT',NULL,24.0,NULL),(3,'Front Door Lock','DOOR_LOCK',NULL,NULL,0);
/*!40000 ALTER TABLE `devices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `activity_log`
-- One row per device state change, plus door alerts. The log starts empty.
--

DROP TABLE IF EXISTS `activity_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `device_id` int NOT NULL,
  `action` varchar(100) NOT NULL,
  `activity_type` varchar(30) NOT NULL,
  `value` int DEFAULT NULL,
  `source` varchar(10) NOT NULL DEFAULT 'USER',
  `detail` varchar(100) DEFAULT NULL,
  `ref_log_id` int DEFAULT NULL,
  `logged_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`log_id`),
  UNIQUE KEY `uq_activity_log_ref` (`ref_log_id`),
  KEY `idx_activity_log_logged_at` (`logged_at`),
  KEY `idx_activity_log_device_time` (`device_id`,`logged_at`,`log_id`),
  CONSTRAINT `fk_activity_log_device` FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`),
  CONSTRAINT `fk_activity_log_ref` FOREIGN KEY (`ref_log_id`) REFERENCES `activity_log` (`log_id`),
  CONSTRAINT `chk_activity_log_source` CHECK (`source` in ('USER','SCHEDULE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schedules`
-- Commands that run at a set time on the chosen days. No starting rows.
-- The CHECK on `value` is table-level because MySQL rejects a column-level CHECK that names another column.
--

DROP TABLE IF EXISTS `schedules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedules` (
  `schedule_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `device_id` int NOT NULL,
  `activity_type` varchar(30) NOT NULL,
  `value` int DEFAULT NULL,
  `run_at` time NOT NULL,
  `days` varchar(27) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `last_run_on` date DEFAULT NULL,
  `last_result` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`schedule_id`),
  KEY `fk_schedules_device` (`device_id`),
  CONSTRAINT `fk_schedules_device` FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`),
  CONSTRAINT `chk_schedules_type` CHECK (`activity_type` in ('TURN_ON','TURN_OFF','LOCK','SET_TEMPERATURE')),
  CONSTRAINT `chk_schedules_value` CHECK ((((`activity_type` = 'SET_TEMPERATURE') and (`value` is not null) and (`value` between 10 and 32)) or ((`activity_type` <> 'SET_TEMPERATURE') and (`value` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-06 14:12:01
