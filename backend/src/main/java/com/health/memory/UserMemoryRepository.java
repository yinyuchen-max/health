package com.health.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UserMemoryRepository {
    private final JdbcTemplate jdbc;
    public UserMemoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Settings(boolean enabled, long revision, long nextTurn) {}
    public record Memory(Long id, String factKey, String category, String content, String sourceText,
                         String status, String pendingContent, String pendingSourceText, long lastTurn,
                         LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {}

    private static final RowMapper<Memory> ROW = (rs, n) -> new Memory(
            rs.getLong("id"), rs.getString("fact_key"), rs.getString("category"), rs.getString("content"),
            rs.getString("source_text"), rs.getString("status"), rs.getString("pending_content"),
            rs.getString("pending_source_text"), rs.getLong("last_turn"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime());

    // All writes and reply-history writes hold this row lock, including deletion/disable.
    public Settings lock(Long userId) {
        jdbc.update("INSERT INTO user_memory_settings(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", userId);
        return jdbc.queryForObject("SELECT enabled,revision,next_turn FROM user_memory_settings WHERE user_id=? FOR UPDATE",
                (rs, n) -> new Settings(rs.getBoolean(1), rs.getLong(2), rs.getLong(3)), userId);
    }
    public void advanceTurn(Long userId) {
        jdbc.update("UPDATE user_memory_settings SET next_turn=next_turn+1 WHERE user_id=?", userId);
    }
    public void invalidate(Long userId) {
        jdbc.update("UPDATE user_memory_settings SET revision=revision+1 WHERE user_id=?", userId);
    }
    public void enabled(Long userId, boolean enabled) {
        jdbc.update("UPDATE user_memory_settings SET enabled=?,revision=revision+1 WHERE user_id=?", enabled, userId);
    }
    public List<Memory> list(Long userId, LocalDateTime now) {
        return jdbc.query("SELECT * FROM user_memory WHERE user_id=? AND status IN ('ACTIVE','CONFLICT') AND (expires_at IS NULL OR expires_at>?) ORDER BY updated_at DESC,id DESC",
                ROW, userId, Timestamp.valueOf(now));
    }
    public Memory find(Long userId, Long id) {
        return jdbc.query("SELECT * FROM user_memory WHERE user_id=? AND id=?", ROW, userId, id)
                .stream().findFirst().orElse(null);
    }
    public void purgeExpired(Long userId, LocalDateTime now) {
        jdbc.update("DELETE FROM user_memory WHERE user_id=? AND expires_at<=?", userId, Timestamp.valueOf(now));
    }
    public void insert(Long userId, String key, String category, String content, String evidence, long turn, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO user_memory(user_id,fact_key,category,content,source_text,last_turn,expires_at) VALUES(?,?,?,?,?,?,?)",
                userId, key, category, content, evidence, turn, expiresAt);
    }
    public void replace(Long userId, Long id, String content, String evidence, long turn, LocalDateTime expiresAt) {
        jdbc.update("UPDATE user_memory SET content=?,source_text=?,status='ACTIVE',pending_content=NULL,pending_source_text=NULL,"
                        + "last_turn=?,expires_at=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",
                content, evidence, turn, expiresAt, userId, id);
    }
    public void conflict(Long userId, Long id, String content, String evidence, long turn) {
        jdbc.update("UPDATE user_memory SET status='CONFLICT',pending_content=?,pending_source_text=?,last_turn=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",
                content, evidence, turn, userId, id);
    }
    public void touch(Long userId, Long id, long turn) {
        jdbc.update("UPDATE user_memory SET last_turn=? WHERE user_id=? AND id=?", turn, userId, id);
    }
    public void delete(Long userId, Long id) { jdbc.update("DELETE FROM user_memory WHERE user_id=? AND id=?", userId, id); }
    public void clear(Long userId) { jdbc.update("DELETE FROM user_memory WHERE user_id=?", userId); }
}
