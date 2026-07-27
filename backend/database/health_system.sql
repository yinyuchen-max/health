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
-- Current Database: `health_system`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `health_system` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `health_system`;

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
INSERT INTO `health_record` VALUES (1,1,120.00,80.00,72,5.50,70.00,'2024-01-01','正常血压','2026-03-18 15:39:05',0),(2,2,115.00,75.00,68,5.20,75.00,'2024-01-01','健康状态良好','2026-03-18 15:39:05',0),(3,1,70.00,42.00,54,2.40,65.00,'2026-04-08','','2026-04-14 15:29:14',0),(4,1,60.00,40.00,78,3.00,64.00,'2026-04-07','','2026-04-14 15:34:20',0),(5,1,123.00,61.00,67,4.30,60.00,'2026-04-01','五','2026-04-17 16:43:16',0),(6,1,120.00,80.00,72,5.50,70.00,'2024-01-01','正常血压','2026-04-17 16:51:53',0),(7,2,115.00,75.00,68,5.20,75.00,'2024-01-01','健康状态良好','2026-04-17 16:51:53',0),(8,1,120.00,80.00,72,5.50,65.00,'2024-01-01','正常血压','2026-04-17 17:28:12',0),(9,2,115.00,75.00,68,5.20,75.00,'2024-01-01','健康状态良好','2026-04-17 17:28:12',0),(10,1,111.00,70.00,111,30.00,20.00,'2026-04-20','11','2026-04-20 15:06:24',0),(11,3,110.00,70.00,78,3.40,65.00,'2026-04-02','wu','2026-04-21 10:43:09',0),(12,3,120.00,70.00,89,2.20,60.00,'2026-04-21','无不适','2026-04-21 11:00:13',0),(13,7,60.00,41.00,47,2.10,20.50,'2026-04-21','e','2026-04-21 14:14:37',0),(14,1,120.00,80.00,72,5.50,70.00,'2024-01-01','正常血压','2026-04-24 16:30:53',0),(15,2,115.00,75.00,68,5.20,75.00,'2024-01-01','健康状态良好','2026-04-24 16:30:53',0);
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
INSERT INTO `history_record` VALUES (1,1,'health',NULL,'体重记录','体重: 70','2024-01-01 00:00:00','2026-04-17 16:51:53','2026-04-17 16:51:53',0),(2,1,'sport',NULL,'跑步运动','晨跑30分钟，消耗300卡路里','2024-01-01 18:30:00','2026-04-17 16:51:53','2026-04-17 16:51:53',0),(3,1,'reminder',NULL,'血压测量提醒','已完成早间血压测量，结果 120/80 mmHg','2024-01-02 09:00:00','2026-04-17 16:51:53','2026-04-17 16:51:53',0),(4,2,'health',NULL,'血压记录','血压: 115/75 mmHg，心率: 68 次/分','2024-01-01 07:45:00','2026-04-17 16:51:53','2026-04-17 16:51:53',0),(5,2,'sport',NULL,'游泳运动','自由泳45分钟，消耗450卡路里','2024-01-01 19:00:00','2026-04-17 16:51:53','2026-04-17 16:51:53',0),(6,1,'health',NULL,'体重记录','体重: 70kg，血糖: 5.5 mmol/L','2024-01-01 08:00:00','2026-04-17 17:28:12','2026-04-17 17:28:12',0),(7,1,'sport',NULL,'跑步运动','晨跑30分钟，消耗300卡路里','2024-01-01 18:30:00','2026-04-17 17:28:12','2026-04-17 17:28:12',0),(8,1,'reminder',NULL,'血压测量提醒','已完成早间血压测量，结果 120/80 mmHg','2024-01-02 09:00:00','2026-04-17 17:28:12','2026-04-17 17:28:12',0),(9,2,'health',NULL,'血压记录','血压: 115/75 mmHg，心率: 68 次/分','2024-01-01 07:45:00','2026-04-17 17:28:12','2026-04-17 17:28:12',0),(10,2,'sport',NULL,'游泳运动','自由泳45分钟，消耗450卡路里','2024-01-01 19:00:00','2026-04-17 17:28:12','2026-04-17 17:28:12',0),(11,3,'health',12,'Health Record 2026-04-21',', HR 89 bpm, Blood Sugar 2.2 mmol/L, Weight 60.0 kg; Notes: 无不适','2026-04-21 00:00:00','2026-04-21 11:00:13','2026-04-21 11:00:13',0),(12,3,'sport',11,'Sport Record 2026-04-21','瑜伽, Duration 90 min, Intensity High, Calories 270.0 kcal; Notes: 无','2026-04-21 00:00:00','2026-04-21 11:05:51','2026-04-21 11:05:50',0),(13,7,'health',13,'Health Record 2026-04-21','BP 60.0/41.0 mmHg, HR 47 bpm, Blood Sugar 2.1 mmol/L, Weight 20.5 kg; Notes: e','2026-04-21 00:00:00','2026-04-21 14:14:37','2026-04-21 14:14:37',0),(14,7,'sport',12,'Sport Record 2026-04-21','骑车, Duration 1 min, Intensity Low, Calories 1.0 kcal','2026-04-21 00:00:00','2026-04-21 14:15:10','2026-04-21 14:15:09',0),(15,1,'health',NULL,'体重记录','','2024-01-01 00:00:00','2026-04-24 16:30:53','2026-04-24 16:30:53',0),(16,1,'sport',NULL,'跑步运动','晨跑30分钟，消耗300卡路里','2024-01-01 18:30:00','2026-04-24 16:30:53','2026-04-24 16:30:53',0),(17,1,'reminder',NULL,'血压测量提醒','已完成早间血压测量，结果 120/80 mmHg','2024-01-02 09:00:00','2026-04-24 16:30:53','2026-04-24 16:30:53',0),(18,2,'health',NULL,'血压记录','血压: 115/75 mmHg，心率: 68 次/分','2024-01-01 07:45:00','2026-04-24 16:30:53','2026-04-24 16:30:53',0),(19,2,'sport',NULL,'游泳运动','自由泳45分钟，消耗450卡路里','2024-01-01 19:00:00','2026-04-24 16:30:53','2026-04-24 16:30:53',0),(20,1,'health',10,'Health Record 2026-04-20','BP 111.0/70.0 mmHg, HR 111 bpm, Blood Sugar 30 mmol/L, Weight 20.0 kg; Notes: 11','2026-04-20 00:00:00','2026-04-29 11:20:50','2026-04-29 11:20:49',0),(21,1,'health',5,'Health Record 2026-04-01','BP 123.0/61.0 mmHg, HR 67 bpm, Blood Sugar 4.3 mmol/L, Weight 60.0 kg; Notes: 五','2026-04-01 00:00:00','2026-04-29 11:21:02','2026-04-29 11:21:01',0);
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
INSERT INTO `sport_record` VALUES (1,1,'跑步',30,300.00,'medium','2024-01-01','晨跑30分钟','2026-03-18 15:39:05',0),(2,2,'游泳',45,450.00,'high','2024-01-01','自由泳45分钟','2026-03-18 15:39:05',0),(3,1,'游泳',1,12.00,'low','2026-04-01','','2026-04-14 15:43:15',0),(4,1,'游泳',66,792.00,'high','2026-04-14','','2026-04-14 15:44:20',0),(5,1,'游泳',32,384.00,'medium','2026-04-15','','2026-04-14 15:48:21',0),(6,1,'瑜伽',44,132.00,'medium','2026-04-15','','2026-04-14 15:52:59',0),(7,1,'跑步',30,300.00,'medium','2024-01-01','晨跑30分钟','2026-04-17 16:51:53',0),(8,2,'游泳',45,450.00,'high','2024-01-01','自由泳45分钟','2026-04-17 16:51:53',0),(9,1,'跑步',30,300.00,'medium','2024-01-01','晨跑30分钟','2026-04-17 17:28:12',0),(10,2,'游泳',45,450.00,'high','2024-01-01','自由泳45分钟','2026-04-17 17:28:12',0),(11,3,'瑜伽',90,270.00,'high','2026-04-21','无','2026-04-21 11:05:51',0),(12,7,'骑车',1,1.00,'low','2026-04-21','','2026-04-21 14:15:10',0),(13,1,'跑步',30,300.00,'medium','2024-01-01','晨跑30分钟','2026-04-24 16:30:53',0),(14,2,'游泳',45,450.00,'high','2024-01-01','自由泳45分钟','2026-04-24 16:30:53',0);
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
INSERT INTO `sys_user` VALUES (1,'admin','123456','admin@health.com','138000000001',1,23,175.00,70.00,NULL,1,'2026-03-18 15:39:05','2026-04-15 10:27:52',0),(2,'user1','456','user1@health.com','13800000001',1,28,180.00,75.00,NULL,1,'2026-03-18 15:39:05','2026-04-15 15:30:14',0),(3,'yyc','123','ycy66165@gmail.com','16783743726',1,43,185.00,70.00,NULL,1,'2026-04-15 15:29:10','2026-04-24 14:07:10',0),(7,'1321','111','18663566210@163.com','1',1,4,58.00,20.00,NULL,0,'2026-04-21 14:12:54','2026-04-24 17:50:49',0);
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
