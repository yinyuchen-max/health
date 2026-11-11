package com.health.controller;

import com.health.common.utils.SecurityUtil;
import com.health.memory.LongTermMemoryService;
import com.health.service.ChatFeedbackService;
import com.health.service.ChatService;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatStreamHeartbeatTest {
    @TempDir Path directory;
    @Configuration @EnableWebMvc static class WebConfiguration {}
    @Test void privatePreparationWaitProducesOnlySseCommentsThenUsualTokenAndDoneOverRealHttp() throws Exception {
        var service=mock(ChatService.class);var security=mock(SecurityUtil.class);when(security.getCurrentUserId()).thenReturn(1L);
        var callback=new AtomicReference<ChatService.StreamCallback>();
        doAnswer(inv->{callback.set(inv.getArgument(2));return null;}).when(service).chatStream(eq("继续"),eq(1L),any());
        var controller=new ChatController(service,security,mock(LongTermMemoryService.class),mock(ChatFeedbackService.class));
        var tomcat=new Tomcat();tomcat.setBaseDir(directory.toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",directory.toString());
        var web=new GenericWebApplicationContext();web.setServletContext(context.getServletContext());
        new AnnotatedBeanDefinitionReader(web).register(WebConfiguration.class);
        web.registerBean(ChatController.class,()->controller);web.refresh();
        var servlet=Tomcat.addServlet(context,"chat-api",new DispatcherServlet(web));servlet.setAsyncSupported(true);
        context.addServletMappingDecoded("/*","chat-api");
        var deadline=Executors.newSingleThreadScheduledExecutor();
        try {
            tomcat.start();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/api/chat/send/stream"))
                    .timeout(Duration.ofSeconds(6)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"继续\"}")).build();
            var response=HttpClient.newHttpClient().sendAsync(request,HttpResponse.BodyHandlers.ofInputStream()).get(6,TimeUnit.SECONDS);
            assertEquals(200,response.statusCode());
            try(var input=response.body()) {
                var abort=deadline.schedule(()->{try { input.close(); }catch(IOException ignored) {}},6,TimeUnit.SECONDS);
                var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
                String waiting=reader.readLine();assertEquals(":keepalive",waiting);assertEquals("",reader.readLine());
                assertFalse(waiting.contains("data:"));assertNotNull(callback.get());
                callback.get().onToken("请核实原文");callback.get().onComplete("请核实原文");
                var remaining=new StringBuilder();String line;while((line=reader.readLine())!=null) remaining.append(line).append('\n');
                assertTrue(remaining.toString().contains("event:token"));assertTrue(remaining.toString().contains("event:done"));
                assertFalse(remaining.toString().contains("checkpoint"));abort.cancel(false);
            }
        } finally { deadline.shutdownNow();controller.close();tomcat.stop();tomcat.destroy();web.close(); }
    }
}
