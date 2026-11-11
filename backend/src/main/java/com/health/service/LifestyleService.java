package com.health.service;

import com.health.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 网页和小程序共用的步数、睡眠、每日目标和提醒，供聊天与健康报告读取。 */
@Service
public class LifestyleService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> QUALITIES = Set.of("优秀", "良好", "一般", "较差");
    private final JdbcTemplate jdbc;

    public LifestyleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Goals(int steps, int exerciseMinutes, int waterMl, int sleepHours, int calories, boolean saved) {}
    public record SleepDay(String date, String bedTime, String wakeTime, int sleepMinutes, String quality,
                           Integer deepMinutes, Integer lightMinutes, Integer remMinutes) {}
    public record Capture(boolean hasSteps, boolean hasSleep, boolean savedGoals, boolean hasReminders, boolean tracked,
                          String prompt, String fingerprint, List<String> evidence, List<String> labels) {}

    public Goals goals(Long userId) {
        var rows = jdbc.query("SELECT steps,exercise_minutes,water_ml,sleep_hours,calories FROM user_health_goal WHERE user_id=?",
                (rs, row) -> new Goals(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), true), userId);
        return rows.isEmpty() ? new Goals(10000, 60, 2000, 8, 1800, false) : rows.get(0);
    }

    public void saveGoals(Long userId, int steps, int exerciseMinutes, int waterMl, int sleepHours, int calories) {
        if (steps < 1000 || steps > 100000) throw new BusinessException("每日步数目标需在 1000 到 100000 之间");
        if (exerciseMinutes < 10 || exerciseMinutes > 600) throw new BusinessException("每日运动目标需在 10 到 600 分钟之间");
        if (waterMl < 500 || waterMl > 8000) throw new BusinessException("每日饮水目标需在 500 到 8000 毫升之间");
        if (sleepHours < 4 || sleepHours > 12) throw new BusinessException("睡眠目标需在 4 到 12 小时之间");
        if (calories < 800 || calories > 6000) throw new BusinessException("每日热量目标需在 800 到 6000 千卡之间");
        jdbc.update("INSERT INTO user_health_goal(user_id,steps,exercise_minutes,water_ml,sleep_hours,calories,updated_at) VALUES(?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE steps=VALUES(steps),exercise_minutes=VALUES(exercise_minutes),water_ml=VALUES(water_ml),"
                        + "sleep_hours=VALUES(sleep_hours),calories=VALUES(calories),updated_at=VALUES(updated_at)",
                userId, steps, exerciseMinutes, waterMl, sleepHours, calories, LocalDateTime.now(ZONE));
    }

    public List<SleepDay> recentSleep(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), 30);
        return jdbc.query("SELECT record_date,bed_time,wake_time,sleep_minutes,quality,deep_minutes,light_minutes,rem_minutes "
                        + "FROM sleep_record WHERE user_id=? ORDER BY record_date DESC LIMIT ?",
                (rs, row) -> new SleepDay(rs.getDate(1).toString(), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        (Integer) rs.getObject(6), (Integer) rs.getObject(7), (Integer) rs.getObject(8)), userId, size);
    }

    public void saveSleep(Long userId, String date, String bedTime, String wakeTime, int sleepMinutes, String quality,
                          Integer deepMinutes, Integer lightMinutes, Integer remMinutes) {
        LocalDate day = parseDate(date);
        LocalDate today = LocalDate.now(ZONE);
        if (day.isAfter(today) || day.isBefore(today.minusDays(365))) throw new BusinessException("睡眠日期需在过去一年内");
        if (!clock(bedTime) || !clock(wakeTime)) throw new BusinessException("入睡和醒来时间格式应为 HH:mm");
        if (sleepMinutes < 30 || sleepMinutes > 16 * 60) throw new BusinessException("睡眠时长需在 30 分钟到 16 小时之间");
        if (!QUALITIES.contains(quality)) throw new BusinessException("睡眠质量只能是优秀、良好、一般或较差");
        checkStage(deepMinutes);
        checkStage(lightMinutes);
        checkStage(remMinutes);
        int stages = value(deepMinutes) + value(lightMinutes) + value(remMinutes);
        if (stages > sleepMinutes) throw new BusinessException("深睡、浅睡和快速眼动时长不能超过总睡眠时长");
        jdbc.update("INSERT INTO sleep_record(user_id,record_date,bed_time,wake_time,sleep_minutes,quality,deep_minutes,light_minutes,rem_minutes,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE bed_time=VALUES(bed_time),wake_time=VALUES(wake_time),"
                        + "sleep_minutes=VALUES(sleep_minutes),quality=VALUES(quality),deep_minutes=VALUES(deep_minutes),"
                        + "light_minutes=VALUES(light_minutes),rem_minutes=VALUES(rem_minutes),updated_at=VALUES(updated_at)",
                userId, day.toString(), bedTime, wakeTime, sleepMinutes, quality, deepMinutes, lightMinutes, remMinutes, LocalDateTime.now(ZONE));
    }

    public Capture capture(Long userId) {
        Goals goal = goals(userId);
        List<SleepDay> sleep = recentSleep(userId, 7);
        List<StepDay> steps = jdbc.query("SELECT record_date,steps FROM wechat_step_record WHERE user_id=? AND record_date>=? ORDER BY record_date DESC LIMIT 7",
                (rs, row) -> new StepDay(rs.getDate(1).toString(), rs.getInt(2)), userId, LocalDate.now(ZONE).minusDays(6).toString());
        List<ReminderLine> reminders = jdbc.query("SELECT type,time,frequency FROM reminder_preference WHERE user_id=? AND deleted=0 AND enabled=1 ORDER BY id DESC LIMIT 8",
                (rs, row) -> new ReminderLine(rs.getString(1), rs.getString(2), rs.getString(3)), userId);
        StringBuilder prompt = new StringBuilder();
        StringBuilder fingerprint = new StringBuilder();
        List<String> evidence = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (steps.isEmpty()) prompt.append("微信步数：尚未同步。\n");
        else {
            labels.add("微信步数");
            evidence.add("最近 " + steps.size() + " 天微信步数");
            prompt.append("微信步数：\n");
            for (StepDay day : steps) {
                prompt.append("- ").append(day.date).append(' ').append(day.steps).append(" 步\n");
                fingerprint.append("step:").append(day.date).append(':').append(day.steps).append('\n');
            }
        }
        if (sleep.isEmpty()) prompt.append("睡眠记录：没有。不要输出实测睡眠评分。\n");
        else {
            labels.add("睡眠记录");
            evidence.add("最近 " + sleep.size() + " 条睡眠记录");
            prompt.append("睡眠记录：\n");
            for (SleepDay day : sleep) {
                prompt.append("- ").append(day.date).append(" 入睡 ").append(day.bedTime).append(" 醒来 ").append(day.wakeTime)
                        .append(" 共 ").append(day.sleepMinutes).append(" 分钟 质量").append(day.quality).append('\n');
                fingerprint.append("sleep:").append(day.date).append(':').append(day.sleepMinutes).append(':').append(day.quality).append('\n');
            }
        }
        if (!goal.saved) {
            prompt.append("每日目标：用户还没有保存。\n");
            fingerprint.append("goals:none\n");
        } else {
            labels.add("每日目标");
            evidence.add("已保存每日目标");
            prompt.append("每日目标：步数 ").append(goal.steps).append("，运动 ").append(goal.exerciseMinutes)
                    .append(" 分钟，饮水 ").append(goal.waterMl).append(" 毫升，睡眠 ").append(goal.sleepHours)
                    .append(" 小时，热量 ").append(goal.calories).append(" 千卡。\n");
            fingerprint.append("goals:").append(goal.steps).append(':').append(goal.exerciseMinutes).append(':')
                    .append(goal.waterMl).append(':').append(goal.sleepHours).append(':').append(goal.calories).append('\n');
        }
        if (reminders.isEmpty()) prompt.append("提醒：没有启用中的提醒。\n");
        else {
            labels.add("提醒");
            evidence.add(reminders.size() + " 条启用中的提醒");
            prompt.append("启用中的提醒：\n");
            for (ReminderLine reminder : reminders) {
                String name = reminderName(reminder.type);
                prompt.append("- ").append(name).append(' ').append(reminder.time).append(' ').append(frequencyName(reminder.frequency)).append('\n');
                fingerprint.append("reminder:").append(reminder.type).append(':').append(reminder.time).append(':').append(reminder.frequency).append('\n');
            }
        }
        boolean tracked = !steps.isEmpty() || !sleep.isEmpty();
        return new Capture(!steps.isEmpty(), !sleep.isEmpty(), goal.saved, !reminders.isEmpty(), tracked,
                prompt.toString(), fingerprint.toString(), List.copyOf(evidence), List.copyOf(labels));
    }

    private record StepDay(String date, int steps) {}
    private record ReminderLine(String type, String time, String frequency) {}

    private static void checkStage(Integer minutes) {
        if (minutes != null && (minutes < 0 || minutes > 16 * 60)) throw new BusinessException("分段睡眠时长无效");
    }

    private static int value(Integer minutes) { return minutes == null ? 0 : minutes; }

    private static boolean clock(String value) { return value != null && value.matches("([01]\\d|2[0-3]):[0-5]\\d"); }

    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException | NullPointerException exception) { throw new BusinessException("睡眠日期无效"); }
    }

    private static String reminderName(String type) {
        return switch (type == null ? "" : type) {
            case "bloodPressure" -> "血压测量";
            case "bloodSugar" -> "血糖检测";
            case "weight" -> "体重记录";
            case "exercise" -> "运动";
            case "sleep" -> "睡眠";
            case "water" -> "饮水";
            default -> "健康提醒";
        };
    }

    private static String frequencyName(String frequency) {
        return switch (frequency == null ? "" : frequency) {
            case "daily" -> "每天";
            case "weekly" -> "每周";
            case "custom" -> "隔天";
            default -> frequency;
        };
    }
}
