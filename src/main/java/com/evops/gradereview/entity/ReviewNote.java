package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核内部批注：只追加；仅内部可见——学员视图与导出文件均不带出。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review_note")
public class ReviewNote extends BaseEntity {
    private Long reviewId;
    private String note;
    private String operator;
    private LocalDateTime notedAt;
}
