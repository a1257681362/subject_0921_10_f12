package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核申请主表（模块）：当前状态取最新版本；版本链见 t_grade_review_version。
 * 窗口只卡新受理，历史内容不受窗口关闭影响。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review")
public class GradeReview extends BaseEntity {
    /** 申请号 FH + 批次编码 + 4 位流水。 */
    private String applyNo;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private String applyReason;
    private String applicant;
    private LocalDateTime appliedAt;
    /** SUBMITTED 待结论 / APPROVED 通过 / REJECTED 驳回。 */
    private String status;
    private Integer currentVersion;
    /** 最新结论说明与依据（每次改结论整体覆盖，历史见版本链）。 */
    private String conclusion;
    private String basis;
    private String reviewer;
    private LocalDateTime concludedAt;
    private String operator;
    private String remark;
}
