package com.health.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.utils.SecurityUtil;
import com.health.common.utils.ContentHash;
import com.health.service.ChatService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AiClient implements AutoCloseable {
    private final AiConfigService configs;
    private final SecurityUtil security;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Semaphore inFlight=new Semaphore(8);
    private final SecretKey signingKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ThreadPoolExecutor streams = new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t=new Thread(r,"health-ai-stream"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "health-ai-deadline"); t.setDaemon(true); return t;
    });
    public AiClient(AiConfigService configs, SecurityUtil security, ObjectMapper json,
                    @Value("${ai.service-url:http://127.0.0.1:8081}") String url,
                    @Value("${ai.service-secret}") String secret) {
        this.configs=configs; this.security=security; this.json=json;
        this.endpoint=URI.create(url.replaceAll("/+$", "") + "/internal/ai/");
        try { signingKey=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)); }
        catch (Exception e) { throw new IllegalStateException("AI_SERVICE_SECRET 必须是 Base64 编码的至少 32 字节密钥"); }
    }
    // Interactive family/admin analysis uses the caller's key. Scheduled jobs use the record owner's key.
    public Long payer(Long ownerId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())
                ? security.getCurrentUserId() : Objects.requireNonNull(ownerId);
    }
    public String cacheScope(Long payerId) { return payerId + ":" + configs.revision(payerId); }
    public AiRequest prepare(Long payerId, String task, Map<String,String> variables,
                             List<AiRequest.Message> history, String message) {
        var c=configs.credentials(payerId);
        return new AiRequest(payerId,c.provider(),c.apiKey(),c.model(),task,c.embeddingApiKey(),variables,history,message);
    }
    public String complete(AiRequest invocation) {
        if(!inFlight.tryAcquire()) throw new AiServiceException("AI 请求较多，请稍后重试");
        try {
            var response=http.send(request("complete",invocation),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode()!=200) throw failure(response.statusCode());
            String reply=json.readTree(response.body()).path("reply").asText();
            if (reply.isBlank()) throw new AiServiceException("AI 未返回有效内容，请稍后重试");
            return reply;
        } catch (AiServiceException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AiServiceException("AI 请求已取消"); }
        catch (Exception e) { throw new AiServiceException("AI 服务暂时不可用，请稍后重试"); }
        finally { inFlight.release(); }
    }
    public void stream(AiRequest invocation, ChatService.StreamCallback callback) {
        if(!inFlight.tryAcquire()) { callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); return; }
        try { streams.execute(() -> { try { readStream(invocation,callback); } finally { inFlight.release(); } }); }
        catch (RejectedExecutionException e) { inFlight.release(); callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); }
    }
    private void readStream(AiRequest invocation, ChatService.StreamCallback callback) {
        try {
            var response=http.send(request("stream",invocation),HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input=response.body()) {
                if (response.statusCode()!=200) throw failure(response.statusCode());
                // HttpRequest timeout ends at headers for InputStream bodies; explicitly close stalled streams.
                var timeout=deadlines.schedule(() -> { try { input.close(); } catch (IOException ignored) {} },95,TimeUnit.SECONDS);
                try (var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
                    String event="", data="";
                    String line;
                    while ((line=reader.readLine())!=null) {
                        if (line.startsWith("event:")) event=line.substring(6).trim();
                        else if (line.startsWith("data:")) data=line.substring(5).trim();
                        else if (line.isEmpty() && !data.isEmpty()) {
                            String text=json.readValue(data,String.class);
                            switch (event) {
                                case "token" -> callback.onToken(text);
                                case "thinking" -> callback.onThinking(text);
                                case "done" -> { callback.onComplete(text); return; }
                                case "error" -> throw new AiServiceException(text);
                            }
                            event=""; data="";
                        }
                    }
                    throw new AiServiceException("AI 连接已中断，请重试");
                } finally { timeout.cancel(false); }
            }
        } catch (AiServiceException e) { callback.onError(e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); callback.onError(new AiServiceException("AI 请求已取消")); }
        catch (Exception e) { callback.onError(new AiServiceException("AI 服务暂时不可用，请稍后重试")); }
    }
    private HttpRequest request(String operation,AiRequest invocation) throws Exception {
        String body=json.writeValueAsString(invocation);
        long now=System.currentTimeMillis();
        String token=Jwts.builder().setIssuer("health-core").setAudience("health-ai")
                .setSubject(invocation.userId().toString()).setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(now)).setExpiration(new Date(now+60_000))
                .claim("bodyHash",ContentHash.sha256(body)).claim("operation",operation)
                .signWith(signingKey,SignatureAlgorithm.HS256).compact();
        return HttpRequest.newBuilder(endpoint.resolve(operation)).timeout(Duration.ofSeconds(90))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
    }
    private AiServiceException failure(int status) {
        return new AiServiceException(switch(status) {
            case 422 -> "API Key 无效、余额不足或模型不可用，请检查「AI 设置」";
            case 429 -> "AI 请求较多，请稍后重试";
            default -> "AI 服务暂时不可用，请稍后重试";
        });
    }
    @jakarta.annotation.PreDestroy public void close() { streams.shutdownNow(); deadlines.shutdownNow(); }
}
