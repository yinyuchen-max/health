package com.health.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
@RestController
public class AiController {
    private final InternalAuthorization auth;
    private final ObjectMapper json;
    private final AiEngine engine;
    private final Semaphore slots;
    public AiController(InternalAuthorization auth,ObjectMapper json,AiEngine engine,
                        @Value("${ai.max-concurrent:8}") int concurrent) {
        this.auth=auth; this.json=json; this.engine=engine; slots=new Semaphore(Math.max(1,concurrent));
    }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","UP"); }
    @PostMapping("/internal/ai/complete")
    public Map<String,String> complete(@RequestHeader(value="Authorization",required=false) String token,@RequestBody String body) throws Exception {
        AiRequest r=read(token,body,"complete");
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        try { return Map.of("reply",engine.complete(r)); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"AI 请求无效"); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"密钥、额度或模型不可用"); }
        finally { slots.release(); }
    }
    @PostMapping(value="/internal/ai/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value="Authorization",required=false) String token,@RequestBody String body) throws Exception {
        AiRequest r=read(token,body,"stream");
        if (!"chat".equals(r.task())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        SseEmitter emitter=new SseEmitter(85_000L);
        AtomicBoolean released=new AtomicBoolean();
        AtomicBoolean finished=new AtomicBoolean();
        Runnable release=()->{ if(released.compareAndSet(false,true)) slots.release(); };
        emitter.onCompletion(()->finished.set(true));
        emitter.onTimeout(()->{ finished.set(true); emitter.complete(); });
        emitter.onError(e->finished.set(true));
        try {
            engine.stream(r,new StreamingChatResponseHandler() {
                private final StringBuilder partialText=new StringBuilder();
                private void send(String event,String value) {
                    if (finished.get() || value==null) return;
                    try { emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(value))); }
                    catch(Exception e) { finished.set(true); emitter.completeWithError(e); }
                }
                @Override public void onPartialResponse(String value) { if(value!=null) partialText.append(value); send("token",value); }
                @Override public void onPartialThinking(PartialThinking value) { send("thinking",value.text()); }
                @Override public void onCompleteResponse(ChatResponse response) {
                    String reply=response!=null && response.aiMessage()!=null ? response.aiMessage().text() : null;
                    if(reply==null || reply.isBlank()) reply=partialText.toString();
                    if(reply.isBlank()) send("error","AI 未返回有效内容，请稍后重试");
                    else send("done",reply);
                    finished.set(true); release.run(); emitter.complete();
                }
                @Override public void onError(Throwable error) {
                    send("error","平台 AI 服务暂不可用，请稍后重试或联系管理员");
                    finished.set(true); release.run(); emitter.complete();
                }
            });
        } catch(Exception e) {
            try { emitter.send(SseEmitter.event().name("error").data(json.writeValueAsString("AI 请求失败，请稍后重试"))); }
            catch(Exception ignored) {}
            release.run(); emitter.complete();
        }
        return emitter;
    }
    private AiRequest read(String token,String body,String operation) throws Exception {
        if(body.length()>1_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        String userId=auth.verify(token,body,operation);
        AiRequest r;
        try { r=json.readValue(body,AiRequest.class); }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        if(r.userId()==null || !userId.equals(r.userId().toString()) || r.apiKey()==null || r.apiKey().length()>512
                || r.apiKey().isBlank() || r.embeddingApiKey()!=null && r.embeddingApiKey().length()>512 || r.model()==null || !r.model().matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}")
                || r.variables()==null || r.history()!=null && r.history().size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return r;
    }
}
