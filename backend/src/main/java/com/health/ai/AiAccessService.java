package com.health.ai;

import com.health.common.enums.UserRole;
import com.health.common.exception.ForbiddenException;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AiAccessService {
    private final UserMapper users;
    private final AiAccessMapper entitlements;
    public AiAccessService(UserMapper users, AiAccessMapper entitlements) {
        this.users=users; this.entitlements=entitlements;
    }
    public record Access(boolean allowed, boolean admin, LocalDateTime expiresAt) {}
    public Access status(Long userId) {
        User user=userId==null ? null : users.selectById(userId);
        if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || Integer.valueOf(1).equals(user.getDeleted()))
            throw new ForbiddenException("账号不存在或已禁用");
        // Always read the current role; stale tokens and background jobs cannot retain admin exemption.
        if(UserRole.ADMIN.getCode().equals(user.getRole())) return new Access(true,true,null);
        var entitlement=entitlements.find(userId);
        return entitlement==null ? new Access(false,false,null)
                : new Access(entitlement.active(),false,entitlement.expiresAt());
    }
    public void requireAccess(Long userId) {
        if(!status(userId).allowed()) throw new AiPaymentRequiredException();
    }
}
