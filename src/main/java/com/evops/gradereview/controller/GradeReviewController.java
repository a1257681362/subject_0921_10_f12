package com.evops.gradereview.controller;

import com.evops.common.ApiResponse;
import com.evops.gradereview.dto.ReviewConcludeRequest;
import com.evops.gradereview.dto.ReviewNoteRequest;
import com.evops.gradereview.dto.ReviewSubmitRequest;
import com.evops.gradereview.entity.ReviewNote;
import com.evops.gradereview.entity.ReviewNotice;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.entity.GradeReviewVersion;
import com.evops.gradereview.service.GradeReviewService;
import com.evops.gradereview.service.ReviewNoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 复核申请流转：提交受理（窗口只卡新受理）、审核岗写依据出结论、版本链、内部批注、通知回执。
 * 材料阅读走 /api/grade-review/requests/{id}/materials（ReviewMaterialController），与流转分开。
 */
@RestController
@RequestMapping("/api/grade-review/requests")
public class GradeReviewController {

    private final GradeReviewService reviewService;
    private final ReviewNoticeService noticeService;

    public GradeReviewController(GradeReviewService reviewService, ReviewNoticeService noticeService) {
        this.reviewService = reviewService;
        this.noticeService = noticeService;
    }

    /** 提交复核申请：仅窗口 OPEN 受理；未配置 / 未开始 / 已关闭分别返回可读原因。 */
    @PostMapping
    public ApiResponse<GradeReview> submit(@Valid @RequestBody ReviewSubmitRequest request) {
        return ApiResponse.ok(reviewService.submit(request));
    }

    /** 审核岗出结论 / 改结论：必须写明依据，追加版本并通知学员（回执挂回原申请）。 */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<GradeReview> conclude(@PathVariable Long id,
                                               @Valid @RequestBody ReviewConcludeRequest request) {
        return ApiResponse.ok(reviewService.conclude(id, request));
    }

    /** 内部批注：只追加；仅内部可见，学员视图与导出文件均不带出。 */
    @PostMapping("/{id}/notes")
    public ApiResponse<ReviewNote> addNote(@PathVariable Long id,
                                           @Valid @RequestBody ReviewNoteRequest request) {
        return ApiResponse.ok(reviewService.addNote(id, request));
    }

    @GetMapping("/{id}/notes")
    public ApiResponse<List<ReviewNote>> notes(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.listNotes(id));
    }

    /** 通知回执：挂回原申请的通知与已读状态。 */
    @GetMapping("/{id}/notices")
    public ApiResponse<List<ReviewNotice>> notices(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.listByReview(id));
    }

    /** 版本链：受理 / 结论逐版只追加。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<GradeReviewVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.listVersions(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<GradeReview> get(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.getReview(id));
    }

    @GetMapping
    public ApiResponse<List<GradeReview>> list(@RequestParam(required = false) Long batchId,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(reviewService.list(batchId, status));
    }
}
