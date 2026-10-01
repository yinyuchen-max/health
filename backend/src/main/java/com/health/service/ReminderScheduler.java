package com.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.health.common.config.RabbitMQConfig;
import com.health.domain.dto.ReminderMessage;
import com.health.domain.entity.ReminderNotification;
import com.health.domain.entity.ReminderPreference;
import com.health.domain.entity.User;
import com.health.mapper.ReminderNotificationMapper;
import com.health.mapper.ReminderPreferenceMapper;
import com.health.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提醒定时调度器（消息生产者）
 *
 * 职责：
 * 1. 每分钟扫描匹配的提醒偏好
 * 2. Redis 去重 + 频率判断
 * 3. 记录通知到数据库
 * 4. 发送消息到 RabbitMQ（由消费者异步发邮件）
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SENT_KEY_PREFIX = "reminder:sent:";

    private final ReminderPreferenceMapper preferenceMapper;
    private final ReminderNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    /**
     * 阿里手册：禁止使用 Executors 创建线程池，改用 ScheduledThreadPoolExecutor 明确参数
     * 线程命名格式：health-reminder-{序号}
     */
    private final ScheduledExecutorService sendScheduler = new ScheduledThreadPoolExecutor(2,
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "health-reminder-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    /** 每条消息之间的发送间隔（秒） */
    private static final int SEND_INTERVAL_SECONDS = 20;

    @Value("${reminder.scheduler.enabled:false}")
    private boolean enabled;

    public ReminderScheduler(ReminderPreferenceMapper preferenceMapper,
                             ReminderNotificationMapper notificationMapper,
                             UserMapper userMapper,
                             StringRedisTemplate redisTemplate,
                             RabbitTemplate rabbitTemplate) {
        this.preferenceMapper = preferenceMapper;
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 每分钟执行一次，扫描匹配的提醒偏好
     */
    @Scheduled(fixedRateString = "${reminder.scheduler.check-interval-ms:60000}")
    public void checkAndSendReminders() {
        if (!enabled) {
            return;
        }

        String currentTime = LocalTime.now().format(TIME_FMT);
        String today = LocalDate.now().format(DATE_FMT);

        QueryWrapper<ReminderPreference> query = new QueryWrapper<>();
        query.eq("enabled", 1)
                .eq("time", currentTime)
                .eq("deleted", 0);

        List<ReminderPreference> preferences = preferenceMapper.selectList(query);
        if (preferences.isEmpty()) {
            return;
        }

        log.info("提醒调度: 当前时间={}, 匹配到 {} 条提醒偏好", currentTime, preferences.size());

        int delayIndex = 0;
        for (ReminderPreference pref : preferences) {
            // 每条消息延时投递到 MQ：第0条立即发，第1条20秒后，第2条40秒后...
            int delaySeconds = delayIndex * SEND_INTERVAL_SECONDS;
            sendScheduler.schedule(() -> {
                try {
                    processReminder(pref, today, currentTime);
                } catch (Exception e) {
                    log.error("处理提醒失败: preferenceId={}, userId={}", pref.getId(), pref.getUserId(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);
            delayIndex++;
        }
    }

    private void processReminder(ReminderPreference pref, String today, String currentTime) {
        // 频率检查
        if (!shouldSendToday(pref)) {
            return;
        }

        // Redis 去重（先占位，防止多实例/多次扫描重复投递）
        String deduplicateKey = SENT_KEY_PREFIX + pref.getId() + ":" + today;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(deduplicateKey, "1", 25, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            log.debug("提醒已发送过，跳过: preferenceId={}, date={}", pref.getId(), today);
            return;
        }

        Long notificationId = null;
        boolean delivered = false;
        try {
            // 查询用户信息
            User user = userMapper.selectById(pref.getUserId());
            if (user == null || user.getStatus() == 0) {
                log.warn("用户不存在或已禁用，跳过: userId={}", pref.getUserId());
                return;
            }

            // 记录通知到数据库
            ReminderNotification notification = new ReminderNotification();
            notification.setUserId(user.getId());
            notification.setPreferenceId(pref.getId());
            notification.setType(pref.getType());
            notification.setScheduledTime(LocalDateTime.now());
            notification.setActualTime(LocalDateTime.now());
            notification.setCompleted(false);
            notification.setReadStatus(false);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());
            notification.setDeleted(0);
            notificationMapper.insert(notification);
            notificationId = notification.getId();

            // ★ 发送消息到 RabbitMQ（由消费者异步处理邮件）
            ReminderMessage message = new ReminderMessage(
                    user.getId(),
                    pref.getId(),
                    pref.getType(),
                    currentTime
            );

            // 使用 Topic 交换机，按类型路由（未来可针对不同类型单独消费）
            String routingKey = RabbitMQConfig.REMINDER_EMAIL_ROUTING_KEY + "." + pref.getType();
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REMINDER_EXCHANGE,
                    routingKey,
                    message
            );
            delivered = true;

            log.info("提醒消息已投递MQ: userId={}, type={}, routingKey={}, preferenceId={}",
                    user.getId(), pref.getType(), routingKey, pref.getId());
        } finally {
            if (!delivered) {
                // 补偿：用户无效 / 通知落库失败 / MQ 投递失败时，
                // 回滚已写入的通知记录并释放去重 key。
                // 否则 dedup key 被占用 25 小时，而调度按 HH:mm 精确匹配当天不会再扫到，
                // 该提醒当天将永久丢失且无法重试。
                if (notificationId != null) {
                    try {
                        notificationMapper.deleteById(notificationId);
                    } catch (Exception ex) {
                        log.error("补偿删除通知记录失败: notificationId={}", notificationId, ex);
                    }
                }
                try {
                    redisTemplate.delete(deduplicateKey);
                } catch (Exception ex) {
                    log.error("补偿释放去重key失败: key={}", deduplicateKey, ex);
                }
            }
        }
    }

    private boolean shouldSendToday(ReminderPreference pref) {
        String frequency = pref.getFrequency();
        if (frequency == null) {
            return true;
        }

        return switch (frequency) {
            case "daily" -> true;
            case "weekly" -> LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
            case "custom" -> {
                long dayOfYear = LocalDate.now().getDayOfYear();
                yield (dayOfYear % 2) == (pref.getId() % 2);
            }
            default -> true;
        };
    }
}
