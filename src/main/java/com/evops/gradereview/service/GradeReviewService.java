package com.evops.gradereview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.service.ExamBatchService;
import com.evops.gradereview.common.GradeReviewConstants;
import com.evops.gradereview.dto.MaterialUploadRequest;
import com.evops.gradereview.dto.ReviewConcludeRequest;
import com.evops.gradereview.dto.ReviewNoteRequest;
import com.evops.gradereview.dto.ReviewSubmitRequest;
import com.evops.gradereview.entity.ReviewNote;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.entity.GradeReviewVersion;
import com.evops.gradereview.mapper.ReviewNoteMapper;
import com.evops.gradereview.mapper.GradeReviewMapper;
import com.evops.gradereview.mapper.GradeReviewVersionMapper;
import com.evops.gradereview.vo.MyReviewDetail;
import com.evops.gradereview.vo.MyReviewItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 复核申请流转：
 * - 窗口起止取批次配置（review_start_at / review_end_at），窗口只卡新受理——
 *   关闭后在办申请照常结论、历史内容照常可查；
 * - 受理 / 结论各追加一版（只追加），审核岗修改结论状态必须写明依据；
 * - 结论即站内通知学员，回执挂回原申请。
 */
@Service
public class GradeReviewService {

    private final GradeReviewMapper reviewMapper;
    private final GradeReviewVersionMapper versionMapper;
    private final ReviewNoteMapper noteMapper;
    private final ExamBatchService batchService;
    private final RegistrationMapper registrationMapper;
    private final ReviewMaterialService materialService;
    private final ReviewNoticeService noticeService;

    public GradeReviewService(GradeReviewMapper reviewMapper,
                              GradeReviewVersionMapper versionMapper,
                              ReviewNoteMapper noteMapper,
                              ExamBatchService batchService,
                              RegistrationMapper registrationMapper,
                              ReviewMaterialService materialService,
                              ReviewNoticeService noticeService) {
        this.reviewMapper = reviewMapper;
        this.versionMapper = versionMapper;
        this.noteMapper = noteMapper;
        this.batchService = batchService;
        this.registrationMapper = registrationMapper;
        this.materialService = materialService;
        this.noticeService = noticeService;
    }

    // ---------- 窗口状态（只用于新受理校验） ----------

    /** 计算窗口状态：NOT_CONFIGURED / NOT_OPENED / OPEN / CLOSED。 */
    public String windowStatus(ExamBatch batch, LocalDateTime now) {
        if (batch.getReviewStartAt() == null || batch.getReviewEndAt() == null) {
            return GradeReviewConstants.WINDOW_NOT_CONFIGURED;
        }
        if (now.isBefore(batch.getReviewStartAt())) {
            return GradeReviewConstants.WINDOW_NOT_OPENED;
        }
        if (now.isAfter(batch.getReviewEndAt())) {
            return GradeReviewConstants.WINDOW_CLOSED;
        }
        return GradeReviewConstants.WINDOW_OPEN;
    }

    // ---------- 提交复核申请（仅窗口 OPEN 受理） ----------

