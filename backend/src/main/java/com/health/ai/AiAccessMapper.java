package com.health.ai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

@Mapper
public interface AiAccessMapper {
    // Use the database clock so expiry does not depend on individual application clocks.
    @Select("SELECT expires_at, (status='ACTIVE' AND expires_at > UTC_TIMESTAMP()) AS active "
            + "FROM ai_entitlement WHERE user_id=#{userId}")
    Entitlement find(Long userId);
    record Entitlement(LocalDateTime expiresAt, boolean active) {}
}
