package com.health.ai;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
@Component
public class InternalAuthorization {
    private final SecretKey key;
    private final StringRedisTemplate redis;
    public InternalAuthorization(@Value("${ai.service-secret}") String secret,StringRedisTemplate redis) {
        this.redis=redis;
        try { key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)); }
        catch(Exception e) { throw new IllegalStateException("AI_SERVICE_SECRET 必须是 Base64 编码的至少 32 字节密钥"); }
    }
    public String verify(String authorization,String body,String operation) {
        Claims claims;
        try {
            if (authorization==null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            var parsed=Jwts.parserBuilder().setSigningKey(key).requireIssuer("health-core").requireAudience("health-ai")
                    .build().parseClaimsJws(authorization.substring(7));
            if (!SignatureAlgorithm.HS256.getValue().equals(parsed.getHeader().getAlgorithm())) throw new IllegalArgumentException();
            claims=parsed.getBody();
            if (claims.getExpiration()==null || claims.getIssuedAt()==null || claims.getId()==null
                    || !claims.getSubject().matches("[1-9][0-9]*")
                    || claims.getExpiration().getTime()-claims.getIssuedAt().getTime()>60_000
                    || claims.getIssuedAt().getTime()>System.currentTimeMillis()+5000
                    || !operation.equals(claims.get("operation",String.class))) throw new IllegalArgumentException();
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            String expected=HexFormat.of().formatHex(hash);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    claims.get("bodyHash",String.class).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部服务认证失败"); }
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("ai:request:"+claims.getId(),"1",Duration.ofSeconds(120))))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请求已使用");
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"内部认证暂时不可用"); }
        return claims.getSubject();
    }
}
