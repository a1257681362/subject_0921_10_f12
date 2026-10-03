package com.evops.gradereview.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 内部批注：只追加；仅内部可见，学员视图与导出文件均不带出。
 */
@Data
public class ReviewNoteRequest {

    @NotBlank(message = "批注内容不能为空")
    private String note;
}
