package com.health.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 *
 * 架构说明：
 *   Scheduler（生产者）→ reminder.exchange → reminder.email.queue → Consumer（发邮件）
 *                                              ↓ 失败重试3次
 *                                         reminder.email.dead.queue（死信队列，人工排查）
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 名称常量 ====================
    public static final String REMINDER_EXCHANGE = "reminder.exchange";
    public static final String REMINDER_EMAIL_QUEUE = "reminder.email.queue";
    public static final String REMINDER_EMAIL_ROUTING_KEY = "reminder.email";
    public static final String REMINDER_DEAD_QUEUE = "reminder.email.dead.queue";
    public static final String REMINDER_DEAD_ROUTING_KEY = "reminder.email.dead";

    // ==================== 正常队列 + 交换机 ====================

    /**
     * Topic 交换机：支持按提醒类型路由（未来扩展用）
     * 例如：reminder.email.bloodPressure 只投递血压类提醒
     */
    @Bean
    public TopicExchange reminderExchange() {
        return new TopicExchange(REMINDER_EXCHANGE, true, false);
    }

    /**
     * 邮件发送队列（持久化，服务器重启不丢失）
     */
    @Bean
    public Queue reminderEmailQueue() {
        return QueueBuilder.durable(REMINDER_EMAIL_QUEUE)
                .deadLetterExchange(REMINDER_EXCHANGE)        // 失败后路由到死信交换机
                .deadLetterRoutingKey(REMINDER_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定：队列 ← 交换机（routing key = reminder.email.#）
     * # 通配符：同时匹配 reminder.email 和 reminder.email.bloodPressure 等
     */
    @Bean
    public Binding reminderEmailBinding() {
        return BindingBuilder.bind(reminderEmailQueue())
                .to(reminderExchange())
                .with(REMINDER_EMAIL_ROUTING_KEY + ".#");
    }

    // ==================== 死信队列（失败消息堆积，方便排查） ====================

    @Bean
    public Queue reminderDeadQueue() {
        return QueueBuilder.durable(REMINDER_DEAD_QUEUE).build();
    }

    @Bean
    public Binding reminderDeadBinding() {
        return BindingBuilder.bind(reminderDeadQueue())
                .to(reminderExchange())
                .with(REMINDER_DEAD_ROUTING_KEY);
    }

    // ==================== 消息序列化 ====================

    /**
     * 使用 JSON 序列化（替代默认的 JDK 序列化，方便跨语言 + 可读性）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
