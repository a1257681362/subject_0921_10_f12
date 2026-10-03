package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核通知回执：结论通知挂回原申请（review_id），学员已读即回执。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review_notice")
public class ReviewNotice extends BaseEntity {
    private Long reviewId;
    private String applyNo;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** REVIEW_CONCLUSION 复核结论通知。 */
    private String notifyType;
    private String title;
    private String content;
    /** NEW 未读 / READ 已读（已读钟点即回执）。 */
    private String status;
    private LocalDateTime notifiedAt;
    private LocalDateTime readAt;
    private String operator;
}
