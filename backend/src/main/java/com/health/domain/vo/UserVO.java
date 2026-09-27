package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
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

}