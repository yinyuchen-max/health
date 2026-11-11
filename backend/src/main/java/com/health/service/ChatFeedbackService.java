package com.health.service;

import com.health.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
public class ChatFeedbackService {
    private static final Set<String> RATINGS = Set.of("USEFUL", "INACCURATE");
    private final JdbcTemplate jdbc;

    public ChatFeedbackService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(Long userId, String rating, String excerpt) {
        if (!RATINGS.contains(rating)) throw new BusinessException("评价无效");
        String text = excerpt == null ? "" : excerpt.trim();
        if (text.isEmpty()) throw new BusinessException("请选择一条回复后再评价");
        if (text.length() > 500) text = text.substring(0, 500);
        jdbc.update("INSERT INTO chat_reply_feedback(user_id,rating,excerpt,created_at) VALUES(?,?,?,?)",
                userId, rating, text, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }
}
