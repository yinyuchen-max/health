package com.health.common.exception;

/** 乐观锁未匹配，调用方需要重新读取并确认修改内容。 */
public class RecordConflictException extends RuntimeException {
    public RecordConflictException(String message) {
        super(message);
    }
}
