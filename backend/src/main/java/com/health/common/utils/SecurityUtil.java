package com.health.common.utils;

import com.health.common.exception.ForbiddenException;
import com.health.common.exception.UnauthorizedException;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 安全工具类
 * 提供获取当前登录用户信息、权限校验等功能
 */
@Component
public class SecurityUtil {

    private final UserMapper userMapper;

    public SecurityUtil(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 获取当前登录用户名
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("请先登录");
        }
        return auth.getName();
    }

    /**
     * 获取当前登录用户实体
     */
    public User getCurrentUser() {
        String username = getCurrentUsername();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new UnauthorizedException("用户不存在或已被删除");
        }
        if (user.getStatus() == 0) {
            throw new ForbiddenException("账号已被禁用");
        }
        return user;
    }

    /**
     * 获取当前登录用户ID
     */
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * 校验当前用户是否为管理员
     */
    public void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new UnauthorizedException("请先登录");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new ForbiddenException("权限不足，仅管理员可操作");
        }
    }

    /**
     * 校验当前用户是否有权操作指定userId的数据
     * 管理员可以操作所有数据，普通用户只能操作自己的数据
     */
    public void requireOwnerOrAdmin(Long targetUserId) {
        if (targetUserId == null) {
            throw new ForbiddenException("目标用户ID不能为空");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new UnauthorizedException("请先登录");
        }
        // 管理员可以操作所有数据
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return;
        }
        // 普通用户只能操作自己的数据
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(targetUserId)) {
            throw new ForbiddenException("无权操作他人的数据");
        }
    }

    /**
     * 校验当前用户是否为某条记录的所有者（通过记录所属userId）
     */
    public void requireRecordOwner(Long recordUserId) {
        requireOwnerOrAdmin(recordUserId);
    }
}
