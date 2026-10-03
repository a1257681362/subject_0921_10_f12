package com.evops.gradereview.controller;

import com.evops.appoint.common.SelfHolder;
import com.evops.common.ApiResponse;
import com.evops.gradereview.entity.ReviewNotice;
import com.evops.gradereview.service.GradeReviewService;
import com.evops.gradereview.service.ReviewMaterialService;
import com.evops.gradereview.service.ReviewNoticeService;
import com.evops.gradereview.vo.MyMaterialItem;
import com.evops.gradereview.vo.MyNoticeItem;
import com.evops.gradereview.vo.MyReviewDetail;
import com.evops.gradereview.vo.MyReviewItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员本人视图（X-Self-Code=报名号）：受权限限制只能读取本人资料——
 * 申请、材料、通知只回本人那条，脱敏且不含内部批注；查别人的统一“查无此申请”。
 */
@RestController
@RequestMapping("/api/grade-review/my")
public class MyGradeReviewController {

    private final GradeReviewService reviewService;
    private final ReviewMaterialService materialService;
    private final ReviewNoticeService noticeService;

    public MyGradeReviewController(GradeReviewService reviewService,
                                   ReviewMaterialService materialService,
                                   ReviewNoticeService noticeService) {
        this.reviewService = reviewService;
        this.materialService = materialService;
        this.noticeService = noticeService;
    }

    @GetMapping("/requests")
    public ApiResponse<List<MyReviewItem>> myRequests() {
        return ApiResponse.ok(reviewService.myList(SelfHolder.currentSelfCode()));
    }

    @GetMapping("/requests/{id}")
    public ApiResponse<MyReviewDetail> myDetail(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.myDetail(SelfHolder.currentSelfCode(), id));
    }

    /** 本人材料阅读（按时间排列）。 */
    @GetMapping("/requests/{id}/materials")
    public ApiResponse<List<MyMaterialItem>> myMaterials(@PathVariable Long id) {
        return ApiResponse.ok(materialService.myMaterials(SelfHolder.currentSelfCode(), id));
    }

    @GetMapping("/notices")
    public ApiResponse<List<MyNoticeItem>> myNotices(@RequestParam(required = false) Boolean unreadOnly) {
        String selfCode = reviewService.requireSelfCode(SelfHolder.currentSelfCode());
        return ApiResponse.ok(noticeService.myNotices(selfCode, Boolean.TRUE.equals(unreadOnly)));
    }

    /** 通知已读：已读钟点即回执，挂回原申请可查。 */
    @PostMapping("/notices/{noticeId}/read")
    public ApiResponse<ReviewNotice> readNotice(@PathVariable Long noticeId) {
        String selfCode = reviewService.requireSelfCode(SelfHolder.currentSelfCode());
        return ApiResponse.ok(noticeService.markRead(selfCode, noticeId));
    }
}
