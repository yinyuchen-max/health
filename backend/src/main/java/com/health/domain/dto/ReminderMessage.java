package com.health.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 提醒邮件消息（通过 RabbitMQ 传递）
 *
 * 只传 ID，消费者端再查完整数据，避免消息体过大。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderMessage implements Serializable {

    /** 用户ID */
    private Long userId;

    /** 提醒偏好ID */
    private Long preferenceId;

    /** 提醒类型：bloodPressure, bloodSugar, weight, exercise */
    private String type;

    /** 提醒时间（HH:mm），用于日志追踪 */
    private String scheduledTime;
}
