USE `health_system`;

CREATE TABLE IF NOT EXISTS `doctor_appointment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '系统用户ID，未登录时为空',
  `patient_name` varchar(50) NOT NULL COMMENT '姓名',
  `age` int NOT NULL COMMENT '年龄',
  `appointment_time` datetime NOT NULL COMMENT '预约时间',
  `phone` varchar(20) NOT NULL COMMENT '用户电话',
  `department` varchar(50) NOT NULL COMMENT '预约科室',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_appointment_user_id` (`user_id`),
  KEY `idx_appointment_time` (`appointment_time`),
  KEY `idx_appointment_phone` (`phone`),
  CONSTRAINT `doctor_appointment_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生预约表';