    @Transactional
    public GradeReview submit(ReviewSubmitRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = locateRegistration(request.getBatchId(), request.getRegNo());
        LocalDateTime now = LocalDateTime.now();

        String window = windowStatus(batch, now);
        if (GradeReviewConstants.WINDOW_NOT_CONFIGURED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_NOT_CONFIGURED",
                    "批次 " + batch.getCode() + " 尚未配置复核窗口，无法受理新申请");
        }
        if (GradeReviewConstants.WINDOW_NOT_OPENED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_NOT_OPENED",
                    "复核窗口尚未开始（" + batch.getReviewStartAt() + "），请在窗口期内提交");
        }
        if (GradeReviewConstants.WINDOW_CLOSED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_CLOSED",
                    "复核窗口已于 " + batch.getReviewEndAt() + " 关闭，不再受理新申请（历史内容仍可查看）");
        }

        GradeReview pending = reviewMapper.selectOne(new QueryWrapper<GradeReview>()
                .eq("registration_id", reg.getId())
                .eq("status", GradeReviewConstants.REVIEW_SUBMITTED));
        if (pending != null) {
            throw new BusinessException("REVIEW_DUPLICATE",
                    "该学员已有在办的复核申请（" + pending.getApplyNo() + "），请勿重复提交");
        }

        String applicant = isBlank(request.getApplicant()) ? OperatorHolder.current() : request.getApplicant().trim();

        GradeReview review = new GradeReview();
        review.setApplyNo(nextApplyNo(batch));
        review.setBatchId(batch.getId());
        review.setRegistrationId(reg.getId());
        review.setRegNo(reg.getRegNo());
        review.setName(reg.getName());
        review.setApplyReason(request.getApplyReason());
        review.setApplicant(applicant);
        review.setAppliedAt(now);
        review.setStatus(GradeReviewConstants.REVIEW_SUBMITTED);
        review.setCurrentVersion(1);
        review.setOperator(applicant);
        review.setRemark(request.getRemark());
        reviewMapper.insert(review);

        appendVersion(review, GradeReviewConstants.CHANGE_CREATE, null, null, applicant, now);

        if (request.getMaterials() != null) {
            for (com.evops.gradereview.dto.MaterialUploadRequest item : request.getMaterials()) {
                materialService.upload(review.getId(), item, applicant);
            }
        }
        return review;
    }

    // ---------- 结论：修改结论状态必须写明依据，留版 + 通知 ----------

    @Transactional
    public GradeReview conclude(Long id, ReviewConcludeRequest request) {
        GradeReview review = requireReview(id);
        String target = request.getConclusionStatus() == null
                ? "" : request.getConclusionStatus().trim().toUpperCase();
        if (!GradeReviewConstants.REVIEW_APPROVED.equals(target)
                && !GradeReviewConstants.REVIEW_REJECTED.equals(target)) {
            throw new BusinessException("CONCLUSION_STATUS_INVALID",
                    "结论状态只支持 APPROVED 通过 / REJECTED 驳回：" + request.getConclusionStatus());
        }
        if (isBlank(request.getBasis())) {
            throw new BusinessException("BASIS_REQUIRED", "修改结论状态必须写明依据");
        }
        String reviewer = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String conclusion = isBlank(request.getConclusion())
                ? (GradeReviewConstants.REVIEW_APPROVED.equals(target) ? "复核通过" : "复核驳回，维持原结论")
                : request.getConclusion().trim();

        review.setStatus(target);
        review.setConclusion(conclusion);
        review.setBasis(request.getBasis().trim());
        review.setReviewer(reviewer);
        review.setConcludedAt(now);
        review.setCurrentVersion(review.getCurrentVersion() + 1);
        reviewMapper.updateById(review);

        appendVersion(review, GradeReviewConstants.CHANGE_CONCLUDE, conclusion,
                request.getBasis().trim(), reviewer, now);

        noticeService.notifyConclusion(review, reviewer);
        return review;
    }

    // ---------- 内部批注（只追加，仅内部可见） ----------

    @Transactional
    public ReviewNote addNote(Long id, ReviewNoteRequest request) {
        GradeReview review = requireReview(id);
        if (isBlank(request.getNote())) {
            throw new BusinessException("NOTE_REQUIRED", "批注内容不能为空");
        }
        ReviewNote note = new ReviewNote();
        note.setReviewId(review.getId());
        note.setNote(request.getNote().trim());
        note.setOperator(OperatorHolder.current());
        note.setNotedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return note;
    }

    public List<ReviewNote> listNotes(Long id) {
        requireReview(id);
        return noteMapper.selectList(new QueryWrapper<ReviewNote>()
                .eq("review_id", id)
                .orderByAsc("noted_at", "id"));
    }

    // ---------- 查询（流转侧） ----------

    public GradeReview getReview(Long id) {
        return requireReview(id);
    }

    public List<GradeReview> list(Long batchId, String status) {
        QueryWrapper<GradeReview> wrapper = new QueryWrapper<GradeReview>()
                .orderByDesc("applied_at", "id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (!isBlank(status)) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    public List<GradeReviewVersion> listVersions(Long id) {
        requireReview(id);
        return versionMapper.selectList(new QueryWrapper<GradeReviewVersion>()
                .eq("review_id", id)
                .orderByAsc("version_no"));
    }

    // ---------- 学员本人视图（X-Self-Code，只读本人资料） ----------

    public List<MyReviewItem> myList(String selfCode) {
        String regNo = requireSelfCode(selfCode);
        List<GradeReview> reviews = reviewMapper.selectList(new QueryWrapper<GradeReview>()
                .eq("reg_no", regNo)
                .orderByDesc("applied_at", "id"));
        List<MyReviewItem> items = new ArrayList<>();
        for (GradeReview r : reviews) {
            MyReviewItem item = new MyReviewItem();
            item.setId(r.getId());
            item.setApplyNo(r.getApplyNo());
            item.setMaskedName(maskName(r.getName()));
            item.setStatus(r.getStatus());
            item.setAppliedAt(r.getAppliedAt());
            item.setConcludedAt(r.getConcludedAt());
            items.add(item);
        }
        return items;
    }

    public MyReviewDetail myDetail(String selfCode, Long id) {
        GradeReview review = requireOwnReview(selfCode, id);
        MyReviewDetail detail = new MyReviewDetail();
        detail.setId(review.getId());
        detail.setApplyNo(review.getApplyNo());
        detail.setMaskedName(maskName(review.getName()));
        detail.setStatus(review.getStatus());
        detail.setApplyReason(review.getApplyReason());
        detail.setAppliedAt(review.getAppliedAt());
        detail.setConclusion(review.getConclusion());
        detail.setBasis(review.getBasis());
        detail.setConcludedAt(review.getConcludedAt());
        detail.setMaterials(materialService.myMaterials(selfCode, id));
        detail.setNotices(noticeService.myNoticesByReview(selfCode, id));
        return detail;
    }

    /** 校验申请属于本人；不属于统一“查无此申请”，不暴露存在性。 */
    public GradeReview requireOwnReview(String selfCode, Long id) {
        String regNo = requireSelfCode(selfCode);
        GradeReview review = requireReview(id);
        if (!regNo.equals(review.getRegNo())) {
            throw new BusinessException("REVIEW_NOT_FOUND", "查无此申请：" + id);
        }
        return review;
    }

    public String requireSelfCode(String selfCode) {
        if (isBlank(selfCode)) {
            throw new BusinessException("SELF_CODE_REQUIRED",
                    "学员视图需带请求头 " + GradeReviewConstants.SELF_HEADER + "（报名号）");
        }
        return selfCode.trim();
    }

    // ---------- 内部 ----------

    GradeReview requireReview(Long id) {
        if (id == null) {
            throw new BusinessException("REVIEW_ID_REQUIRED", "复核申请 id 不能为空");
        }
        GradeReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "复核申请不存在：id=" + id);
        }
        return review;
    }

    private Registration locateRegistration(Long batchId, String regNo) {
        Registration reg = registrationMapper.selectOne(new QueryWrapper<Registration>()
                .eq("batch_id", batchId)
                .eq("reg_no", regNo == null ? "" : regNo.trim()));
        if (reg == null) {
            throw new BusinessException("REGISTRATION_NOT_FOUND",
                    "批次内未找到该报名号的名册：" + regNo);
        }
        return reg;
    }

    private void appendVersion(GradeReview review, String changeType, String conclusion,
                               String basis, String operator, LocalDateTime now) {
        GradeReviewVersion version = new GradeReviewVersion();
        version.setReviewId(review.getId());
        version.setVersionNo(review.getCurrentVersion());
        version.setStatus(review.getStatus());
        version.setChangeType(changeType);
        version.setConclusion(conclusion);
        version.setBasis(basis);
        version.setOperator(operator);
        version.setChangedAt(now);
        versionMapper.insert(version);
    }

    /** 申请号 FH + 批次编码 + 4 位流水（同批次内递增，不撞旧号）。 */
    private String nextApplyNo(ExamBatch batch) {
        String prefix = "FH" + batch.getCode() + "-";
        int max = 0;
        List<GradeReview> exist = reviewMapper.selectList(new QueryWrapper<GradeReview>()
                .likeRight("apply_no", prefix));
        for (GradeReview r : exist) {
            String no = r.getApplyNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "*";
        }
        StringBuilder sb = new StringBuilder(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
