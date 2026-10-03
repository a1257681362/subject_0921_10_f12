package com.evops.gradereview.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员本人通知视图：已读钟点即回执。
 */
@Data
public class MyNoticeItem {
    private Long id;
    private String notifyType;
    private String title;
    private String content;
    /** NEW 未读 / READ 已读。 */
    private String status;
    private LocalDateTime notifiedAt;
    private LocalDateTime readAt;
}
