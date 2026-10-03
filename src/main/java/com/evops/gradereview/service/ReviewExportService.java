package com.evops.gradereview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import com.evops.gradereview.common.GradeReviewConstants;
import com.evops.gradereview.entity.ReviewMaterial;
import com.evops.gradereview.entity.ReviewNotice;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.mapper.ReviewMaterialMapper;
import com.evops.gradereview.mapper.ReviewNoticeMapper;
import com.evops.gradereview.mapper.GradeReviewMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复核申请导出：批次内申请台账 CSV（带 BOM，Excel 直接打开中文不乱码）。
 * 导出文件不得带出内部批注——批注只留在库内，任何导出都不含。
 */
@Service
public class ReviewExportService {

    private final GradeReviewMapper reviewMapper;
    private final ReviewMaterialMapper materialMapper;
    private final ReviewNoticeMapper noticeMapper;
    private final ExamBatchService batchService;

    public ReviewExportService(GradeReviewMapper reviewMapper,
                               ReviewMaterialMapper materialMapper,
                               ReviewNoticeMapper noticeMapper,
                               ExamBatchService batchService) {
        this.reviewMapper = reviewMapper;
        this.materialMapper = materialMapper;
        this.noticeMapper = noticeMapper;
        this.batchService = batchService;
    }

    /** 生成带 BOM 的 UTF-8 CSV：申请台账（状态 / 结论 / 依据 / 材料数 / 通知回执），不含内部批注。 */
    public byte[] buildCsv(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        List<GradeReview> reviews = reviewMapper.selectList(new QueryWrapper<GradeReview>()
                .eq("batch_id", batchId)
                .orderByAsc("applied_at", "id"));

        Map<Long, Integer> materialCounts = materialCounts(batchId);
        Map<Long, String> receiptByReview = latestReceipts(batchId);

        StringBuilder sb = new StringBuilder('﻿');
        sb.append("申请号,批次编码,报名号,姓名,状态,结论说明,结论依据,申请时间,结论时间,材料数,通知回执\r\n");
        for (GradeReview r : reviews) {
            sb.append(csv(r.getApplyNo())).append(',')
                    .append(csv(batch.getCode())).append(',')
                    .append(csv(r.getRegNo())).append(',')
                    .append(csv(r.getName())).append(',')
                    .append(csv(statusLabel(r.getStatus()))).append(',')
                    .append(csv(r.getConclusion())).append(',')
                    .append(csv(r.getBasis())).append(',')
                    .append(r.getAppliedAt() == null ? "" : r.getAppliedAt()).append(',')
                    .append(r.getConcludedAt() == null ? "" : r.getConcludedAt()).append(',')
                    .append(materialCounts.getOrDefault(r.getId(), 0)).append(',')
                    .append(csv(receiptByReview.getOrDefault(r.getId(), "未通知")))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<Long, Integer> materialCounts(Long batchId) {
        List<ReviewMaterial> materials = materialMapper.selectList(new QueryWrapper<ReviewMaterial>()
                .inSql("review_id", "SELECT id FROM t_grade_review WHERE batch_id = " + batchId));
        Map<Long, Integer> counts = new HashMap<>();
        for (ReviewMaterial m : materials) {
            counts.merge(m.getReviewId(), 1, Integer::sum);
        }
        return counts;
    }

    /** 每张申请最新一条通知的回执状态：已读 / 未读；无通知则不出现。 */
    private Map<Long, String> latestReceipts(Long batchId) {
        List<ReviewNotice> notices = noticeMapper.selectList(new QueryWrapper<ReviewNotice>()
                .eq("batch_id", batchId)
                .orderByAsc("notified_at", "id"));
        Map<Long, String> receipt = new HashMap<>();
        for (ReviewNotice n : notices) {
            receipt.put(n.getReviewId(),
                    GradeReviewConstants.NOTIFY_READ.equals(n.getStatus()) ? "已读" : "未读");
        }
        return receipt;
    }

    private String statusLabel(String status) {
        if (GradeReviewConstants.REVIEW_SUBMITTED.equals(status)) {
            return "待结论";
        }
        if (GradeReviewConstants.REVIEW_APPROVED.equals(status)) {
            return "通过";
        }
        if (GradeReviewConstants.REVIEW_REJECTED.equals(status)) {
            return "驳回";
        }
        return status;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
