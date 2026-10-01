package com.health.common.exception;

import com.health.common.enums.ResultCode;
import lombok.Getter;

/**
 * 业务异常（阿里手册：禁止直接抛出 RuntimeException，应使用有业务含义的自定义异常）
 *
 * 用于 Service 层业务校验失败时抛出，由 GlobalExceptionHandler 统一捕获并返回友好提示。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
