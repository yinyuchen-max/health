package com.health.service.impl;

import com.health.common.exception.AuthStoreUnavailableException;
import com.health.domain.dto.ChangePasswordDTO;
import com.health.domain.entity.User;
import com.health.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class UserCredentialRevocationTest {
    UserServiceImpl service;
    AuthSessionService sessions;
    User user;
    @BeforeEach void setup() {
        service = spy(new UserServiceImpl()); sessions = mock(AuthSessionService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(service,"authSessions",sessions);
        ReflectionTestUtils.setField(service,"passwordEncoder",encoder);
        user = new User(); user.setId(1L); user.setUsername("alice"); user.setPassword("old-hash"); user.setStatus(1);
        doReturn(user).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(User.class));
        when(encoder.matches("old-password","old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
    }
    private ChangePasswordDTO change() {
        var dto = new ChangePasswordDTO(); dto.setOldPassword("old-password"); dto.setNewPassword("new-password"); return dto;
    }
    @Test void passwordChangeRevokesAllDevicesBeforeUpdatingCredentials() {
        service.changePassword(1L,change());
        var order = inOrder(sessions,service);
        order.verify(sessions).revokeAll("alice"); order.verify(service).updateById(user);
        assertEquals("new-hash",user.getPassword());
    }
    @Test void statusChangeRevokesAllDevices() {
        service.updateUserStatus(1L,0);
        verify(sessions).revokeAll("alice"); assertEquals(0,user.getStatus());
    }
    @Test void unavailableStoreDoesNotReportSuccessfulPasswordChange() {
        doThrow(new AuthStoreUnavailableException()).when(sessions).revokeAll("alice");
        assertThrows(AuthStoreUnavailableException.class,() -> service.changePassword(1L,change()));
        verify(service,never()).updateById(any(User.class)); assertEquals("old-hash",user.getPassword());
    }
}
