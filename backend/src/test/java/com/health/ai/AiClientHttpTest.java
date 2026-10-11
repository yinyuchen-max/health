package com.health.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.utils.*;
import com.health.service.ChatService;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiClientHttpTest {
    @Test void realHttpContractBindsUserKeyAndBodyAndRelaysStreamAndSanitizesErrors() throws Exception {
        byte[] key=new byte[32]; var json=new ObjectMapper(); var configs=mock(AiConfigService.class); var security=mock(SecurityUtil.class);
        when(configs.credentials(1L)).thenReturn(new AiConfigService.Credentials("deepseek","test","key-of-user-one",null,"v1"));
        when(configs.credentials(2L)).thenReturn(new AiConfigService.Credentials("dashscope","test2","key-of-user-two",null,"v2"));
        when(configs.revision(1L)).thenReturn("v1"); when(security.getCurrentUserId()).thenReturn(1L);
        var failure=new AtomicReference<Throwable>(); var keys=new CopyOnWriteArrayList<String>(); var invalid=new AtomicBoolean();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/ai/",exchange->{
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                var claims=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).requireIssuer("health-core").requireAudience("health-ai")
                        .build().parseClaimsJws(exchange.getRequestHeaders().getFirst("Authorization").substring(7)).getBody();
                var invocation=json.readValue(body,AiRequest.class);
                assertEquals(invocation.userId().toString(),claims.getSubject()); assertEquals(ContentHash.sha256(body),claims.get("bodyHash"));
                assertTrue(claims.getExpiration().getTime()-claims.getIssuedAt().getTime()<=60000);
                keys.add(invocation.apiKey()); String response;
                if(invalid.get()) { response="upstream leaked key-of-user-one"; exchange.sendResponseHeaders(422,response.length()); }
                else if(exchange.getRequestURI().getPath().endsWith("stream")) {
                    assertEquals("stream",claims.get("operation")); exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                    response="event: thinking\ndata: \"思考\"\n\nevent: token\ndata: \"回答\\n下一行\"\n\nevent: done\ndata: \"完整回答\"\n\n";
                    exchange.sendResponseHeaders(200,response.getBytes(StandardCharsets.UTF_8).length);
                } else {
                    assertEquals("complete",claims.get("operation")); response="{\"reply\":\"ok\"}"; exchange.sendResponseHeaders(200,response.length());
                }
                exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            } catch(Throwable e) { failure.set(e); }
            finally { exchange.close(); }
        }); server.start();
        try(var client=new AiClient(configs,security,json,"http://127.0.0.1:"+server.getAddress().getPort(),Base64.getEncoder().encodeToString(key))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-one","unused",List.of()));
            assertEquals(1L,client.payer(2L)); assertEquals("1:v1",client.cacheScope(1L)); SecurityContextHolder.clearContext();
            assertEquals(2L,client.payer(2L));
            for(long user:List.of(1L,2L)) assertEquals("ok",client.complete(client.prepare(user,"chat",Map.of(),List.of(),"hello")));
            var done=new CountDownLatch(1); var tokens=new StringBuilder(); var thinking=new StringBuilder(); var full=new AtomicReference<String>();
            client.stream(client.prepare(1L,"chat",Map.of(),List.of(),"hello"),new ChatService.StreamCallback() {
                public void onToken(String token) { tokens.append(token); }
                public void onThinking(String token) { thinking.append(token); }
                public void onComplete(String text) { full.set(text); done.countDown(); }
                public void onError(Throwable e) { failure.set(e); done.countDown(); }
            });
            assertTrue(done.await(5,TimeUnit.SECONDS)); assertEquals("回答\n下一行",tokens.toString()); assertEquals("思考",thinking.toString()); assertEquals("完整回答",full.get());
            invalid.set(true); var e=assertThrows(AiServiceException.class,()->client.complete(client.prepare(1L,"chat",Map.of(),List.of(),"hello")));
            assertFalse(e.getMessage().contains("key-of-user-one")); assertEquals(List.of("key-of-user-one","key-of-user-two","key-of-user-one","key-of-user-one"),keys);
            if(failure.get()!=null) throw new AssertionError(failure.get());
        } finally { SecurityContextHolder.clearContext(); server.stop(0); }
    }
}
