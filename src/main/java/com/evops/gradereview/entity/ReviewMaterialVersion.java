package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核材料版本：首次上传 / 附件替换各追加一版，永不修改；
 * 替换必须留下替换摘要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review_material_version")
public class ReviewMaterialVersion extends BaseEntity {
    private Long materialId;
    private Integer versionNo;
    /** UPLOAD 首次上传 / REPLACE 附件替换。 */
    private String changeType;
    private String fileRef;
    private String contentHash;
    private String content;
    /** 替换摘要：REPLACE 版必填，说明本版相对上版换了什么。 */
    private String changeSummary;
    private String operator;
    private LocalDateTime changedAt;
}
