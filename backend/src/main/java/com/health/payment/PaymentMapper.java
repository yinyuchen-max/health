package com.health.payment;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentMapper {
    String COLUMNS="id,user_id,plan_code AS plan,channel,amount_cents,currency,status,request_id,checkout_url,transaction_id,created_at,expires_at,paid_at";
    @Select("SELECT UTC_TIMESTAMP()") LocalDateTime now();
    @Select("SELECT id FROM sys_user WHERE id=#{id} FOR UPDATE") Long lockUser(Long id);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE id=#{id}") PaymentOrder find(String id);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE id=#{id} FOR UPDATE") PaymentOrder lock(String id);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE user_id=#{userId} AND request_id=#{requestId}")
    PaymentOrder byRequest(@Param("userId") Long userId,@Param("requestId") String requestId);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE user_id=#{userId} AND status='PENDING' ORDER BY created_at LIMIT 1")
    PaymentOrder pending(Long userId);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE user_id=#{userId} ORDER BY created_at DESC LIMIT 20") List<PaymentOrder> list(Long userId);
    @Insert("INSERT INTO ai_payment_order(id,user_id,plan_code,channel,amount_cents,currency,status,request_id,created_at,expires_at,next_check_at) "
            +"VALUES(#{id},#{userId},#{plan},#{channel},#{amountCents},#{currency},'PENDING',#{requestId},#{createdAt},#{expiresAt},#{createdAt})")
    void insert(PaymentOrder order);
    @Update("UPDATE ai_payment_order SET checkout_url=#{url} WHERE id=#{id} AND status='PENDING'")
    void checkout(@Param("id") String id,@Param("url") String url);
    @Update("UPDATE ai_payment_order SET status='PAID',transaction_id=#{transaction},paid_at=#{now} WHERE id=#{id} AND status='PENDING'")
    int paid(@Param("id") String id,@Param("transaction") String transaction,@Param("now") LocalDateTime now);
    @Update("UPDATE ai_payment_order SET status='CLOSED' WHERE id=#{id} AND status='PENDING'") int closed(String id);
    @Select("SELECT plan_code AS plan,expires_at,status FROM ai_entitlement WHERE user_id=#{id}") Membership membership(Long id);
    @Insert("INSERT INTO ai_entitlement(user_id,plan_code,status,expires_at) VALUES(#{id},#{plan},'ACTIVE',#{expires}) "
            +"ON DUPLICATE KEY UPDATE plan_code=VALUES(plan_code),status='ACTIVE',expires_at=VALUES(expires_at)")
    void grant(@Param("id") Long userId,@Param("plan") String plan,@Param("expires") LocalDateTime expires);
    @Select("SELECT "+COLUMNS+" FROM ai_payment_order WHERE status='PENDING' AND next_check_at<=UTC_TIMESTAMP() ORDER BY next_check_at LIMIT 30")
    List<PaymentOrder> due();
    @Update("UPDATE ai_payment_order SET next_check_at=#{nextCheck} WHERE id=#{id}")
    void postpone(@Param("id") String id,@Param("nextCheck") LocalDateTime nextCheck);
    record Membership(String plan,LocalDateTime expiresAt,String status) {}
}
