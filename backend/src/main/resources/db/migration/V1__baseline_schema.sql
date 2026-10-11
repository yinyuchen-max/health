-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: health_system
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


--
-- Table structure for table `health_record`
--

DROP TABLE IF EXISTS `health_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `health_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `blood_pressure_systolic` decimal(5,2) DEFAULT NULL COMMENT '收缩压',
  `blood_pressure_diastolic` decimal(5,2) DEFAULT NULL COMMENT '舒张压',
  `heart_rate` int DEFAULT NULL COMMENT '心率',
  `blood_sugar` decimal(6,2) DEFAULT NULL COMMENT '血糖',
  `weight` decimal(5,2) DEFAULT NULL COMMENT '体重',
  `record_date` date NOT NULL,
  `notes` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_record_date` (`record_date`),
  CONSTRAINT `health_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `health_record`
--

LOCK TABLES `health_record` WRITE;
/*!40000 ALTER TABLE `health_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `health_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `history_record`
--

DROP TABLE IF EXISTS `history_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `history_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL COMMENT 'health, sport, reminder',
  `source_record_id` bigint DEFAULT NULL COMMENT 'source record id',
  `title` varchar(200) NOT NULL,
  `content` text,
  `record_date` datetime NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_record_date` (`record_date`),
  KEY `idx_source_record_id` (`source_record_id`),
  CONSTRAINT `history_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `history_record`
--

LOCK TABLES `history_record` WRITE;
/*!40000 ALTER TABLE `history_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `history_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reminder_history`
--

DROP TABLE IF EXISTS `reminder_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reminder_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `preference_id` bigint NOT NULL,
  `action_type` varchar(50) NOT NULL,
  `scheduled_time` datetime NOT NULL,
  `completed_time` datetime DEFAULT NULL,
  `completion_status` enum('completed','missed','snoozed') DEFAULT 'missed',
  `notes` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_preference_id` (`preference_id`),
  KEY `idx_completed_time` (`completed_time`),
  CONSTRAINT `reminder_history_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `reminder_history_ibfk_2` FOREIGN KEY (`preference_id`) REFERENCES `reminder_preference` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminder_history`
--

LOCK TABLES `reminder_history` WRITE;
/*!40000 ALTER TABLE `reminder_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `reminder_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reminder_notification`
--

DROP TABLE IF EXISTS `reminder_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reminder_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `preference_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL,
  `scheduled_time` datetime NOT NULL,
  `actual_time` datetime DEFAULT NULL,
  `completed` tinyint(1) DEFAULT '0',
  `read_status` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_scheduled_time` (`scheduled_time`),
  KEY `idx_completed` (`completed`),
  KEY `preference_id` (`preference_id`),
  CONSTRAINT `reminder_notification_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `reminder_notification_ibfk_2` FOREIGN KEY (`preference_id`) REFERENCES `reminder_preference` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminder_notification`
--

LOCK TABLES `reminder_notification` WRITE;
/*!40000 ALTER TABLE `reminder_notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `reminder_notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reminder_preference`
--

DROP TABLE IF EXISTS `reminder_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reminder_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL COMMENT 'bloodPressure, bloodSugar, weight, exercise',
  `time` varchar(5) NOT NULL COMMENT 'HH:mm format',
  `frequency` varchar(20) NOT NULL COMMENT 'daily, weekly, custom',
  `smart_mode` tinyint(1) DEFAULT '0',
  `enabled` tinyint(1) DEFAULT '1',
  `effectiveness_score` decimal(3,2) DEFAULT '0.00' COMMENT '0.00-1.00',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_type` (`type`),
  CONSTRAINT `reminder_preference_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒偏好设置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminder_preference`
--

LOCK TABLES `reminder_preference` WRITE;
/*!40000 ALTER TABLE `reminder_preference` DISABLE KEYS */;
/*!40000 ALTER TABLE `reminder_preference` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sport_record`
--

DROP TABLE IF EXISTS `sport_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sport_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `sport_type` varchar(50) NOT NULL COMMENT '运动类型',
  `duration` int NOT NULL COMMENT '运动时长(分钟)',
  `calories` decimal(8,2) DEFAULT NULL COMMENT '消耗卡路里',
  `intensity` enum('low','medium','high') NOT NULL COMMENT '强度',
  `record_date` date NOT NULL,
  `notes` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_record_date` (`record_date`),
  CONSTRAINT `sport_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运动记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sport_record`
--

LOCK TABLES `sport_record` WRITE;
/*!40000 ALTER TABLE `sport_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `sport_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `gender` tinyint DEFAULT '1' COMMENT '1-男, 2-女',
  `age` int DEFAULT NULL,
  `height` decimal(5,2) DEFAULT NULL COMMENT 'cm',
  `weight` decimal(5,2) DEFAULT NULL COMMENT 'kg',
  `avatar` varchar(255) DEFAULT NULL,
  `role` varchar(20) DEFAULT 'user' COMMENT 'admin-管理员, user-普通用户',
  `status` tinyint DEFAULT '1' COMMENT '0-禁用, 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'health_system'
--

--
-- Dumping routines for database 'health_system'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-18  8:54:24
