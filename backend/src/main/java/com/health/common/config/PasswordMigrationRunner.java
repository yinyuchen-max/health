package com.health.common.config;

import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据迁移：启动时自动将明文密码迁移为 BCrypt 加密
 * 同时确保 admin 用户拥有正确的 role 字段
 */
@Component
public class PasswordMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationRunner.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationRunner(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        migratePasswords();
        migrateRoles();
    }

    /**
     * 检测并迁移明文密码为 BCrypt 加密
     */
    private void migratePasswords() {
        List<User> users = userMapper.selectList(new QueryWrapper<>());
        int migratedCount = 0;
        for (User user : users) {
            String password = user.getPassword();
            // BCrypt 哈希以 $2a$ 或 $2b$ 开头，长度约 60
            if (password != null && !password.startsWith("$2a$") && !password.startsWith("$2b$")) {
                user.setPassword(passwordEncoder.encode(password));
                userMapper.updateById(user);
                migratedCount++;
                log.info("已迁移用户 [{}] 的密码为 BCrypt 加密格式", user.getUsername());
            }
        }
        if (migratedCount > 0) {
            log.info("密码迁移完成，共迁移 {} 个用户", migratedCount);
        }
    }

    /**
     * 确保 role 字段存在且正确
     */
    private void migrateRoles() {
        List<User> users = userMapper.selectList(new QueryWrapper<>());
        for (User user : users) {
            if (user.getRole() == null || user.getRole().isEmpty()) {
                // 用户名为 admin 的默认设为管理员
                String role = "admin".equals(user.getUsername()) ? "admin" : "user";
                user.setRole(role);
                userMapper.updateById(user);
                log.info("已为用户 [{}] 设置角色: {}", user.getUsername(), role);
            }
        }
    }
}
