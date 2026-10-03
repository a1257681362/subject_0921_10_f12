package com.evops.gradereview.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 材料上传：附件类给 fileRef（建议同时给 contentHash 摘要留档）；文字材料给 content 正文。
 */
@Data
public class MaterialUploadRequest {

    @NotBlank(message = "材料类型不能为空（DOC 文档 / PHOTO 照片 / VIDEO 视频 / OTHER 其他）")
    private String materialType;

    @NotBlank(message = "材料标题不能为空")
    private String title;

    /** 附件存放引用（路径/对象存储键）。 */
    private String fileRef;

    /** 附件内容摘要（防替换抵赖）。 */
    private String contentHash;

    /** 文字内容（说明类材料）。 */
    private String content;

    /** 首次上传说明（可空，记入首版摘要）。 */
    private String summary;
}
