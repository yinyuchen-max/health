package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.utils.ContentHash;
import com.health.common.utils.SecurityUtil;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiClientContextHttpTest {
    @Test void contextContractIsSignedAndRejectsAnyRewrittenOrReorderedMedicalEvidence() throws Exception {
        var key=new byte[32]; var json=new ObjectMapper();
        var configs=mock(AiConfigService.class); var access=mock(AiAccessService.class);
        var mode=new AtomicInteger(); var failure=new AtomicReference<Throwable>();
        var history=List.of(new AiRequest.Message("user","我没有糖尿病，母亲用药0.25 mg","u1","2026-10-23T12:00:00"),
                new AiRequest.Message("assistant","请确认","a1",null),new AiRequest.Message("user","我对青霉素过敏","u2",null));
        var invocation=new AiRequest(7L,"deepseek","secret","test","chat",null,Map.of(),history,"怎么办");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/ai/context",exchange -> {
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                var claims=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key))
                        .requireIssuer("health-core").requireAudience("health-ai").build()
                        .parseClaimsJws(exchange.getRequestHeaders().getFirst("Authorization").substring(7)).getBody();
                assertEquals("context",claims.get("operation"));assertEquals("7",claims.getSubject());
                assertEquals(ContentHash.sha256(body),claims.get("bodyHash"));
                List<AiRequest.Message> selected=switch(mode.get()) {
                    case 1 -> List.of(new AiRequest.Message("user","我有糖尿病","u2",null));
                    case 2 -> List.of(history.get(2),history.get(0));
                    default -> List.of(history.get(2));
                };
                var cp=ConversationCheckpoint.ground(new ConversationCheckpoint.Draft(List.of(
                        new ConversationCheckpoint.Item("family","核对本人和家人",List.of("u1")))),Map.of("u1",history.get(0)),Set.of("u1"));
                if(mode.get()==3) cp=new ConversationCheckpoint(cp.items(),List.of(
                        new AiRequest.Message("user","我有糖尿病，正在用药25 mg","u1",history.get(0).recordedAt())));
                byte[] response=mode.get()==4?json.writeValueAsBytes(Map.of("history",List.of(),"compressed",true,
                        "requiresClarification",false,"beforeTokens",9000,"afterTokens",2500,"windowTokens",10000))
                        :json.writeValueAsBytes(new AiClient.ChatContext(selected,true,false,9000,2500,10000,cp,false));
                exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
            } catch(Throwable e) { failure.set(e); }
            finally { exchange.close(); }
        });server.start();
        try(var client=new AiClient(configs,access,mock(SecurityUtil.class),json,
                "http://127.0.0.1:"+server.getAddress().getPort(),Base64.getEncoder().encodeToString(key))) {
            assertEquals(List.of(history.get(2)),client.chatContext(invocation).history());
            mode.set(1); assertThrows(AiServiceException.class,()->client.chatContext(invocation));
            mode.set(2); assertThrows(AiServiceException.class,()->client.chatContext(invocation));
            mode.set(3); assertThrows(AiServiceException.class,()->client.chatContext(invocation));
            mode.set(4); assertThrows(AiClient.ContextProtocolException.class,()->client.chatContext(invocation));
            verify(access,times(5)).requireAccess(7L);
            if(failure.get()!=null) throw new AssertionError(failure.get());
        } finally { server.stop(0); }
    }
}
