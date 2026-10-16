package com.health.payment;

import com.health.ai.AiAccessService;
import com.health.ai.AiConfigService;
import com.health.common.exception.BusinessException;
import com.health.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentMapper mapper;
    private final TransactionTemplate transactions;
    private final PaymentProperties properties;
    private final AiAccessService access;
    private final AiConfigService config;
    private final java.util.concurrent.ScheduledExecutorService reconciliation=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"ai-payment-reconciliation"); thread.setDaemon(true); return thread;
    });
    private final Map<String,PaymentGateway> gateways=new HashMap<>();
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PaymentService.class);
    public PaymentService(PaymentMapper mapper,TransactionTemplate transactions,PaymentProperties properties,
                          AiAccessService access,AiConfigService config,List<PaymentGateway> gateways) {
        this.mapper=mapper; this.transactions=transactions; this.properties=properties; this.access=access; this.config=config;
        gateways.forEach(g -> this.gateways.put(g.channel(),g));
    }
    public record Plan(String code,String name,int amountCents) {}
    public record Catalog(List<Plan> plans,List<String> channels,boolean available,String currentPlan,String message) {}
    public record OrderView(String id,String plan,String channel,int amountCents,String status,String checkoutUrl,
                            LocalDateTime expiresAt,LocalDateTime paidAt) {}
    public List<Plan> plans() {
        return List.of(new Plan("plus","Plus",properties.getPlusCents()),new Plan("pro","Pro",properties.getProCents()),
                new Plan("promax","Pro Max",properties.getPromaxCents()));
    }
    public Catalog catalog(Long userId) {
        access.status(userId);
        List<String> channels=gateways.values().stream().filter(PaymentGateway::enabled).map(PaymentGateway::channel).sorted().toList();
        boolean available=properties.isEnabled() && config.settings(userId).configured() && !channels.isEmpty();
        var membership=mapper.membership(userId);
        return new Catalog(plans(),channels,available,membership==null ? null : membership.plan(),
                available ? "三档当前功能相同；每次支付开通一个自然月，不自动扣费。有效期内可续费同一套餐，到期后可换档。"
                        : "支付渠道暂未开放，请联系管理员；管理员无需购买。");
    }
    public OrderView create(Long userId,String planCode,String channel,String requestId) {
        if(requestId==null || !requestId.matches("[a-fA-F0-9-]{36}")) throw new BusinessException("下单请求编号无效");
        if(access.status(userId).admin()) throw new BusinessException("管理员可免费使用 AI，无需购买");
        if(!catalog(userId).available()) throw new BusinessException("支付暂未开放");
        var plan=plans().stream().filter(p->p.code().equals(planCode)).findFirst().orElseThrow(()->new BusinessException("套餐无效"));
        if(plan.amountCents()<1) throw new BusinessException("套餐价格未配置");
        PaymentGateway gateway=gateway(channel);
        for(int attempt=0;attempt<2;attempt++) {
            PaymentOrder order=transactions.execute(tx -> {
                lockUser(userId);
                var previous=mapper.byRequest(userId,requestId);
                if(previous!=null) {
                    if(!previous.plan().equals(planCode) || !previous.channel().equals(channel)) throw new BusinessException("同一下单编号不能更换套餐或渠道");
                    return previous;
                }
                LocalDateTime now=mapper.now();
                var membership=mapper.membership(userId);
                if(membership!=null && "ACTIVE".equals(membership.status()) && membership.expiresAt().isAfter(now)
                        && !membership.plan().equals(planCode)) throw new BusinessException("有效期内仅支持续费当前套餐，到期后可换档");
                var pending=mapper.pending(userId);
                if(pending!=null) return pending;
                var created=new PaymentOrder(UUID.randomUUID().toString().replace("-",""),userId,planCode,channel,
                        plan.amountCents(),"CNY","PENDING",requestId,null,null,now,now.plusMinutes(15),null);
                mapper.insert(created); return created;
            });
            if(!"PENDING".equals(order.status())) return view(order);
            if(!order.expiresAt().isAfter(mapper.now())) {
                try { refreshOrder(order,true); }
                catch(Exception e) { throw new BusinessException("上一笔订单状态暂未确认，请稍后重试"); }
                if(order.requestId().equals(requestId)) return view(mapper.find(order.id()));
                continue;
            }
            if(!order.channel().equals(channel) || !order.plan().equals(planCode))
                throw new BusinessException("已有待支付订单，请先完成或等待订单过期后再更换套餐/渠道");
            if(order.checkoutUrl()!=null) return view(order);
            try {
                String url=gateway.create(order);
                boolean valid="wechat".equals(channel) ? url!=null && url.startsWith("weixin://wxpay/")
                        : url!=null && url.startsWith("https://qr.alipay.com/");
                if(!valid || url.length()>2048) throw new IllegalStateException();
                mapper.checkout(order.id(),url);
                return view(mapper.find(order.id()));
            } catch(Exception e) {
                // Keep the same order number after uncertain network outcomes; never grant locally.
                throw new BusinessException("下单结果暂未确认，请稍后刷新订单或重试，不要重复付款");
            }
        }
        throw new BusinessException("正在核对上一笔订单，请稍后重试");
    }
    public List<OrderView> list(Long userId) { return mapper.list(userId).stream().map(this::view).toList(); }
    public OrderView resumeCheckout(Long userId,String id) {
        var order=mapper.find(id);
        if(order==null || !order.userId().equals(userId)) throw new ForbiddenException("订单不存在或无权访问");
        // Query first: a lost prepay response must not turn an already paid order into another purchase.
        var current=refresh(userId,id);
        if(!"PENDING".equals(current.status()) || current.checkoutUrl()!=null) return current;
        return create(userId,order.plan(),order.channel(),order.requestId());
    }
    public OrderView refresh(Long userId,String id) {
        PaymentOrder order=mapper.find(id);
        if(order==null || !order.userId().equals(userId)) throw new ForbiddenException("订单不存在或无权访问");
        try { refreshOrder(order,true); }
        catch(BusinessException e) { throw e; }
        catch(Exception e) { throw new BusinessException("支付状态暂时无法确认，请稍后重试"); }
        return view(mapper.find(id));
    }
    public void notification(String channel,String body,Map<String,String> headers,Map<String,String> params) {
        PaymentGateway.PaymentResult result=gateway(channel).notification(body,headers,params);
        if(!result.paid()) return;
        settle(result);
    }
    void settle(PaymentGateway.PaymentResult result) {
        if(!result.paid() || result.transactionId()==null || result.transactionId().isBlank() || result.transactionId().length()>128)
            throw new IllegalArgumentException("支付结果不完整");
        var existing=mapper.find(result.orderId());
        if(existing==null) throw new IllegalArgumentException("未知支付订单");
        transactions.executeWithoutResult(tx -> {
            lockUser(existing.userId());
            var order=mapper.lock(existing.id());
            if(!order.channel().equals(result.channel()) || order.amountCents()!=result.amountCents()
                    || !order.currency().equals(result.currency())) throw new IllegalArgumentException("订单支付信息不匹配");
            if("PAID".equals(order.status())) {
                if(!result.transactionId().equals(order.transactionId())) throw new IllegalArgumentException("支付流水不匹配");
                return;
            }
            if(!"PENDING".equals(order.status())) throw new IllegalArgumentException("订单状态冲突");
            LocalDateTime now=mapper.now();
            var membership=mapper.membership(order.userId());
            LocalDateTime start=membership!=null && "ACTIVE".equals(membership.status()) && membership.expiresAt().isAfter(now)
                    ? membership.expiresAt() : now;
            if(start.isAfter(now) && !membership.plan().equals(order.plan())) throw new IllegalArgumentException("套餐状态冲突，需核对订单");
            if(mapper.paid(order.id(),result.transactionId(),now)!=1) throw new IllegalStateException("支付状态已变化");
            mapper.grant(order.userId(),order.plan(),start.plusMonths(1));
        });
    }
    private void refreshOrder(PaymentOrder order,boolean closeExpired) {
        if(!"PENDING".equals(order.status())) return;
        PaymentGateway gateway=gateway(order.channel());
        var result=gateway.query(order);
        if(!order.id().equals(result.orderId()) || !order.channel().equals(result.channel())) throw new IllegalArgumentException("支付查询订单不匹配");
        if(result.paid()) { settle(result); return; }
        if("CLOSED".equals(result.status())) { mapper.closed(order.id()); return; }
        if(closeExpired && !order.expiresAt().isAfter(mapper.now())) {
            if(!"NOT_FOUND".equals(result.status())) {
                try { gateway.close(order); }
                catch(Exception e) {
                    var retry=gateway.query(order);
                    if(retry.paid()) { settle(retry); return; }
                    throw e;
                }
            }
            mapper.closed(order.id());
        }
    }
    @jakarta.annotation.PostConstruct public void startReconciliation() {
        if(gateways.values().stream().anyMatch(PaymentGateway::enabled)) reconciliation.scheduleWithFixedDelay(() -> {
            try { reconcile(); } catch(Exception e) { log.warn("支付对账暂不可用，将自动重试"); }
        },60,60,java.util.concurrent.TimeUnit.SECONDS);
    }
    @jakarta.annotation.PreDestroy public void close() { reconciliation.shutdownNow(); }
    public void reconcile() {
        if(gateways.values().stream().noneMatch(PaymentGateway::enabled)) return;
        for(var order:mapper.due()) {
            mapper.postpone(order.id(),mapper.now().plusMinutes(5));
            try { refreshOrder(order,true); }
            catch(Exception e) { log.warn("支付订单待重查，orderId={}",order.id()); }
        }
    }
    private PaymentGateway gateway(String channel) {
        var gateway=gateways.get(channel);
        if(gateway==null || !gateway.enabled()) throw new BusinessException("该支付渠道未开通");
        return gateway;
    }
    private void lockUser(Long id) { if(mapper.lockUser(id)==null) throw new ForbiddenException("用户不存在"); }
    private OrderView view(PaymentOrder order) {
        return new OrderView(order.id(),order.plan(),order.channel(),order.amountCents(),order.status(),
                "PENDING".equals(order.status()) ? order.checkoutUrl() : null,order.expiresAt(),order.paidAt());
    }
}
