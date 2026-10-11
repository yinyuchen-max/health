package com.health.service;

import com.health.common.exception.BusinessException;
import com.health.common.exception.ForbiddenException;
import com.health.domain.dto.FamilyDtos.*;
import com.health.domain.dto.SmartHealthOverviewDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class FamilyService {
    private final JdbcTemplate jdbc;
    private final SmartHealthService smartHealth;
    private record Link(long id, long inviter, long invitee, String status) {
        long other(long userId) {
            if (userId != inviter && userId != invitee) throw new ForbiddenException("无权访问该家庭关系");
            return userId == inviter ? invitee : inviter;
        }
    }
    private static final RowMapper<Link> LINK = (rs, row) -> new Link(rs.getLong("id"),
            rs.getLong("inviter_id"), rs.getLong("invitee_id"), rs.getString("status"));

    public FamilyService(JdbcTemplate jdbc, SmartHealthService smartHealth) {
        this.jdbc = jdbc;
        this.smartHealth = smartHealth;
    }

    public List<Binding> list(long userId) {
        return jdbc.query("""
                SELECT b.*, u.id member_id, u.username member_name, u.phone
                FROM family_binding b JOIN sys_user u
                  ON u.id = CASE WHEN b.inviter_id = ? THEN b.invitee_id ELSE b.inviter_id END
                WHERE (b.inviter_id = ? OR b.invitee_id = ?)
                  AND b.status IN ('PENDING', 'ACTIVE') AND u.deleted = 0 AND u.status = 1
                ORDER BY b.created_at DESC, b.id DESC
                """, (rs, row) -> {
            String phone = rs.getString("phone");
            String masked = phone != null && phone.length() == 11
                    ? phone.substring(0, 3) + "****" + phone.substring(7) : "未填写手机号";
            return new Binding(rs.getLong("id"), rs.getLong("member_id"), rs.getString("member_name"),
                    masked, rs.getString("relationship"), rs.getString("status"),
                    rs.getLong("invitee_id") == userId, rs.getTimestamp("created_at").toLocalDateTime());
        }, userId, userId, userId);
    }

    @Transactional
    public void invite(long userId, Invite invite) {
        // 手机号不是认证凭据；同号多账号时不能猜测应该邀请谁。
        var targets = jdbc.queryForList("SELECT id FROM sys_user WHERE phone = ? AND deleted = 0 AND status = 1",
                Long.class, invite.phone());
        if (targets.size() != 1) throw new BusinessException("该手机号无法唯一匹配账号，请家人检查个人资料中的手机号");
        long target = targets.get(0);
        if (target == userId) throw new BusinessException("不能绑定自己");
        long low = Math.min(userId, target), high = Math.max(userId, target);
        // 固定顺序锁定双方账号，序列化双向同时邀请，避免不存在关系时的间隙锁竞争。
        for (long account : new long[] { low, high }) {
            if (jdbc.queryForList("SELECT id FROM sys_user WHERE id = ? AND deleted = 0 AND status = 1 FOR UPDATE",
                    Long.class, account).isEmpty()) throw new BusinessException("账号不可用，请刷新后重试");
        }
        var links = jdbc.query("SELECT * FROM family_binding WHERE user_low = ? AND user_high = ? FOR UPDATE",
                LINK, low, high);
        if (!links.isEmpty()) {
            var link = links.get(0);
            if (Set.of("PENDING", "ACTIVE").contains(link.status()))
                throw new BusinessException("已绑定或已有待处理邀请，请勿重复邀请");
            jdbc.update("""
                    UPDATE family_binding SET inviter_id = ?, invitee_id = ?, relationship = ?,
                        status = 'PENDING', created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, userId, target, invite.relationship().trim(), link.id());
        } else {
            try {
                jdbc.update("INSERT INTO family_binding (user_low, user_high, inviter_id, invitee_id, relationship) VALUES (?, ?, ?, ?, ?)",
                        low, high, userId, target, invite.relationship().trim());
            } catch (DuplicateKeyException e) {
                throw new BusinessException("已有家庭邀请，请刷新后查看");
            }
        }
    }

    @Transactional
    public void decide(long userId, long id, boolean accept) {
        var link = link(userId, id, true);
        if (link.invitee() != userId) throw new ForbiddenException("只有被邀请人可以处理邀请");
        if (!"PENDING".equals(link.status())) throw new BusinessException("该邀请已处理，请刷新列表");
        requireAvailable(link.other(userId));
        jdbc.update("UPDATE family_binding SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                accept ? "ACTIVE" : "REJECTED", id);
    }

    @Transactional
    public void remove(long userId, long id) {
        link(userId, id, true);
        jdbc.update("UPDATE family_binding SET status = 'REMOVED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public SmartHealthOverviewDTO report(long userId, long id) {
        var link = active(userId, id, false);
        var report = smartHealth.generateOverview(link.other(userId));
        // AI 生成期间若对方解除授权，也不能返回报告。
        active(userId, id, false);
        return report;
    }

    @Transactional
    public void remind(long userId, long id, String type) {
        if (!Set.of("bloodPressure", "bloodSugar", "weight", "exercise").contains(type))
            throw new BusinessException("不支持的提醒类型");
        var link = active(userId, id, true);
        long recipient = link.other(userId);
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM family_reminder WHERE sender_id = ? AND recipient_id = ?
                  AND type = ? AND created_at > ?
                """, Long.class, userId, recipient, type, LocalDateTime.now().minusMinutes(30));
        if (count != null && count > 0) throw new BusinessException("已发送该提醒，请30分钟后再试");
        jdbc.update("INSERT INTO family_reminder (binding_id, sender_id, recipient_id, type) VALUES (?, ?, ?, ?)",
                id, userId, recipient, type);
    }

    public List<Reminder> reminders(long userId) {
        return jdbc.query("""
                SELECT r.*, u.username FROM family_reminder r JOIN sys_user u ON u.id = r.sender_id
                WHERE r.recipient_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT 100
                """, (rs, row) -> new Reminder(rs.getLong("id"), rs.getString("username"), rs.getString("type"),
                rs.getBoolean("read_status"), rs.getBoolean("completed"),
                rs.getTimestamp("created_at").toLocalDateTime()), userId);
    }

    public void acknowledge(long userId, long id, boolean complete) {
        int changed = jdbc.update(complete
                ? "UPDATE family_reminder SET read_status = TRUE, completed = TRUE WHERE id = ? AND recipient_id = ?"
                : "UPDATE family_reminder SET read_status = TRUE WHERE id = ? AND recipient_id = ?", id, userId);
        if (changed == 0) throw new ForbiddenException("提醒不存在或无权操作");
    }

    private Link active(long userId, long id, boolean lock) {
        var link = link(userId, id, lock);
        if (!"ACTIVE".equals(link.status())) throw new ForbiddenException("请先获得家人同意并完成绑定");
        requireAvailable(link.other(userId));
        return link;
    }

    private Link link(long userId, long id, boolean lock) {
        var links = jdbc.query("SELECT * FROM family_binding WHERE id = ?" + (lock ? " FOR UPDATE" : ""), LINK, id);
        if (links.isEmpty()) throw new ForbiddenException("家庭关系不存在或已解除");
        var link = links.get(0);
        link.other(userId);
        return link;
    }

    private void requireAvailable(long userId) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0 AND status = 1",
                Long.class, userId);
        if (count == null || count == 0) throw new ForbiddenException("家人账号不可用");
    }
}
