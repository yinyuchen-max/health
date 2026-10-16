package com.health.payment;
import java.util.Map;
public interface PaymentGateway {
    String channel();
    boolean enabled();
    String create(PaymentOrder order);
    PaymentResult query(PaymentOrder order);
    void close(PaymentOrder order);
    PaymentResult notification(String body,Map<String,String> headers,Map<String,String> parameters);
    record PaymentResult(String channel,String orderId,String transactionId,int amountCents,String currency,String status) {
        public boolean paid() { return "PAID".equals(status); }
    }
}
