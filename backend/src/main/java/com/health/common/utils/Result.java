package com.health.common.utils;

import com.health.common.enums.ResultCode;
import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<T>().setCode(ResultCode.SUCCESS.getCode())
                .setMessage(ResultCode.SUCCESS.getMessage());
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(ResultCode.SUCCESS.getCode())
                .setMessage(ResultCode.SUCCESS.getMessage())
                .setData(data);
    }

    public static <T> Result<T> failed() {
        return new Result<T>().setCode(ResultCode.FAILED.getCode())
                .setMessage(ResultCode.FAILED.getMessage());
    }

    public static <T> Result<T> failed(String message) {
        return new Result<T>().setCode(ResultCode.FAILED.getCode())
                .setMessage(message);
    }

    public static <T> Result<T> validateFailed() {
        return new Result<T>().setCode(ResultCode.VALIDATE_FAILED.getCode())
                .setMessage(ResultCode.VALIDATE_FAILED.getMessage());
    }

    public static <T> Result<T> tooManyRequests() {
        return new Result<T>().setCode(ResultCode.TOO_MANY_REQUESTS.getCode())
                .setMessage(ResultCode.TOO_MANY_REQUESTS.getMessage());
    }

    public static <T> Result<T> tooManyRequests(String message) {
        return new Result<T>().setCode(ResultCode.TOO_MANY_REQUESTS.getCode())
                .setMessage(message);
    }

    public Result<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public Result<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public Result<T> setData(T data) {
        this.data = data;
        return this;
    }
}