package com.health.common.config;

import com.health.common.exception.ForbiddenException;
import com.health.common.exception.RateLimitException;
import com.health.common.exception.UnauthorizedException;
import com.health.common.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理限流异常
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitException.class)
    public Result<?> handleRateLimitException(RateLimitException e) {
        return Result.tooManyRequests(e.getMessage());
    }

    /**
     * 处理未认证异常
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedException.class)
    public Result<?> handleUnauthorizedException(UnauthorizedException e) {
        Result<?> result = Result.failed(e.getMessage());
        result.setCode(401);
        return result;
    }

    /**
     * 处理无权限异常
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public Result<?> handleForbiddenException(ForbiddenException e) {
        Result<?> result = Result.failed(e.getMessage());
        result.setCode(403);
        return result;
    }

    /**
     * 处理参数校验异常
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.validateFailed().setMessage(message);
    }

    /**
     * 处理业务运行时异常（只返回业务消息，不暴露堆栈）
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        // 对于业务层抛出的 RuntimeException，消息通常是业务提示（如"用户不存在"）
        // 保留业务消息，但不暴露底层异常细节
        return Result.failed(e.getMessage());
    }

    /**
     * 处理所有未知异常（不暴露内部错误信息）
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("未知系统异常: {}", e.getMessage(), e);
        // 不向客户端暴露任何内部异常详情
        return Result.failed("系统繁忙，请稍后重试");
    }
}
