package com.health.payment;
import java.time.LocalDateTime;
public record PaymentOrder(String id,Long userId,String plan,String channel,int amountCents,String currency,
                           String status,String requestId,String checkoutUrl,String transactionId,
                           LocalDateTime createdAt,LocalDateTime expiresAt,LocalDateTime paidAt) {}
