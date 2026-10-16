package com.health.ai;

import com.health.common.exception.ForbiddenException;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAccessServiceTest {
    final UserMapper users=mock(UserMapper.class);
    final AiAccessMapper mapper=mock(AiAccessMapper.class);
    final AiAccessService service=new AiAccessService(users,mapper);
    User user(String role) {
        User user=new User(); user.setId(1L); user.setRole(role); user.setStatus(1); user.setDeleted(0);
        when(users.selectById(1L)).thenReturn(user); return user;
    }
    @Test void unpaidExpiredAndRevokedUsersAreDenied() {
        user("user");
        assertThrows(AiPaymentRequiredException.class,()->service.requireAccess(1L));
        when(mapper.find(1L)).thenReturn(new AiAccessMapper.Entitlement(LocalDateTime.now().minusDays(1),false));
        assertThrows(AiPaymentRequiredException.class,()->service.requireAccess(1L));
        when(mapper.find(1L)).thenReturn(new AiAccessMapper.Entitlement(LocalDateTime.now().plusDays(30),false));
        assertThrows(AiPaymentRequiredException.class,()->service.requireAccess(1L));
        when(mapper.find(1L)).thenReturn(new AiAccessMapper.Entitlement(LocalDateTime.now().plusDays(30),true));
        assertDoesNotThrow(()->service.requireAccess(1L));
    }
    @Test void adminExemptionIsRecheckedAfterDemotionAndDoesNotApplyToDoctors() {
        User user=user("admin");
        assertDoesNotThrow(()->service.requireAccess(1L)); verifyNoInteractions(mapper);
        user.setRole("user"); assertThrows(AiPaymentRequiredException.class,()->service.requireAccess(1L));
        user.setRole("doctor"); assertThrows(AiPaymentRequiredException.class,()->service.requireAccess(1L));
    }
    @Test void disabledAdminAndDeletedUserCannotCallAi() {
        User user=user("admin"); user.setStatus(0);
        assertThrows(ForbiddenException.class,()->service.requireAccess(1L));
        user.setStatus(1); user.setDeleted(1);
        assertThrows(ForbiddenException.class,()->service.requireAccess(1L));
        assertThrows(ForbiddenException.class,()->service.requireAccess(2L));
        verifyNoInteractions(mapper);
    }
}
