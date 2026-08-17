package com.health.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话列表项（医生端显示所有对话过的用户，用户端显示所有对话过的医生）
 */
@Data
public class ConversationVO {
    private Long doctorId;
    private Long userId;
    private String doctorName;    // 医生真实姓名
    private String userName;      // 用户username
    private String lastMessage;   // 最后一条消息
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;  // 未读消息数
}
