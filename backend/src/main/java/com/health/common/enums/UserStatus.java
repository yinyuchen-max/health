package com.health.common.enums;

import lombok.Getter;

/**
 * 用户账号状态枚举
 * 阿里手册：不允许任何魔法值直接出现在代码中
 */
@Getter
public enum UserStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
