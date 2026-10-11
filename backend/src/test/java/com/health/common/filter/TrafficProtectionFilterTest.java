package com.health.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.TrafficProtectionProperties;
import com.health.service.GlobalTrafficLimiter;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrafficProtectionFilterTest {
    private final GlobalTrafficLimiter limiter = mock(GlobalTrafficLimiter.class);
    private final TrafficProtectionProperties properties = new TrafficProtectionProperties();

    private TrafficProtectionFilter filter(int concurrent) {
        properties.setMaxConcurrentRequests(concurrent);
        return new TrafficProtectionFilter(properties, limiter, new ObjectMapper());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAsyncSupported(true);
        return request;
    }

    @Test void differentUsersAndIpsShareConcurrencyLimitWithoutQueueing() throws Exception {
        TrafficProtectionFilter filter = filter(2);
        ExecutorService threads = Executors.newFixedThreadPool(10);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch finish = new CountDownLatch(1);
        List<Future<Integer>> accepted = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                accepted.add(threads.submit(() -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    filter.doFilter(request("/api/user/info"), response, (req, res) -> {
                        entered.countDown();
                        try {
                            if (!finish.await(10, TimeUnit.SECONDS)) throw new ServletException("test timeout");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ServletException(e);
                        }
                    });
                    return response.getStatus();
                }));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            List<Future<Integer>> rejected = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                int user = i;
                rejected.add(threads.submit(() -> {
                    MockHttpServletRequest request = request("/api/doctor/list");
                    request.setRemoteAddr("192.0.2." + user);
                    request.addHeader("Authorization", "Bearer user-" + user);
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    filter.doFilter(request, response, (req, res) -> fail("超载请求不得进入下游"));
                    assertEquals("1", response.getHeader("Retry-After"));
                    return response.getStatus();
                }));
            }
            for (Future<Integer> response : rejected) assertEquals(429, response.get(5, TimeUnit.SECONDS));
            verify(limiter, times(2)).acquire();
            finish.countDown();
            for (Future<Integer> response : accepted) assertEquals(200, response.get(5, TimeUnit.SECONDS));
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/user/info"), response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        } finally {
            finish.countDown();
            threads.shutdownNow();
        }
    }

    @Test void failuresAndRateRejectionsReleaseConcurrencyPermit() throws Exception {
        TrafficProtectionFilter filter = filter(1);
        when(limiter.acquire()).thenReturn(1501L).thenThrow(new IllegalStateException("Redis down"))
                .thenReturn(0L);
        MockHttpServletResponse rateResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/user/login"), rateResponse, (req, res) -> fail("不得查库"));
        assertEquals(429, rateResponse.getStatus());
        assertEquals("2", rateResponse.getHeader("Retry-After"));
        assertEquals(429, new ObjectMapper().readTree(rateResponse.getContentAsString()).get("code").asInt());
        MockHttpServletResponse failureResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/user/login"), failureResponse, (req, res) -> fail("不得查库"));
        assertEquals(503, failureResponse.getStatus());
        assertThrows(ServletException.class, () -> filter.doFilter(request("/api/user/login"),
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("business failure"); }));
        MockHttpServletResponse recovered = new MockHttpServletResponse();
        filter.doFilter(request("/api/user/login"), recovered, (req, res) -> {});
        assertEquals(200, recovered.getStatus());
        verify(limiter, times(4)).acquire();
    }

    @Test void asyncRequestsHoldPermitUntilCompletionWithoutCountingRedispatchTwice() throws Exception {
        TrafficProtectionFilter filter = filter(1);
        MockHttpServletRequest initial = request("/api/chat/send/stream");
        AtomicReference<MockAsyncContext> context = new AtomicReference<>();
        filter.doFilter(initial, new MockHttpServletResponse(), (req, res) ->
                context.set((MockAsyncContext) req.startAsync()));
        initial.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(initial, new MockHttpServletResponse(), (req, res) -> {});
        verify(limiter, times(1)).acquire();

        MockAsyncContext async = context.get();
        for (var listener : List.copyOf(async.getListeners())) {
            listener.onTimeout(new AsyncEvent(async));
            listener.onError(new AsyncEvent(async, new RuntimeException("client disconnected")));
        }
        MockHttpServletResponse busy = new MockHttpServletResponse();
        filter.doFilter(request("/api/doctor/list"), busy, (req, res) -> fail("异步未完成，额度不能提前释放"));
        assertEquals(429, busy.getStatus());
        async.complete();
        // 重复完成通知不能增加额外额度。
        for (var listener : List.copyOf(async.getListeners())) listener.onComplete(new AsyncEvent(async));
        MockHttpServletRequest next = request("/api/chat/send/stream");
        AtomicReference<MockAsyncContext> nextContext = new AtomicReference<>();
        filter.doFilter(next, new MockHttpServletResponse(), (req, res) ->
                nextContext.set((MockAsyncContext) req.startAsync(req, res)));
        MockHttpServletResponse stillBusy = new MockHttpServletResponse();
        filter.doFilter(request("/api/doctor/list"), stillBusy, (req, res) -> fail("不能重复释放额度"));
        assertEquals(429, stillBusy.getStatus());
        nextContext.get().complete();
    }

    @Test void asyncCompletionBeforeChainReturnsDoesNotLeakOrDoubleRelease() throws Exception {
        TrafficProtectionFilter filter = filter(1);
        filter.doFilter(request("/api/chat/send/stream"), new MockHttpServletResponse(), (req, res) -> req.startAsync().complete());
        MockHttpServletResponse next = new MockHttpServletResponse();
        filter.doFilter(request("/api/user/info"), next, (req, res) -> {});
        assertEquals(200, next.getStatus());
    }

    @Test void staticFilesOptionsAndDisabledProtectionSkipLimiterButContextPathApiIsProtected() throws Exception {
        TrafficProtectionFilter filter = filter(1);
        for (String path : List.of("/", "/assets/app.js", "/login", "/api-other/test")) {
            filter.doFilter(request(path), new MockHttpServletResponse(), (req, res) -> {});
        }
        MockHttpServletRequest options = request("/api/user/login");
        options.setMethod("OPTIONS");
        filter.doFilter(options, new MockHttpServletResponse(), (req, res) -> {});
        verifyNoInteractions(limiter);
        MockHttpServletRequest contextual = request("/health/api/user/info");
        contextual.setContextPath("/health");
        when(limiter.acquire()).thenReturn(1L);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(contextual, limited, (req, res) -> fail("context path 不应绕过保护"));
        assertEquals(429, limited.getStatus());
        for (String path : List.of("/%61pi/user/info", "/api;v=1/user/info", "/api%2Fuser/info")) {
            MockHttpServletResponse encoded = new MockHttpServletResponse();
            filter.doFilter(request(path), encoded, (req, res) -> fail("路径编码/矩阵参数不应绕过保护"));
            assertEquals(429, encoded.getStatus());
        }
        properties.setEnabled(false);
        filter.doFilter(request("/api/user/login"), new MockHttpServletResponse(), (req, res) -> {});
        verify(limiter, times(4)).acquire();
    }
}
