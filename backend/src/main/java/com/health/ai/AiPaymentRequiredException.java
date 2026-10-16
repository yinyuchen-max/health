package com.health.ai;

public class AiPaymentRequiredException extends RuntimeException {
    public AiPaymentRequiredException() { super("请先开通 AI 服务后再使用"); }
}
