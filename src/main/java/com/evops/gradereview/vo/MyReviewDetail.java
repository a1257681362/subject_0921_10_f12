package com.evops.gradereview.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员本人申请详情：结论与依据照实回传；不含审核人、内部批注等内部信息。
 */
@Data
public class MyReviewDetail {
    private Long id;
    private String applyNo;
    private String maskedName;
    private String status;
    private String applyReason;
    private LocalDateTime appliedAt;
    /** 结论说明与依据（未结论为空）。 */
    private String conclusion;
    private String basis;
    private LocalDateTime concludedAt;
    /** 材料清单（按时间排列）。 */
    private List<MyMaterialItem> materials;
    /** 通知与回执。 */
    private List<MyNoticeItem> notices;
}
