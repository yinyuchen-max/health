package com.health.service;

import com.health.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class WechatStepService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public WechatStepService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record DaySteps(String date, int steps, String syncedAt) {}
    public record Summary(String date, Integer todaySteps, String lastSyncAt, List<DaySteps> history) {}

    @Transactional
    public void sync(Long userId, Map<String, Object> decrypted) {
        if (!(decrypted.get("stepInfoList") instanceof List<?> values) || values.size() > 32)
            throw new BusinessException("微信运动数据格式无效");
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime syncedAt = LocalDateTime.now(ZONE);
        List<Object[]> rows = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> day) || !(day.get("timestamp") instanceof Number timestamp)
                    || !(day.get("step") instanceof Number steps) || steps.doubleValue() != steps.intValue()
                    || steps.intValue() < 0 || steps.intValue() > 1_000_000)
                throw new BusinessException("微信运动数据格式无效");
            LocalDate date;
            try { date = Instant.ofEpochSecond(timestamp.longValue()).atZone(ZONE).toLocalDate(); }
            catch (DateTimeException e) { throw new BusinessException("微信运动日期无效"); }
            if (date.isAfter(today) || date.isBefore(today.minusDays(31))) throw new BusinessException("微信运动日期超出范围");
            rows.add(new Object[]{userId, date.toString(), steps.intValue(), syncedAt});
        }
        // 同一天反复同步、多个设备并发同步时只更新一行，旧快照不能降低已有步数。
        jdbc.batchUpdate("INSERT INTO wechat_step_record(user_id,record_date,steps,synced_at) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE steps=GREATEST(steps,VALUES(steps)),synced_at=GREATEST(synced_at,VALUES(synced_at))", rows);
    }

    public Summary summary(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        List<DaySteps> days = jdbc.query("SELECT record_date,steps,synced_at FROM wechat_step_record "
                        + "WHERE user_id=? AND record_date>=? AND record_date<=? ORDER BY record_date DESC",
                (rs, row) -> new DaySteps(rs.getDate(1).toString(), rs.getInt(2), rs.getTimestamp(3).toLocalDateTime().toString()),
                userId, today.minusDays(30).toString(), today.toString());
        Integer steps = days.stream().filter(day -> day.date().equals(today.toString())).map(DaySteps::steps).findFirst().orElse(null);
        String lastSync = days.stream().map(DaySteps::syncedAt).max(String::compareTo).orElse(null);
        return new Summary(today.toString(), steps, lastSync, days);
    }
}
