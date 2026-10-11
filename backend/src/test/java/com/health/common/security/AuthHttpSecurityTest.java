package com.health.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.SecurityConfig;
import com.health.common.config.GlobalExceptionHandler;
import com.health.common.filter.JwtAuthenticationFilter;
import com.health.common.utils.JwtUtil;
import com.health.common.utils.SecurityUtil;
import com.health.common.exception.AuthStoreUnavailableException;
import com.health.controller.UserController;
import com.health.domain.dto.AuthTokens;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import com.health.service.*;
import com.health.service.impl.TokenBlacklistServiceImpl;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthHttpSecurityTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer pathMatching() {
            // Match the project's spring.mvc.pathmatch.matching-strategy=ant_path_matcher.
            return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
                @Override public void configurePathMatch(org.springframework.web.servlet.config.annotation.PathMatchConfigurer configurer) { configurer.setPatternParser(null); }
            };
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean StringRedisTemplate redis() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                    .thenReturn(0L);
            return redis;
        }
        @Bean GlobalExceptionHandler exceptions() { return new GlobalExceptionHandler(); }
        @Bean JwtUtil jwtUtil() { return new JwtUtil(); }
        @Bean AuthCookies authCookies() { return new AuthCookies(); }
        @Bean AuthSessionService sessions() { return mock(AuthSessionService.class); }
        @Bean TokenBlacklistServiceImpl blacklist() { return mock(TokenBlacklistServiceImpl.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtUtil jwt, TokenBlacklistServiceImpl blacklist, ObjectMapper mapper, AuthSessionService sessions) {
            return new JwtAuthenticationFilter(jwt, blacklist, mapper, sessions);
        }
        @Bean UserController controller() { return new UserController(); }
        @Bean UserService users() { return mock(UserService.class); }
        @Bean SecurityUtil securityUtil() { return mock(SecurityUtil.class); }
        @Bean WechatAuthService wechat() { return mock(WechatAuthService.class); }
        @Bean UserMapper mapper() { return mock(UserMapper.class); }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    AuthSessionService sessions;
    ObjectMapper json;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "jwt.secret","test-only-secret-at-least-thirty-two-bytes", "jwt.expiration", "900000", "jwt.cookie-secure", "true")));
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        sessions = context.getBean(AuthSessionService.class); json = context.getBean(ObjectMapper.class);
        var tokens = new AuthTokens("test.access.jwt", "test-refresh-credential", 900, System.currentTimeMillis()+604800000);
        when(sessions.issue(any(), anyString())).thenReturn(tokens);
        when(sessions.refresh(any(), anyString())).thenReturn(tokens);
        when(context.getBean(UserService.class).login(any())).thenReturn(new User());
    }
    @AfterEach void close() { context.close(); }
    @Test void overloadStopsBeforeAuthenticationAndKeepsCorsHeaders() throws Exception {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                .thenReturn(1000L);
        JwtUtil jwt = context.getBean(JwtUtil.class);
        String token = jwt.generateToken("alice", "user", TokenDigests.randomToken(), 900000);
        mvc.perform(get("/api/user/info").header("Authorization", "Bearer " + token)
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429))
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Authorization, Retry-After"));
        verify(sessions, never()).validateAccess(any());
        verifyNoInteractions(context.getBean(UserMapper.class));
    }

    @Test void unavailableGlobalLimiterStopsPublicLoginBeforeDatabaseAccess() throws Exception {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test unavailable"));
        clearInvocations(context.getBean(UserService.class));
        mvc.perform(post("/api/user/native-login").contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"test\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503))
                .andExpect(header().string("Retry-After", "1"));
        verify(context.getBean(UserService.class), never()).login(any());
    }
    @Test void browserLoginAndRefreshRequireCsrfAndDoNotReturnRefreshInJson() throws Exception {
        mvc.perform(post("/api/user/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/user/login").contentType("application/json").content("{\"username\":\"alice\",\"password\":\"test\"}"))
                .andExpect(status().isForbidden());
        var csrf = mvc.perform(get("/api/user/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var cookie = csrf.getCookie(AuthCookies.CSRF);
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly()); assertTrue(cookie.getSecure());
        assertTrue(csrf.getHeader("Set-Cookie").contains("SameSite=Strict"));
        String masked = json.readTree(csrf.getContentAsString()).at("/data/token").asText();
        var response = mvc.perform(post("/api/user/login").cookie(cookie).header("X-CSRF-TOKEN", masked)
                .contentType("application/json").content("{\"username\":\"alice\",\"password\":\"test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertFalse(json.readTree(response.getContentAsString()).get("data").has("refreshToken"));
        var refresh = response.getCookie(AuthCookies.REFRESH);
        assertTrue(refresh.isHttpOnly()); assertTrue(refresh.getSecure()); assertEquals("/api/user", refresh.getPath());
        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Strict"));
        mvc.perform(post("/api/user/refresh").cookie(cookie, refresh).header("X-CSRF-TOKEN", masked))
                .andExpect(status().isOk());
        mvc.perform(post("/api/user/web-logout").cookie(cookie, refresh).header("X-CSRF-TOKEN", masked))
                .andExpect(status().isOk()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
    }
    @Test void nativeRefreshUsesBodyCredentialAndCannotExchangeBrowserCookie() throws Exception {
        mvc.perform(post("/api/user/native-refresh").contentType("application/json").content("{\"refreshToken\":\"test-native\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").value("test-refresh-credential"));
        verify(sessions).refresh("test-native", AuthSessionService.NATIVE);
        mvc.perform(post("/api/user/native-refresh").cookie(new Cookie(AuthCookies.REFRESH,"web-token"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
    @Test void staleCsrfCannotRevokeAndFreshTokenAllowsLogout() throws Exception {
        var stale = mvc.perform(get("/api/user/csrf")).andReturn().getResponse();
        String staleToken = json.readTree(stale.getContentAsString()).at("/data/token").asText();
        var fresh = mvc.perform(get("/api/user/csrf")).andReturn().getResponse();
        var currentCookie = fresh.getCookie(AuthCookies.CSRF);
        var refresh = new Cookie(AuthCookies.REFRESH, "test-refresh-credential");
        mvc.perform(post("/api/user/web-logout").cookie(currentCookie, refresh).header("X-CSRF-TOKEN", staleToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data.reason").value("CSRF"));
        verify(sessions, never()).revokeRefresh(any(), any());
        String currentToken = json.readTree(fresh.getContentAsString()).at("/data/token").asText();
        mvc.perform(post("/api/user/web-logout").cookie(currentCookie, refresh).header("X-CSRF-TOKEN", currentToken))
                .andExpect(status().isOk()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        verify(sessions).revokeRefresh("test-refresh-credential", AuthSessionService.WEB);
    }
    @Test void getLogoutIsRejectedWith405AndCannotChangeTheSession() throws Exception {
        clearInvocations(sessions);
        mvc.perform(get("/api/user/web-logout").cookie(new Cookie(AuthCookies.REFRESH, "test-refresh-credential")))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(405))
                .andExpect(header().string("Allow", "POST"));
        verify(sessions, never()).revokeRefresh(any(), any());
        verify(sessions, never()).revokeAccess(any());
    }
    @Test void cookieDoesNotAuthenticateBusinessApiAndUnavailableStoreReturns503() throws Exception {
        mvc.perform(get("/api/user/info").cookie(new Cookie(AuthCookies.REFRESH,"test")))
                .andExpect(status().isUnauthorized());
        JwtUtil jwt = context.getBean(JwtUtil.class);
        String token = jwt.generateToken("alice", "admin", TokenDigests.randomToken(), 900000);
        when(sessions.validateAccess(any())).thenThrow(new AuthStoreUnavailableException());
        mvc.perform(get("/api/user/info").header("Authorization", "Bearer "+token))
                .andExpect(status().isServiceUnavailable());
        doReturn("user").when(sessions).validateAccess(any());
        mvc.perform(get("/api/user/admin/list").header("Authorization", "Bearer "+token))
                .andExpect(status().isForbidden());
    }
}
