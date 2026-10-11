package com.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.health.common.exception.AuthStoreUnavailableException;
import com.health.common.exception.UnauthorizedException;
import com.health.common.security.TokenDigests;
import com.health.common.utils.JwtUtil;
import com.health.domain.dto.AuthTokens;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Device sessions have a fixed maximum lifetime; refreshing never extends it. */
@Service
public class AuthSessionService {
    public static final String WEB = "web", NATIVE = "native";
    private final StringRedisTemplate redis;
    private final JwtUtil jwt;
    private final UserMapper users;
    @Value("${jwt.refresh-expiration:7d}") private String refreshExpirationValue = "7d";
    private Duration refreshExpiration = Duration.ofDays(7);
    private final DefaultRedisScript<String> create = new DefaultRedisScript<>();
    private final DefaultRedisScript<List> rotate = script("rotate-session.lua");
    private final DefaultRedisScript<List> validate = script("validate-session.lua");

    public AuthSessionService(StringRedisTemplate redis, JwtUtil jwt, UserMapper users) {
        this.redis = redis; this.jwt = jwt; this.users = users;
        create.setLocation(new ClassPathResource("auth/create-session.lua"));
        create.setResultType(String.class);
    }
    private static DefaultRedisScript<List> script(String name) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("auth/" + name)); script.setResultType(List.class);
        return script;
    }
    @PostConstruct void validateConfiguration() {
        refreshExpiration = org.springframework.boot.convert.DurationStyle.detectAndParse(refreshExpirationValue);
        if (refreshExpiration == null || refreshExpiration.toMillis() <= jwt.getExpirationMillis())
            throw new IllegalArgumentException("JWT_REFRESH_EXPIRATION 必须大于 Access Token 有效期");
    }
    public AuthTokens issue(User user, String client) {
        // Check the authenticated snapshot again: a concurrent password/status change cannot create a usable session.
        User current = findUser(user.getUsername());
        if (!Objects.equals(credentials(user), credentials(current))) throw unauthorized();
        String sid = TokenDigests.randomToken();
        String refresh = sid + "." + TokenDigests.randomToken();
        long expires = Math.addExact(System.currentTimeMillis(), refreshExpiration.toMillis());
        store(() -> redis.execute(create, List.of(sessionKey(sid), epochKey(user.getUsername())),
                user.getUsername(), current.getRole() == null ? "user" : current.getRole(),
                TokenDigests.sha256(refresh), Long.toString(expires), client, credentials(current)));
        return tokens(sid, refresh, current.getUsername(), current.getRole(), expires);
    }
    public AuthTokens refresh(String refresh, String client) {
        String sid = parseRefresh(refresh);
        Map<Object, Object> state = store(() -> redis.opsForHash().entries(sessionKey(sid)));
        if (state.isEmpty() || !client.equals(state.get("client"))) throw unauthorized();
        String username = (String) state.get("username");
        User current = findUser(username);
        if (!credentials(current).equals(state.get("credentials"))) {
            revokeSession(sid); throw unauthorized();
        }
        String next = sid + "." + TokenDigests.randomToken();
        String oldHash = TokenDigests.sha256(refresh);
        List<?> result = store(() -> redis.execute(rotate,
                List.of(sessionKey(sid), epochKey(username), "auth:used-refresh:" + oldHash),
                oldHash, TokenDigests.sha256(next), client, Long.toString(System.currentTimeMillis())));
        if (result == null || result.size() != 3) throw unauthorized();
        return tokens(sid, next, username, current.getRole(), Long.parseLong((String) result.get(2)));
    }
    public String validateAccess(Claims claims) {
        String sid = claims.get("sid", String.class);
        String username = claims.getSubject();
        if (sid == null || !sid.matches("[A-Za-z0-9_-]{43}") || username == null) throw unauthorized();
        List<?> result = store(() -> redis.execute(validate, List.of(sessionKey(sid), epochKey(username)),
                username, Long.toString(System.currentTimeMillis())));
        if (result == null || result.size() != 1) throw unauthorized();
        User current = findUser(username);
        if (!credentials(current).equals(result.get(0))) throw unauthorized();
        return current.getRole() == null ? "user" : current.getRole();
    }
    public void revokeAccess(String token) { revokeSession(jwt.parseAccessToken(token).get("sid", String.class)); }
    public void revokeRefresh(String refresh, String client) {
        String sid = parseRefresh(refresh);
        // Rotate atomically first to authenticate the refresh credential, then remove the whole session.
        refresh(refresh, client);
        revokeSession(sid);
    }
    public void revokeAll(String username) { store(() -> redis.opsForValue().increment(epochKey(username))); }
    private void revokeSession(String sid) {
        if (sid == null || !sid.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        store(() -> redis.delete(sessionKey(sid)));
    }
    private AuthTokens tokens(String sid, String refresh, String username, String role, long expires) {
        long ttl = Math.min(jwt.getExpirationMillis(), expires - System.currentTimeMillis());
        if (ttl <= 0) throw unauthorized();
        return new AuthTokens(jwt.generateToken(username, role, sid, ttl), refresh, ttl / 1000, expires);
    }
    private User findUser(String username) {
        User user = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw unauthorized();
        return user;
    }
    private static String credentials(User user) {
        return TokenDigests.sha256(user.getId() + ":" + user.getPassword() + ":" + user.getStatus() + ":" + user.getRole());
    }
    private static String parseRefresh(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}")) throw unauthorized();
        return value.substring(0, 43);
    }
    private static String sessionKey(String sid) { return "auth:session:" + sid; }
    private static String epochKey(String username) { return "auth:epoch:" + TokenDigests.sha256(username); }
    private static UnauthorizedException unauthorized() { return new UnauthorizedException("登录已失效，请重新登录"); }
    private <T> T store(Supplier<T> operation) {
        try { return operation.get(); }
        catch (org.springframework.dao.DataAccessException e) { throw new AuthStoreUnavailableException(); }
    }
}
