package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String email;
    private String phone;
    private Integer gender; // 1-男, 2-女
    private Integer age;
    private Double height; // cm
    private Double weight; // kg
    private String avatar;
    private Integer status; // 0-禁用, 1-启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}