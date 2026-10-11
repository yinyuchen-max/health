package com.health.common.utils;

import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.issuer:health-system}")
    private String issuer = "health-system";

    @Value("${jwt.audience:health-clients}")
    private String audience = "health-clients";

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET 必须至少包含 32 字节，使用密码学安全随机生成的密钥");
        if (expiration == null || expiration <= 0) throw new IllegalArgumentException("jwt.expiration 必须为正数毫秒");
    }

    public long getExpirationMillis() { return expiration; }

    public String generateToken(String username, String role, String sessionId, long ttlMillis) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role != null ? role : "user")
                .claim("sid", sessionId)
                .claim("type", "access")
                .setId(UUID.randomUUID().toString())
                .setIssuer(issuer).setAudience(audience)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    public Claims parseAccessToken(String token) {
        var parsed = Jwts.parserBuilder().setSigningKey(getSigningKey())
                .requireIssuer(issuer).requireAudience(audience).require("type", "access")
                .build().parseClaimsJws(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) throw new IllegalArgumentException("Unsupported signing algorithm");
        Claims claims = parsed.getBody();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null || claims.getId() == null)
            throw new IllegalArgumentException("Missing required access claims");
        return claims;
    }

    public Boolean validateToken(String token, String username) {
        final String extractedUsername = getUsernameFromToken(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return parseAccessToken(token);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 从token中获取用户名
     * @param token JWT token
     * @return 用户名
     */
    public String extractUsername(String token) {
        return getUsernameFromToken(token);
    }

    /**
     * 从token中获取用户角色
     * @param token JWT token
     * @return 角色（admin/user）
     */
    public String extractRole(String token) {
        return getClaimFromToken(token, claims -> claims.get("role", String.class));
    }

    /**
     * 获取 token 签发时间
     * @param token JWT token
     * @return 签发时间
     */
    public Date getIssuedAt(String token) {
        return getClaimFromToken(token, Claims::getIssuedAt);
    }

    /**
     * 验证是否为管理员
     * @param token JWT token
     * @return 是否为管理员
     */
    public Boolean isAdmin(String token) {
        String role = extractRole(token);
        return "admin".equals(role);
    }
}
