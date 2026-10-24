package com.health.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.concurrent.Semaphore;

@RestController
public class MemoryEmbeddingController {
    private final InternalAuthorization auth;
    private final ObjectMapper json;
    private final MemoryEmbeddingService embeddings;
    private final Semaphore slots=new Semaphore(2);
    public MemoryEmbeddingController(InternalAuthorization auth,ObjectMapper json,MemoryEmbeddingService embeddings) {
        this.auth=auth;this.json=json;this.embeddings=embeddings;
    }
    @PostMapping("/internal/ai/memory-embed")
    public MemoryEmbeddingService.Batch embed(@RequestHeader(value="Authorization",required=false) String token,@RequestBody String body) throws Exception {
        if(body.length()>40_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        String user=auth.verify(token,body,"memory-embed");
        AiRequest request=json.readValue(body,AiRequest.class);
        if(request.userId()==null||!user.equals(request.userId().toString())||!"memory-embed".equals(request.task())||request.variables()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        try {
            List<String> texts=json.readValue(request.variables().getOrDefault("texts","[]"),new TypeReference<>() {});
            return embeddings.embed(texts);
        } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
        finally { slots.release(); }
    }
}
