package com.health.ai;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class InternalAuthorizationTest {
    private final byte[] bytes=new byte[32];
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final InternalAuthorization auth=new InternalAuthorization(Base64.getEncoder().encodeToString(bytes),redis);
    private String token(String body,String operation,long expires) {
        return "Bearer "+Jwts.builder().setIssuer("health-core").setAudience("health-ai").setSubject("1")
                .setIssuedAt(new Date(System.currentTimeMillis()-500)).setExpiration(new Date(expires)).setId("nonce")
                .claim("operation",operation).claim("bodyHash",ContentHash.sha256(body))
                .signWith(Keys.hmacShaKeyFor(bytes),SignatureAlgorithm.HS256).compact();
    }
    @Test void acceptsOnlySignedBodyBoundShortLivedSingleUseRequests() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),eq("1"),eq(Duration.ofSeconds(120)))).thenReturn(true,false);
        String body="{\"userId\":1}",token=token(body,"complete",System.currentTimeMillis()+30000);
        assertEquals("1",auth.verify(token,body,"complete"));
        assertThrows(ResponseStatusException.class,()->auth.verify(token,body,"complete"));
        assertThrows(ResponseStatusException.class,()->auth.verify(token,body+" ","complete"));
        assertThrows(ResponseStatusException.class,()->auth.verify(token,body,"stream"));
        assertThrows(ResponseStatusException.class,()->auth.verify(null,body,"complete"));
        assertThrows(ResponseStatusException.class,()->auth.verify(token(body,"complete",System.currentTimeMillis()-1000),body,"complete"));
        verify(values,times(2)).setIfAbsent(anyString(),eq("1"),any(Duration.class));
    }
    @Test void redisOutageFailsClosed() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        var e=assertThrows(ResponseStatusException.class,()->auth.verify(token("x","complete",System.currentTimeMillis()+30000),"x","complete"));
        assertEquals(503,e.getStatusCode().value());
    }
}
