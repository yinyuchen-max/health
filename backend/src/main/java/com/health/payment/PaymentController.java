package com.health.payment;

import com.health.common.annotation.RateLimit;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/ai/payments")
public class PaymentController {
    private final PaymentService payments;
    private final SecurityUtil security;
    public PaymentController(PaymentService payments,SecurityUtil security) { this.payments=payments; this.security=security; }
    public record Create(String plan,String channel,String requestId) {}
    @GetMapping("/catalog") public Result<?> catalog() { return Result.success(payments.catalog(security.getCurrentUserId())); }
    @GetMapping("/orders") public Result<?> list() { return Result.success(payments.list(security.getCurrentUserId())); }
    @PostMapping("/orders")
    @RateLimit(key="ai-payment-create",maxRequests=6,timeWindow=1,timeUnit=TimeUnit.MINUTES,limitBy=RateLimit.LimitType.USER)
    public Result<?> create(@RequestBody Create request) {
        return Result.success(payments.create(security.getCurrentUserId(),request.plan(),request.channel(),request.requestId()));
    }
    @PostMapping("/orders/{id}/refresh")
    @RateLimit(key="ai-payment-refresh",maxRequests=20,timeWindow=1,timeUnit=TimeUnit.MINUTES,limitBy=RateLimit.LimitType.USER)
    public Result<?> refresh(@PathVariable String id) { return Result.success(payments.refresh(security.getCurrentUserId(),id)); }
    @PostMapping("/orders/{id}/checkout")
    @RateLimit(key="ai-payment-create",maxRequests=6,timeWindow=1,timeUnit=TimeUnit.MINUTES,limitBy=RateLimit.LimitType.USER)
    public Result<?> checkout(@PathVariable String id) { return Result.success(payments.resumeCheckout(security.getCurrentUserId(),id)); }
    @PostMapping("/notify/wechat") public ResponseEntity<?> wechat(@RequestBody String body,@RequestHeader Map<String,String> headers) {
        try {
            Map<String,String> normalized=new java.util.HashMap<>();
            headers.forEach((name,value)->normalized.put(name.toLowerCase(java.util.Locale.ROOT),value));
            payments.notification("wechat",body,normalized,Map.of()); return ResponseEntity.noContent().build();
        }
        catch(Exception e) { return ResponseEntity.status(500).body(Map.of("code","FAIL","message","支付通知处理失败")); }
    }
    @PostMapping(value="/notify/alipay",consumes="application/x-www-form-urlencoded",produces="text/plain;charset=UTF-8")
    public ResponseEntity<String> alipay(@RequestParam Map<String,String> params) {
        try { payments.notification("alipay","",Map.of(),params); return ResponseEntity.ok("success"); }
        catch(Exception e) { return ResponseEntity.status(500).body("failure"); }
    }
}
