package com.health.common.enums;

import lombok.Getter;

/**
 * 医生审核状态枚举
 * 阿里手册：不允许任何魔法值直接出现在代码中
 */
@Getter
public enum DoctorStatus {

    PENDING("pending", "待审核"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已拒绝");

    private final String code;
    private final String desc;

    DoctorStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
