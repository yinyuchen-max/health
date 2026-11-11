package com.health.domain.dto;

import lombok.Data;

@Data
public class ChatResponseDTO {
    private String reply;
    /** 本次回复参考了哪些个人记录。 */
    private String contextNote;
}
