package com.health.domain.vo;

import lombok.Data;

@Data
public class HistoryRecordVO {
    private Long id;
    private Long userId;
    private String type;
    private Long sourceRecordId;
    private String typeName;
    private String title;
    private String content;
    private String date; // YYYY-MM-DD format
    private String createTime; // YYYY-MM-DD HH:mm:ss format
}
