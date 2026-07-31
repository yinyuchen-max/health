package com.health.service;

import com.health.common.config.RabbitMQConfig;
import com.health.domain.dto.ReminderMessage;
import com.health.domain.entity.ReminderPreference;
import com.health.domain.entity.User;
import com.health.mapper.ReminderPreferenceMapper;
import com.health.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 提醒邮件消费者（从 RabbitMQ 队列消费消息并发送邮件）
 *
 * 特性：
 * - 自动重试 3 次（application.yml 配置），间隔 3s → 6s → 12s
 * - 重试全部失败后消息进入死信队列（reminder.email.dead.queue）
 * - 支持并发消费（2-5 个线程）
 */
@Component
public class ReminderEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailConsumer.class);

    private final ReminderEmailService emailService;
    private final UserMapper userMapper;
    private final ReminderPreferenceMapper preferenceMapper;

    public ReminderEmailConsumer(ReminderEmailService emailService,
                                 UserMapper userMapper,
                                 ReminderPreferenceMapper preferenceMapper) {
        this.emailService = emailService;
        this.userMapper = userMapper;
        this.preferenceMapper = preferenceMapper;
    }

    /**
     * 监听邮件队列，收到消息后发送邮件
     *
     * @param message 提醒消息（从 MQ 反序列化）
     */
    @RabbitListener(queues = RabbitMQConfig.REMINDER_EMAIL_QUEUE)
    public void handleReminderEmail(ReminderMessage message) {
        log.info("收到提醒消息: userId={}, preferenceId={}, type={}, scheduledTime={}",
                message.getUserId(), message.getPreferenceId(),
                message.getType(), message.getScheduledTime());

        // 查询完整数据
        User user = userMapper.selectById(message.getUserId());
        if (user == null) {
            log.error("消费者: 用户不存在, userId={}", message.getUserId());
            return; // 不抛异常，避免无限重试
        }

        ReminderPreference preference = preferenceMapper.selectById(message.getPreferenceId());
        if (preference == null) {
            log.error("消费者: 提醒偏好不存在, preferenceId={}", message.getPreferenceId());
            return;
        }

        // 发送邮件（同步，因为 RabbitMQ 消费者本身已经是异步的）
        emailService.sendReminderEmail(user, preference);

        log.info("提醒邮件处理完成: userId={}, username={}, type={}",
                user.getId(), user.getUsername(), message.getType());
    }
}
