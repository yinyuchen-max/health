package com.health.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.TrafficProtectionProperties;
import com.health.service.GlobalTrafficLimiter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 用隔离 Tomcat 验证真实 ASYNC 分发/重启/完成；不启动业务服务，不连接数据库或 Redis。 */
class TrafficProtectionAsyncHttpTest {
    @TempDir Path directory;

    @Test void asyncRestartHoldsTheSamePermitUntilFinalHttpCompletion() throws Exception {
        TrafficProtectionProperties properties = new TrafficProtectionProperties();
        properties.setMaxConcurrentRequests(1);
        AtomicInteger admissions = new AtomicInteger();
        GlobalTrafficLimiter limiter = new GlobalTrafficLimiter(null, properties) {
            @Override public long acquire() { admissions.incrementAndGet(); return 0; }
        };
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        Context context = tomcat.addContext("", directory.toString());
        AtomicReference<AsyncContext> current = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch restarted = new CountDownLatch(1);
        var servlet = Tomcat.addServlet(context, "isolated-api", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                switch (request.getRequestURI()) {
                    case "/api/hold" -> {
                        AsyncContext async = request.startAsync();
                        async.setTimeout(10000);
                        current.set(async);
                        started.countDown();
                    }
                    case "/api/restart" -> {
                        AsyncContext async = request.startAsync();
                        async.setTimeout(10000);
                        current.set(async);
                        restarted.countDown();
                    }
                    case "/api/immediate" -> request.startAsync().complete();
                    default -> response.getWriter().write("ok");
                }
            }
        });
        servlet.setAsyncSupported(true);
        context.addServletMappingDecoded("/api/*", "isolated-api");
        FilterDef definition = new FilterDef();
        definition.setFilterName("traffic");
        definition.setFilter(new TrafficProtectionFilter(properties, limiter, new ObjectMapper()));
        definition.setAsyncSupported("true");
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("traffic");
        mapping.addURLPattern("/*");
        mapping.setDispatcher("REQUEST");
        mapping.setDispatcher("ASYNC");
        context.addFilterMap(mapping);
        try {
            tomcat.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort();
            var held = client.sendAsync(http(base + "/api/hold"), HttpResponse.BodyHandlers.ofString());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(429, client.send(http(base + "/api/plain"), HttpResponse.BodyHandlers.ofString()).statusCode());
            current.get().dispatch("/api/restart");
            assertTrue(restarted.await(5, TimeUnit.SECONDS));
            assertEquals(429, client.send(http(base + "/api/plain"), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, admissions.get(), "异步重启不能重复消耗全局令牌");
            current.get().complete();
            assertEquals(200, held.get(5, TimeUnit.SECONDS).statusCode());
            // HTTP 完成与监听器回调存在极短竞争窗口，等待完成事件归还额度。
            HttpResponse<String> recovered = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                recovered = client.send(http(base + "/api/plain"), HttpResponse.BodyHandlers.ofString());
            } while (recovered.statusCode() == 429 && System.nanoTime() < deadline);
            assertEquals(200, recovered.statusCode());
            assertEquals(2, admissions.get());
            assertEquals(200, client.send(http(base + "/api/immediate"), HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private HttpRequest http(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(8)).GET().build();
    }
}
