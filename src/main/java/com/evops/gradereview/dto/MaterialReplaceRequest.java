package com.evops.gradereview.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 附件版本替换：替换必须留下摘要，说明本版相对上版换了什么。
 */
@Data
public class MaterialReplaceRequest {

    /** 新附件存放引用（路径/对象存储键）。 */
    private String fileRef;

    /** 新附件内容摘要。 */
    private String contentHash;

    /** 新文字内容（说明类材料）。 */
    private String content;

    /** 替换摘要：必填，随版本链留痕。 */
    @NotBlank(message = "附件版本替换必须留下替换摘要")
    private String changeSummary;
}
