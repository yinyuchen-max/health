package com.health.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reminder_notification")
public class ReminderNotification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long preferenceId;
    private String type;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualTime;
    private Boolean completed;
    private Boolean readStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
