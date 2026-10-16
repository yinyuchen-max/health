package com.health.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component @ConfigurationProperties(prefix="ai.payment")
public class PaymentProperties {
    private boolean enabled=false;
    private int plusCents=990, proCents=1990, promaxCents=2990;
    private String publicBaseUrl="";
    private Wechat wechat=new Wechat();
    private Alipay alipay=new Alipay();
    @Getter @Setter public static class Wechat {
        private boolean enabled;
        private String appId="",merchantId="",merchantSerialNumber="",privateKeyPath="",publicKeyPath="",publicKeyId="",apiV3Key="";
        @Override public String toString() { return "Wechat[REDACTED]"; }
    }
    @Getter @Setter public static class Alipay {
        private boolean enabled;
        private String appId="",sellerId="",privateKeyPath="",publicKeyPath="";
        @Override public String toString() { return "Alipay[REDACTED]"; }
    }
    @Override public String toString() { return "PaymentProperties[REDACTED]"; }
    public String notifyUrl(String channel) {
        if(!publicBaseUrl.matches("https://[^?#]+") || publicBaseUrl.contains("@"))
            throw new IllegalStateException("支付回调地址必须为公网 HTTPS 地址");
        return publicBaseUrl.replaceAll("/+$","")+"/api/ai/payments/notify/"+channel;
    }
}
