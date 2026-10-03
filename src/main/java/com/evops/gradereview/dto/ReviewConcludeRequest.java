package com.evops.gradereview.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 复核结论：审核岗修改结论状态必须写明依据，依据随版本链留痕并通知学员。
 */
@Data
public class ReviewConcludeRequest {

    @NotBlank(message = "结论状态不能为空（APPROVED 通过 / REJECTED 驳回）")
    private String conclusionStatus;

    /** 结论依据：必填，写明依据才允许修改结论状态。 */
    @NotBlank(message = "修改结论状态必须写明依据")
    private String basis;

    /** 结论说明（可空，缺省按结论状态给标准话术）。 */
    private String conclusion;
}
