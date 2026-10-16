CREATE TABLE ai_entitlement (
    user_id BIGINT NOT NULL PRIMARY KEY,
    plan_code VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'INACTIVE',
    expires_at DATETIME NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_entitlement_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) COMMENT='AI 付费使用资格；仅可信支付结算流程可开通，普通用户无写入接口';

CREATE TABLE ai_payment_order (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_code VARCHAR(16) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    request_id VARCHAR(36) NOT NULL,
    checkout_url VARCHAR(2048) NULL,
    transaction_id VARCHAR(128) NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    next_check_at DATETIME NOT NULL,
    UNIQUE KEY uk_ai_payment_request(user_id,request_id),
    UNIQUE KEY uk_ai_payment_transaction(channel,transaction_id),
    KEY idx_ai_payment_pending(user_id,status,created_at),
    KEY idx_ai_payment_reconcile(status,next_check_at),
    CONSTRAINT fk_ai_payment_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) COMMENT='AI 月度订单；金额单位分，所有时间为 UTC';
