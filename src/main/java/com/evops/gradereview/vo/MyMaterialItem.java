package com.evops.gradereview.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员本人材料阅读视图：当前版本内容；不含上传人等内部信息。
 */
@Data
public class MyMaterialItem {
    private Long id;
    private String materialType;
    private String title;
    private String fileRef;
    private String contentHash;
    private String content;
    private Integer currentVersion;
    private LocalDateTime uploadedAt;
}
