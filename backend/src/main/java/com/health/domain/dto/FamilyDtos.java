package com.health.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class FamilyDtos {
    private FamilyDtos() {}

    public record Invite(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的11位手机号") String phone,
            @NotBlank @Size(max = 20) String relationship) {}
    public record Decision(@NotNull Boolean accept) {}
    public record SendReminder(
            @NotBlank @Pattern(regexp = "bloodPressure|bloodSugar|weight|exercise", message = "不支持的提醒类型") String type) {}
    public record Binding(Long id, Long memberId, String memberName, String maskedPhone,
                          String relationship, String status, boolean incoming, LocalDateTime createdAt) {}
    public record Reminder(Long id, String senderName, String type, boolean readStatus,
                           boolean completed, LocalDateTime createdAt) {}
}
