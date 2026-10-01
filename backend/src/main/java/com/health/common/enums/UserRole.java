package com.health.common.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 * 阿里手册：不允许任何魔法值直接出现在代码中
 */
@Getter
public enum UserRole {

    USER("user", "普通用户"),
    ADMIN("admin", "管理员"),
    DOCTOR("doctor", "医生");

    private final String code;
    private final String desc;

    UserRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
