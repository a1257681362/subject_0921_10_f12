package com.evops.gradereview.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 提交复核申请：按批次 + 报名号定位名册；仅窗口 OPEN 受理（窗口只卡新受理）。
 * 可同时附初始材料，材料进入 evops-base 后按时间排列。
 */
@Data
public class ReviewSubmitRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    /** 申请理由 / 疑问说明。 */
    private String applyReason;

    /** 申请人；缺省取 X-Operator。 */
    private String applicant;

    /** 初始材料（可空，受理后也可补传）。 */
    @Valid
    private List<MaterialUploadRequest> materials;

    private String remark;
}
