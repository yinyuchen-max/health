package com.health.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewaySignatureTest {
    @TempDir Path directory;
    KeyPair pair;
    PaymentProperties properties() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        Path privateFile=directory.resolve("private.pem"),publicFile=directory.resolve("public.pem");
        Files.writeString(privateFile,pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
        Files.writeString(publicFile,pem("PUBLIC KEY",pair.getPublic().getEncoded()));
        var p=new PaymentProperties();p.setEnabled(true);p.setPublicBaseUrl("https://merchant.example");
        var wx=p.getWechat();wx.setEnabled(true);wx.setAppId("app-test");wx.setMerchantId("merchant-test");wx.setMerchantSerialNumber("ABCD1234");
        wx.setPrivateKeyPath(privateFile.toString());wx.setPublicKeyPath(publicFile.toString());wx.setPublicKeyId("PUB_KEY_ID_TEST");wx.setApiV3Key("0123456789abcdef0123456789abcdef");
        var ali=p.getAlipay();ali.setEnabled(true);ali.setAppId("app-test");ali.setSellerId("seller-test");ali.setPrivateKeyPath(privateFile.toString());ali.setPublicKeyPath(publicFile.toString());
        return p;
    }
    String pem(String type,byte[] key) { return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(key)+"\n-----END "+type+"-----\n"; }
    String sign(String text) throws Exception {
        var signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    String body(String merchant) throws Exception {
        var json=new ObjectMapper();
        String plain=json.writeValueAsString(Map.of("appid","app-test","mchid",merchant,"out_trade_no","order-test",
                "transaction_id","transaction-test","trade_state","SUCCESS","amount",Map.of("total",990,"currency","CNY")));
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),"AES"),
                new GCMParameterSpec(128,"123456789012".getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
        return json.writeValueAsString(Map.of("id","notify-test","event_type","TRANSACTION.SUCCESS","resource_type","encrypt-resource",
                "resource",Map.of("algorithm","AEAD_AES_256_GCM","nonce","123456789012","associated_data","transaction",
                        "ciphertext",Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))))));
    }
    Map<String,String> headers(String body,long epoch) throws Exception {
        String timestamp=Long.toString(epoch),nonce="notification-nonce";
        return Map.of("wechatpay-serial","PUB_KEY_ID_TEST","wechatpay-timestamp",timestamp,"wechatpay-nonce",nonce,
                "wechatpay-signature",sign(timestamp+"\n"+nonce+"\n"+body+"\n"));
    }
    @Test void wechatRequiresAuthenticFreshSignatureDecryptionAndMatchingMerchant() throws Exception {
        var gateway=new WechatPaymentGateway(properties());String body=body("merchant-test");var headers=headers(body,Instant.now().getEpochSecond());
        assertTrue(gateway.notification(body,headers,Map.of()).paid());
        assertThrows(RuntimeException.class,()->gateway.notification(body+" ",headers,Map.of()));
        var stale=headers(body,Instant.now().minusSeconds(600).getEpochSecond());
        assertThrows(RuntimeException.class,()->gateway.notification(body,stale,Map.of()));
        String other=body("wrong-merchant");var otherHeaders=headers(other,Instant.now().getEpochSecond());
        assertThrows(RuntimeException.class,()->gateway.notification(other,otherHeaders,Map.of()));
    }
    Map<String,String> alipayParams(String app) throws Exception {
        Map<String,String> params=new TreeMap<>(Map.of("app_id",app,"seller_id","seller-test","out_trade_no","order-test",
                "trade_no","transaction-test","trade_status","TRADE_SUCCESS","total_amount","9.90"));
        String content=params.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("&"));
        params.put("sign",sign(content));params.put("sign_type","RSA2");return params;
    }
    @Test void alipayRequiresSignatureAndMatchingAppAndSeller() throws Exception {
        var gateway=new AlipayPaymentGateway(properties());var params=alipayParams("app-test");
        assertEquals(990,gateway.notification("",Map.of(),params).amountCents());
        // The SDK may remove sign/sign_type during validation, so construct fresh notifications for every attempt.
        var tampered=alipayParams("app-test");tampered.put("total_amount","0.01");
        assertThrows(IllegalArgumentException.class,()->gateway.notification("",Map.of(),tampered));
        var wrongApp=alipayParams("other-app");
        assertThrows(IllegalArgumentException.class,()->gateway.notification("",Map.of(),wrongApp));
        assertThrows(IllegalArgumentException.class,()->gateway.notification("",Map.of(),Map.of("trade_status","TRADE_SUCCESS")));
    }
}
