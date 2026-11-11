package com.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.exception.BusinessException;
import com.health.domain.dto.*;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatAuthServiceTest {
    WechatAuthService service;
    UserMapper users;
    AuthSessionService sessions;
    ValueOperations<String, String> values;
    MockRestServiceServer http;
    ObjectMapper json = new ObjectMapper();
    String key = Base64.getEncoder().encodeToString(new byte[16]);
    @BeforeEach void setup() {
        users = mock(UserMapper.class); sessions = mock(AuthSessionService.class);
        var redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values); when(values.get(anyString())).thenReturn(key);
        service = new WechatAuthService(users, sessions, json, redis);
        ReflectionTestUtils.setField(service, "appId", "wx-test"); ReflectionTestUtils.setField(service, "appSecret", "test-secret");
        http = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    }
    void expectLogin() {
        http.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session?appid=wx-test&secret=test-secret&js_code=test-code&grant_type=authorization_code"))
                .andRespond(withSuccess("{\"openid\":\"test-openid\",\"session_key\":\"" + key + "\"}", MediaType.TEXT_PLAIN));
    }
    WechatLoginDTO loginDto() { var dto = new WechatLoginDTO(); dto.setCode("test-code"); return dto; }
    User user() { var user = new User(); user.setId(5L); user.setUsername("existing"); user.setWechatOpenid("test-openid"); user.setStatus(1); return user; }
    @Test void firstLoginRegistersOnceAndExistingMiniappUserIsReused() {
        expectLogin(); when(users.selectOne(any())).thenReturn(null);
        doAnswer(i -> { i.getArgument(0, User.class).setId(5L); return 1; }).when(users).insert(any(User.class));
        when(sessions.issue(any(), eq(AuthSessionService.NATIVE))).thenReturn(new AuthTokens("a.b.c", "refresh", 900, 9999999999L));
        var response = service.wechatLogin(loginDto());
        assertEquals(5L, response.getUserInfo().getId()); assertTrue(response.getUserInfo().getWechatBound());
        verify(users).insert(argThat((User value) -> value.getUsername().length() > 8 && "test-openid".equals(value.getWechatOpenid())));
        http.verify();
    }
    @Test void concurrentFirstRegistrationReusesUniqueOpenidInsteadOfDuplicatingAccount() {
        expectLogin(); User existing = user(); existing.setNickname("微信昵称");
        when(users.selectOne(any())).thenReturn(null, existing);
        when(users.insert(any(User.class))).thenThrow(new DuplicateKeyException("openid"));
        when(sessions.issue(existing, AuthSessionService.NATIVE)).thenReturn(new AuthTokens("a.b.c", "refresh", 900, 9999999999L));
        assertEquals("微信昵称", service.wechatLogin(loginDto()).getUserInfo().getNickname());
    }
    @Test void refreshingWechatKeyCannotAttachAnotherWechatIdentityToTheCurrentAccount() {
        expectLogin(); User user = user(); user.setWechatOpenid("another-openid"); when(users.selectById(5L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> service.refreshSession(5L, "test-code"));
        verify(users, never()).updateById(any(User.class));
    }
    Map<String,Object> decrypt(String appId) throws Exception {
        var payload = Map.of("watermark", Map.of("appid", appId, "timestamp", java.time.Instant.now().getEpochSecond()), "stepInfoList", List.of());
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"), new IvParameterSpec(new byte[16]));
        return service.decryptRunData("test-openid", Base64.getEncoder().encodeToString(cipher.doFinal(json.writeValueAsBytes(payload))), key);
    }
    @Test void encryptedRunDataMustBelongToOurMiniapp() throws Exception {
        assertNotNull(decrypt("wx-test").get("stepInfoList"));
        assertThrows(BusinessException.class, () -> decrypt("other-app"));
    }
}
