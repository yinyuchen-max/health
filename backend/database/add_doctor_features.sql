USE `health_system`;

-- =============================================
-- 医生端功能扩展
-- =============================================

-- 1. 医生信息表（与 sys_user 关联，需审核）
CREATE TABLE IF NOT EXISTS `doctor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联 sys_user',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `hospital` varchar(100) NOT NULL COMMENT '所属医院',
  `department` varchar(50) NOT NULL COMMENT '科室',
  `title` varchar(30) DEFAULT NULL COMMENT '职称：主任医师/副主任医师/主治医师/住院医师',
  `specialization` varchar(200) DEFAULT NULL COMMENT '擅长领域',
  `license_number` varchar(50) NOT NULL COMMENT '执业证书编号',
  `introduction` text COMMENT '个人简介',
  `status` enum('pending','approved','rejected') DEFAULT 'pending' COMMENT '审核状态',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `approved_by` bigint DEFAULT NULL COMMENT '审核人（admin user_id）',
  `approved_at` datetime DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_department` (`department`),
  CONSTRAINT `doctor_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生信息表';

-- 2. 医患对话表
CREATE TABLE IF NOT EXISTS `doctor_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doctor_id` bigint NOT NULL COMMENT '医生ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `sender_type` enum('user','doctor') NOT NULL COMMENT '发送者类型',
  `content` text NOT NULL COMMENT '消息内容',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_doctor_user` (`doctor_id`, `user_id`),
  KEY `idx_sender` (`sender_id`, `sender_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `doctor_message_ibfk_1` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE CASCADE,
  CONSTRAINT `doctor_message_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医患对话消息表';

-- 3. 预约表增加医生字段
ALTER TABLE `doctor_appointment`
  ADD COLUMN `doctor_id` bigint DEFAULT NULL COMMENT '医生ID' AFTER `department`,
  ADD COLUMN `status` enum('pending','confirmed','completed','cancelled') DEFAULT 'pending' COMMENT '预约状态' AFTER `doctor_id`,
  ADD KEY `idx_doctor_id` (`doctor_id`);

-- 注意：外键约束需要 doctor 表已存在才能添加
-- ALTER TABLE `doctor_appointment`
--   ADD CONSTRAINT `doctor_appointment_ibfk_2` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE SET NULL;
