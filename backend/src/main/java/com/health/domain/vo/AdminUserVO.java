package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户列表VO（不包含敏感信息）
 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer gender;
    private Integer age;
    private Double height;
    private Double weight;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
