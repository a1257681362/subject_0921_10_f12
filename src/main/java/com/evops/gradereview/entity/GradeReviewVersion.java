package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核申请版本：受理 / 结论各追加一版，永不修改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review_version")
public class GradeReviewVersion extends BaseEntity {
    private Long reviewId;
    private Integer versionNo;
    /** 本版状态：SUBMITTED / APPROVED / REJECTED。 */
    private String status;
    /** CREATE 受理 / CONCLUDE 结论（含改结论）。 */
    private String changeType;
    /** 本版结论说明与依据（CONCLUDE 版依据必填）。 */
    private String conclusion;
    private String basis;
    private String operator;
    private LocalDateTime changedAt;
}
