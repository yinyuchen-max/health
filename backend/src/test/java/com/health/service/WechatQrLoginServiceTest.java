package com.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.exception.BusinessException;
import com.health.common.security.TokenDigests;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatQrLoginServiceTest {
    WechatQrLoginService service;
    StringRedisTemplate redis;
    UserMapper users;
    MockRestServiceServer http;
    AtomicReference<Object[]> arguments;
    @BeforeEach void setup() {
        redis = mock(StringRedisTemplate.class); users = mock(UserMapper.class);
        var access = mock(WechatAccessTokenService.class); when(access.getAccessToken()).thenReturn("test-access");
        service = new WechatQrLoginService(redis, access, users, new ObjectMapper());
        http = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "http")).build();
        arguments = new AtomicReference<>();
        doAnswer(i -> { arguments.set((Object[]) i.getRawArguments()[2]); return "PENDING"; })
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void qrSceneContainsOnlyTicketAndBrowserProofIsStoredAsDigest() {
        http.expect(requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=test-access"))
                .andExpect(jsonPath("$.page").value("pages/web-login/web-login"))
                .andExpect(jsonPath("$.env_version").value("release"))
                .andExpect(jsonPath("$.check_path").value(true))
                .andExpect(jsonPath("$.scene").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.proof").doesNotExist())
                .andRespond(withSuccess(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}, MediaType.IMAGE_JPEG));
        var result = service.create();
        assertEquals(TokenDigests.sha256(result.proof()), arguments.get()[1]);
        assertEquals("180", arguments.get()[2]);
        assertTrue(result.qrCode().startsWith("data:image/jpeg;base64,"));
        assertTrue(result.expiresAt() > System.currentTimeMillis());
        assertFalse(result.toString().contains(result.proof()));
        http.verify();
    }
    @Test void wechatJsonErrorNeverBecomesAQrImageOrCreatesASession() {
        http.expect(anything()).andRespond(withSuccess("{\"errcode\":41030}", MediaType.APPLICATION_JSON));
        var error = assertThrows(BusinessException.class, () -> service.create());
        assertTrue(error.getMessage().contains("41030"));
        assertTrue(error.getMessage().contains("尚未发布"));
        verifyNoInteractions(redis);
    }
    @Test void localDevelopmentCodeDoesNotRequireTheConfirmationPageInThePublishedRelease() {
        ReflectionTestUtils.setField(service, "envVersion", "develop");
        http.expect(anything())
                .andExpect(jsonPath("$.env_version").value("develop"))
                .andExpect(jsonPath("$.check_path").value(false))
                .andExpect(jsonPath("$.page").value("pages/web-login/web-login"))
                .andRespond(withSuccess(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, MediaType.IMAGE_PNG));
        assertTrue(service.create().qrCode().startsWith("data:image/png;base64,"));
        http.verify();
    }
    @Test void developmentPageErrorsGiveUploadGuidanceAndOtherErrorsKeepTheirWechatCode() {
        ReflectionTestUtils.setField(service, "envVersion", "develop");
        http.expect(anything()).andRespond(withSuccess("{\"errcode\":41030}", MediaType.APPLICATION_JSON));
        http.expect(anything()).andRespond(withSuccess("{\"errcode\":48001}", MediaType.APPLICATION_JSON));
        var missingPage = assertThrows(BusinessException.class, () -> service.create());
        assertTrue(missingPage.getMessage().contains("已上传"));
        var noPermission = assertThrows(BusinessException.class, () -> service.create());
        assertTrue(noPermission.getMessage().contains("48001"));
        assertFalse(noPermission.getMessage().contains("尚未发布"));
        verifyNoInteractions(redis);
    }
    @Test void invalidAndExpiredProofCannotIssueUserAndDisabledUsersCannotConfirm() {
        String ticket = "a".repeat(32), proof = TokenDigests.randomToken();
        doReturn("INVALID").when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThrows(BusinessException.class, () -> service.consume(ticket, proof));
        verifyNoInteractions(users);
        User user = new User(); user.setId(1L); user.setStatus(0); user.setWechatOpenid("openid"); when(users.selectById(1L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> service.confirm(ticket, 1L));
        assertThrows(BusinessException.class, () -> service.status(ticket, "wrong-proof"));
    }
}
