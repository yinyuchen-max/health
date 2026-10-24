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
    private final AiAccessService access;
    private final SecurityUtil security;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Semaphore inFlight=new Semaphore(8);
    private final SecretKey signingKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ThreadPoolExecutor streams = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t=new Thread(r,"health-ai-stream"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "health-ai-deadline"); t.setDaemon(true); return t;
    });
    public AiClient(AiConfigService configs, AiAccessService access, SecurityUtil security, ObjectMapper json,
                    @Value("${ai.service-url:http://127.0.0.1:8081}") String url,
                    @Value("${ai.service-secret}") String secret) {
        this.configs=configs; this.access=access; this.security=security; this.json=json;
        this.endpoint=URI.create(url.replaceAll("/+$", "") + "/internal/ai/");
        try { signingKey=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)); }
        catch (Exception e) { throw new IllegalStateException("AI_SERVICE_SECRET 必须是 Base64 编码的至少 32 字节密钥"); }
    }
    // Check the caller's entitlement for interactive requests and the owner's for scheduled jobs.
    public Long payer(Long ownerId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())
                ? security.getCurrentUserId() : Objects.requireNonNull(ownerId);
    }
    public void requireAccess(Long userId) { access.requireAccess(userId); }
    public String cacheScope(Long payerId) {
        requireAccess(payerId); // Must run before returning a previously generated AI report.
        return payerId + ":" + configs.revision(payerId);
    }
    public AiRequest prepare(Long payerId, String task, Map<String,String> variables,
                             List<AiRequest.Message> history, String message) {
        requireAccess(payerId);
        var c=configs.credentials(payerId);
        return new AiRequest(payerId,c.provider(),c.apiKey(),c.model(),task,c.embeddingApiKey(),variables,history,message);
    }
    public String complete(AiRequest invocation) {
        requireAccess(invocation.userId());
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
    public record ChatContext(List<AiRequest.Message> history,boolean compressed,boolean requiresClarification,
                              long beforeTokens,long afterTokens,int windowTokens,
                              ConversationCheckpoint checkpoint,boolean compactionFailed,String summaryModel) {
        public ChatContext(List<AiRequest.Message> history,boolean compressed,boolean clarify,long before,long after,int window,
                           ConversationCheckpoint checkpoint,boolean failed) {
            this(history,compressed,clarify,before,after,window,checkpoint,failed,null);
        }
        public ChatContext(List<AiRequest.Message> history,boolean compressed,boolean clarify,long before,long after,int window) {
            this(history,compressed,clarify,before,after,window,ConversationCheckpoint.empty(),false);
        }
    }
    /** A summary call may run here; regular completion and streaming never repeat it. */
    public static final class ContextProtocolException extends AiServiceException {
        public ContextProtocolException() { super("AI 服务版本不一致，请联系管理员"); }
    }
    public ChatContext chatContext(AiRequest invocation) {
        requireAccess(invocation.userId());
        if(!inFlight.tryAcquire()) throw new AiServiceException("AI 请求较多，请稍后重试");
        try {
            var response=http.send(request("context",invocation,Duration.ofSeconds(40)),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()==404) throw new ContextProtocolException();
            if(response.statusCode()!=200) throw failure(response.statusCode());
            var context=json.readValue(response.body(),ChatContext.class);
            if(context==null || context.checkpoint()==null) throw new ContextProtocolException();
            validateContext(invocation,context,json);
            return context;
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AiServiceException("AI 请求已取消"); }
        catch(AiServiceException e) { throw e; }
        catch(Exception e) { throw new AiServiceException("AI 服务暂时不可用，请稍后重试"); }
        finally { inFlight.release(); }
    }
    public static void validateContext(AiRequest invocation,ChatContext context,ObjectMapper json) throws Exception {
        if(context==null || context.history()==null || context.checkpoint()==null || context.windowTokens()<1
                || context.windowTokens()>2_000_000 || context.afterTokens()<0 || context.afterTokens()>100_000_000
                || context.beforeTokens()<context.afterTokens()
                || context.summaryModel()!=null && !context.summaryModel().matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}"))
            throw new IllegalArgumentException("无效的上下文预算");
        var original=invocation.history()==null?List.<AiRequest.Message>of():invocation.history();
        String serialized=invocation.variables().get("conversationCheckpoint");
        var previous=serialized==null?ConversationCheckpoint.empty():json.readValue(serialized,ConversationCheckpoint.class);
        if(!context.compressed()) {
            if(!original.equals(context.history()) || !previous.equals(context.checkpoint()) || context.afterTokens()!=context.beforeTokens())
                throw new IllegalArgumentException("失败或未触发的压缩不能改变上下文");
            return;
        }
        int cut=original.size()-context.history().size();
        if(cut<0 || !original.subList(cut,original.size()).equals(context.history()) || context.compactionFailed()
                || context.requiresClarification() || context.afterTokens()*100>(long)context.windowTokens()*30
                || context.afterTokens()>=context.beforeTokens()) throw new IllegalArgumentException("摘要或近期窗口未通过校验");
        var allowed=new LinkedHashMap<>(ConversationCheckpoint.sources(previous.evidence()));
        for(var entry:ConversationCheckpoint.sources(original.subList(0,cut)).entrySet()) {
            var old=allowed.putIfAbsent(entry.getKey(),entry.getValue());
            if(old!=null && !old.equals(entry.getValue())) throw new IllegalArgumentException("证据身份冲突");
        }
        Set<String> required=new HashSet<>();
        allowed.values().stream().filter(ConversationCheckpoint::critical).forEach(m->required.add(m.id()));
        context.checkpoint().validate(allowed,required);
    }
    public record MemoryVectors(boolean available,String model,int dimension,List<float[]> vectors) {}
    public MemoryVectors memoryVectors(Long userId,List<String> texts,boolean query) {
        requireAccess(userId);
        if(!inFlight.tryAcquire()) throw new AiServiceException("AI 请求较多，请稍后重试");
        try {
            var invocation=prepare(userId,"memory-embed",Map.of("texts",json.writeValueAsString(texts)),List.of(),null);
            var response=http.send(request("memory-embed",invocation,Duration.ofSeconds(query?2:6)),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()!=200) throw failure(response.statusCode());
            var result=json.readValue(response.body(),MemoryVectors.class);
            if(result.available()&&(result.vectors()==null||result.vectors().size()!=texts.size()||result.dimension()<1||result.dimension()>8192
                    ||result.model()==null||result.model().length()>100
                    ||result.vectors().stream().anyMatch(v->v==null||v.length!=result.dimension()||!finite(v)))) throw new AiServiceException("无效的记忆向量响应");
            return result;
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new AiServiceException("AI 请求已取消"); }
        catch(AiServiceException e) { throw e; }
        catch(Exception e) { throw new AiServiceException("记忆向量服务暂不可用"); }
        finally { inFlight.release(); }
    }
    private static boolean finite(float[] vector) { for(float value:vector) if(!Float.isFinite(value)) return false;return true; }
    public void stream(AiRequest invocation, ChatService.StreamCallback callback) {
        requireAccess(invocation.userId());
        if(!inFlight.tryAcquire()) { callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); return; }
        try { streams.execute(() -> { try { readStream(invocation,callback); } finally { inFlight.release(); } }); }
        catch (RejectedExecutionException e) { inFlight.release(); callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); }
    }
    private void readStream(AiRequest invocation, ChatService.StreamCallback callback) {
        try {
            requireAccess(invocation.userId()); // Recheck queued work at actual dispatch time.
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
        return request(operation,invocation,Duration.ofSeconds(90));
    }
    private HttpRequest request(String operation,AiRequest invocation,Duration timeout) throws Exception {
        String body=json.writeValueAsString(invocation);
        long now=System.currentTimeMillis();
        String token=Jwts.builder().setIssuer("health-core").setAudience("health-ai")
                .setSubject(invocation.userId().toString()).setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(now)).setExpiration(new Date(now+60_000))
                .claim("bodyHash",ContentHash.sha256(body)).claim("operation",operation)
                .signWith(signingKey,SignatureAlgorithm.HS256).compact();
        return HttpRequest.newBuilder(endpoint.resolve(operation)).timeout(timeout)
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
    }
    private AiServiceException failure(int status) {
        return new AiServiceException(switch(status) {
            case 422 -> "平台 AI 服务暂不可用，请稍后重试或联系管理员";
            case 429 -> "AI 请求较多，请稍后重试";
            default -> "AI 服务暂时不可用，请稍后重试";
        });
    }
    @jakarta.annotation.PreDestroy public void close() { streams.shutdownNow(); deadlines.shutdownNow(); }
}
