package com.health.payment;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.internal.util.AlipaySignature;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class AlipayPaymentGateway implements PaymentGateway {
    private final PaymentProperties properties;
    private final AlipayClient client;
    private final String publicKey;
    public AlipayPaymentGateway(PaymentProperties properties) {
        this.properties=properties; var p=properties.getAlipay();
        if(!p.isEnabled()) { client=null; publicKey=""; return; }
        properties.notifyUrl(channel());
        if(p.getAppId().isBlank() || p.getSellerId().isBlank()) throw new IllegalStateException("支付宝商户参数缺失");
        try {
            publicKey=readKey(p.getPublicKeyPath());
            client=new DefaultAlipayClient("https://openapi.alipay.com/gateway.do",p.getAppId(),
                    readKey(p.getPrivateKeyPath()),"json","UTF-8",publicKey,"RSA2");
        } catch(Exception e) { throw new IllegalStateException("读取支付宝商户密钥失败"); }
    }
    private static String readKey(String path) throws Exception {
        return Files.readString(Path.of(path)).replaceAll("-----[A-Z ]+-----","").replaceAll("\\s","");
    }
    public String channel() { return "alipay"; }
    public boolean enabled() { return client!=null; }
    public String create(PaymentOrder order) {
        try {
            var model=new AlipayTradePrecreateModel(); model.setOutTradeNo(order.id());
            model.setSubject("AI "+order.plan()+" 月度服务");
            model.setTotalAmount(BigDecimal.valueOf(order.amountCents(),2).toPlainString());
            model.setSellerId(properties.getAlipay().getSellerId());
            model.setTimeExpire(order.expiresAt().atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            var request=new AlipayTradePrecreateRequest(); request.setBizModel(model); request.setNotifyUrl(properties.notifyUrl(channel()));
            var response=client.execute(request);
            if(!response.isSuccess() || !order.id().equals(response.getOutTradeNo())) throw new IllegalStateException();
            return response.getQrCode();
        } catch(Exception e) { throw new IllegalStateException("支付宝下单暂不可用"); }
    }
    public PaymentResult query(PaymentOrder order) {
        try {
            var model=new AlipayTradeQueryModel(); model.setOutTradeNo(order.id());
            var request=new AlipayTradeQueryRequest(); request.setBizModel(model);
            var response=client.execute(request);
            if(!response.isSuccess()) {
                if("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))
                    return new PaymentResult(channel(),order.id(),null,order.amountCents(),"CNY","NOT_FOUND");
                throw new IllegalStateException();
            }
            return result(response.getOutTradeNo(),response.getTradeNo(),response.getTotalAmount(),response.getTradeStatus());
        } catch(Exception e) { throw new IllegalStateException("支付宝查询暂不可用"); }
    }
    public void close(PaymentOrder order) {
        try {
            var model=new AlipayTradeCloseModel(); model.setOutTradeNo(order.id());
            var request=new AlipayTradeCloseRequest(); request.setBizModel(model);
            if(!client.execute(request).isSuccess()) throw new IllegalStateException();
        } catch(Exception e) { throw new IllegalStateException("支付宝关闭订单暂不可用"); }
    }
    public PaymentResult notification(String body,Map<String,String> headers,Map<String,String> params) {
        try {
            if(!enabled() || !"RSA2".equals(params.get("sign_type"))
                    || !AlipaySignature.rsaCheckV1(new java.util.HashMap<>(params),publicKey,"UTF-8","RSA2")
                    || !properties.getAlipay().getAppId().equals(params.get("app_id"))
                    || !properties.getAlipay().getSellerId().equals(params.get("seller_id"))) throw new IllegalArgumentException();
            return result(params.get("out_trade_no"),params.get("trade_no"),params.get("total_amount"),params.get("trade_status"));
        } catch(Exception e) { throw new IllegalArgumentException("支付宝通知校验失败"); }
    }
    private PaymentResult result(String id,String transaction,String amount,String state) {
        String status=switch(state) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> "PAID";
            case "TRADE_CLOSED" -> "CLOSED";
            default -> "PENDING";
        };
        return new PaymentResult(channel(),id,transaction,new BigDecimal(amount).movePointRight(2).intValueExact(),"CNY",status);
    }
}
