package com.health.payment;

import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.springframework.stereotype.Component;
import java.time.ZoneId;
import java.util.Map;

@Component
public class WechatPaymentGateway implements PaymentGateway {
    private final PaymentProperties properties;
    private final NativePayService service;
    private final NotificationParser parser;
    public WechatPaymentGateway(PaymentProperties properties) {
        this.properties=properties;
        var p=properties.getWechat();
        if(!p.isEnabled()) { service=null; parser=null; return; }
        properties.notifyUrl(channel());
        var config=new RSAPublicKeyConfig.Builder().merchantId(p.getMerchantId())
                .merchantSerialNumber(p.getMerchantSerialNumber()).privateKeyFromPath(p.getPrivateKeyPath())
                .publicKeyFromPath(p.getPublicKeyPath()).publicKeyId(p.getPublicKeyId()).apiV3Key(p.getApiV3Key()).build();
        service=new NativePayService.Builder().config(config).build(); parser=new NotificationParser(config);
    }
    public String channel() { return "wechat"; }
    public boolean enabled() { return service!=null; }
    public String create(PaymentOrder order) {
        var p=properties.getWechat();
        var request=new PrepayRequest(); request.setAppid(p.getAppId()); request.setMchid(p.getMerchantId());
        request.setOutTradeNo(order.id()); request.setDescription("AI "+order.plan()+" 月度服务");
        request.setNotifyUrl(properties.notifyUrl(channel()));
        request.setTimeExpire(order.expiresAt().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime().toString());
        var amount=new Amount(); amount.setTotal(order.amountCents()); amount.setCurrency("CNY"); request.setAmount(amount);
        return service.prepay(request).getCodeUrl();
    }
    public PaymentResult query(PaymentOrder order) {
        var request=new QueryOrderByOutTradeNoRequest(); request.setMchid(properties.getWechat().getMerchantId()); request.setOutTradeNo(order.id());
        try { return result(service.queryOrderByOutTradeNo(request)); }
        catch(com.wechat.pay.java.core.exception.ServiceException e) {
            if("ORDER_NOT_EXIST".equals(e.getErrorCode()) || "ORDER_NOT_EXISTS".equals(e.getErrorCode()))
                return new PaymentResult(channel(),order.id(),null,order.amountCents(),"CNY","NOT_FOUND");
            throw e;
        }
    }
    public void close(PaymentOrder order) {
        var request=new CloseOrderRequest(); request.setMchid(properties.getWechat().getMerchantId()); request.setOutTradeNo(order.id());
        service.closeOrder(request);
    }
    public PaymentResult notification(String body,Map<String,String> headers,Map<String,String> params) {
        if(!enabled()) throw new IllegalStateException("支付未启用");
        String timestamp=headers.get("wechatpay-timestamp");
        if(timestamp==null || Math.abs(java.time.Instant.now().getEpochSecond()-Long.parseLong(timestamp))>300)
            throw new IllegalArgumentException("支付通知时间无效");
        var request=new RequestParam.Builder().serialNumber(headers.get("wechatpay-serial"))
                .nonce(headers.get("wechatpay-nonce")).signature(headers.get("wechatpay-signature"))
                .timestamp(timestamp).body(body).build();
        return result(parser.parse(request,Transaction.class));
    }
    private PaymentResult result(Transaction value) {
        var p=properties.getWechat();
        if(!p.getMerchantId().equals(value.getMchid()) || !p.getAppId().equals(value.getAppid()))
            throw new IllegalArgumentException("支付商户不匹配");
        String status=switch(value.getTradeState().name()) {
            case "SUCCESS" -> "PAID";
            case "CLOSED", "REVOKED", "PAYERROR" -> "CLOSED";
            default -> "PENDING";
        };
        return new PaymentResult(channel(),value.getOutTradeNo(),value.getTransactionId(),
                value.getAmount().getTotal(),value.getAmount().getCurrency(),status);
    }
}
