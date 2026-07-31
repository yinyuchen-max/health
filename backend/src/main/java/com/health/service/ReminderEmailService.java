package com.health.service;

import com.health.domain.entity.ReminderPreference;
import com.health.domain.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReminderEmailService {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public ReminderEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送提醒邮件（由 RabbitMQ 消费者调用，本身已经是异步的）
     */
    public void sendReminderEmail(User user, ReminderPreference preference) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("用户 {} 没有配置邮箱，跳过邮件发送", user.getUsername());
            return;
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("邮件发送方未配置（spring.mail.username），跳过邮件发送");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(buildSubject(preference));

            String htmlContent = buildHtmlContent(user, preference);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("提醒邮件发送成功: to={}, type={}", user.getEmail(), preference.getType());

        } catch (MessagingException e) {
            log.error("提醒邮件发送失败: to={}, error={}", user.getEmail(), e.getMessage(), e);
        }
    }

    private String buildSubject(ReminderPreference preference) {
        String typeName = getTypeName(preference.getType());
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return "【健康管理】" + typeName + " - " + date;
    }

    private String buildHtmlContent(User user, ReminderPreference preference) {
        String typeName = getTypeName(preference.getType());
        String greeting = getGreeting();
        String tip = getHealthTip(preference.getType());
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background-color:#f4f7fa;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f7fa;padding:30px 0;">
            <tr><td align="center">
            <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <tr><td style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:30px 40px;text-align:center;">
                    <h1 style="color:#ffffff;margin:0;font-size:24px;">🏥 健康管理系统</h1>
                </td></tr>
                <tr><td style="padding:30px 40px 10px;">
                    <h2 style="color:#333;margin:0;font-size:20px;">%s，%s！</h2>
                </td></tr>
                <tr><td style="padding:10px 40px 20px;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f5ff;border-radius:8px;border-left:4px solid #667eea;">
                    <tr><td style="padding:20px 24px;">
                        <h3 style="color:#667eea;margin:0 0 8px;font-size:18px;">%s</h3>
                        <p style="color:#555;margin:0;font-size:15px;line-height:1.6;">
                            ⏰ 提醒时间：<strong>%s</strong><br/>
                            📅 日期：%s
                        </p>
                    </td></tr>
                    </table>
                </td></tr>
                <tr><td style="padding:0 40px 30px;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#fef9e7;border-radius:8px;border-left:4px solid #f0ad4e;">
                    <tr><td style="padding:16px 20px;">
                        <h4 style="color:#e67e22;margin:0 0 6px;font-size:15px;">💡 健康小贴士</h4>
                        <p style="color:#666;margin:0;font-size:14px;line-height:1.6;">%s</p>
                    </td></tr>
                    </table>
                </td></tr>
                <tr><td style="background-color:#f8f9fa;padding:20px 40px;text-align:center;border-top:1px solid #eee;">
                    <p style="color:#999;margin:0;font-size:12px;line-height:1.8;">
                        此邮件由健康管理系统自动发送，请勿直接回复。<br/>
                        如需修改提醒设置，请登录系统后在「提醒配置」中调整。
                    </p>
                </td></tr>
            </table>
            </td></tr>
            </table>
            </body></html>
            """.formatted(greeting, user.getUsername(), typeName, preference.getTime(), date, tip);
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "bloodPressure" -> "血压测量提醒";
            case "bloodSugar" -> "血糖检测提醒";
            case "weight" -> "体重记录提醒";
            case "exercise" -> "运动提醒";
            default -> "健康提醒";
        };
    }

    private String getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 6) return "夜深了";
        if (hour < 12) return "早上好";
        if (hour < 14) return "中午好";
        if (hour < 18) return "下午好";
        return "晚上好";
    }

    private String getHealthTip(String type) {
        return switch (type) {
            case "bloodPressure" -> "建议测量前静坐5分钟，避免饮用咖啡或吸烟。保持手臂与心脏同高，连续测量2-3次取平均值。";
            case "bloodSugar" -> "空腹血糖正常值 3.9-6.1 mmol/L，餐后2小时血糖 &lt; 7.8 mmol/L。建议记录每次检测结果以便追踪趋势。";
            case "weight" -> "建议每天固定时间（如早晨空腹）称量体重，穿着轻便衣物。体重的短期波动通常是水分变化，关注长期趋势更重要。";
            case "exercise" -> "世界卫生组织建议成人每周至少150分钟中等强度有氧运动，或75分钟高强度运动。运动前热身5-10分钟，运动后注意拉伸。";
            default -> "保持健康的生活方式，定期关注身体状况。如有不适请及时就医。";
        };
    }
}
