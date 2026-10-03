package com.evops.gradereview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核材料主表（模块）：进入 evops-base 后按上传时间排列；
 * 当前附件取 current_version 指向的版本，版本链见 t_grade_review_material_version。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_review_material")
public class ReviewMaterial extends BaseEntity {
    private Long reviewId;
    private String applyNo;
    /** DOC / PHOTO / VIDEO / OTHER。 */
    private String materialType;
    private String title;
    /** 当前版本快照（冗余自当前版本，便于列表直读）。 */
    private String fileRef;
    private String contentHash;
    private String content;
    private Integer currentVersion;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private String remark;
}
