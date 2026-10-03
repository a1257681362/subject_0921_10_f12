package com.evops.gradereview.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员本人申请列表项：脱敏（掩码姓名），不含审核人、内部批注等内部信息。
 */
@Data
public class MyReviewItem {
    private Long id;
    private String applyNo;
    private String maskedName;
    /** SUBMITTED 待结论 / APPROVED 通过 / REJECTED 驳回。 */
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime concludedAt;
}
