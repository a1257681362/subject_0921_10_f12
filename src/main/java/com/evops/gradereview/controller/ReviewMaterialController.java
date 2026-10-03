package com.evops.gradereview.controller;

import com.evops.common.ApiResponse;
import com.evops.gradereview.dto.MaterialReplaceRequest;
import com.evops.gradereview.dto.MaterialUploadRequest;
import com.evops.gradereview.entity.ReviewMaterial;
import com.evops.gradereview.entity.ReviewMaterialVersion;
import com.evops.gradereview.service.ReviewMaterialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 复核材料阅读：与申请流转分开处理——材料按时间排列、附件版本替换留摘要、版本链只追加。
 */
@RestController
@RequestMapping("/api/grade-review")
public class ReviewMaterialController {

    private final ReviewMaterialService materialService;

    public ReviewMaterialController(ReviewMaterialService materialService) {
        this.materialService = materialService;
    }

    /** 在办申请补传材料（已结论申请的材料只读）。 */
    @PostMapping("/requests/{id}/materials")
    public ApiResponse<ReviewMaterial> upload(@PathVariable Long id,
                                              @Valid @RequestBody MaterialUploadRequest request) {
        return ApiResponse.ok(materialService.upload(id, request, null));
    }

    /** 材料清单：进入 evops-base 后按上传时间排列。 */
    @GetMapping("/requests/{id}/materials")
    public ApiResponse<List<ReviewMaterial>> listByReview(@PathVariable Long id) {
        return ApiResponse.ok(materialService.listByReview(id));
    }

    /** 材料详情（当前版本）。 */
    @GetMapping("/materials/{materialId}")
    public ApiResponse<ReviewMaterial> get(@PathVariable Long materialId) {
        return ApiResponse.ok(materialService.getMaterial(materialId));
    }

    /** 材料版本链：上传 / 替换逐版只追加，替换版带替换摘要。 */
    @GetMapping("/materials/{materialId}/versions")
    public ApiResponse<List<ReviewMaterialVersion>> versions(@PathVariable Long materialId) {
        return ApiResponse.ok(materialService.listVersions(materialId));
    }

    /** 附件版本替换：必须留下替换摘要。 */
    @PostMapping("/materials/{materialId}/replace")
    public ApiResponse<ReviewMaterial> replace(@PathVariable Long materialId,
                                               @Valid @RequestBody MaterialReplaceRequest request) {
        return ApiResponse.ok(materialService.replace(materialId, request));
    }
}
