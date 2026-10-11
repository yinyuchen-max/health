package com.health.common.security;

import com.health.common.utils.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecurityTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    static JwtUtil jwt() {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "expiration", 900000L);
        return jwt;
    }
    @Test void requiresIssuerAudienceAndAccessType() {
        JwtUtil jwt = jwt();
        String token = jwt.generateToken("alice", "user", TokenDigests.randomToken(), 900000);
        var claims = jwt.parseAccessToken(token);
        assertEquals("alice", claims.getSubject());
        assertEquals("access", claims.get("type"));
        assertNotNull(claims.getId());
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() <= 901000);
        for (String audience : new String[]{"other", "health-clients"}) {
            String invalid = Jwts.builder().setSubject("alice").setIssuer("health-system").setAudience(audience)
                    .setExpiration(new Date(System.currentTimeMillis()+900000))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
            assertThrows(RuntimeException.class, () -> jwt.parseAccessToken(invalid));
        }
    }
    @Test void rotationAndExpirationInvalidateOldTokens() {
        JwtUtil jwt = jwt();
        String old = jwt.generateToken("alice", "user", TokenDigests.randomToken(), 900000);
        ReflectionTestUtils.setField(jwt, "secret", "another-test-only-random-secret-at-least-32-bytes");
        assertThrows(RuntimeException.class, () -> jwt.parseAccessToken(old));
        assertThrows(RuntimeException.class, () -> jwt.parseAccessToken(jwt.generateToken("alice", "user", TokenDigests.randomToken(), -1000)));
    }
}
