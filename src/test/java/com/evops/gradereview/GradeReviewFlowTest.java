package com.evops.gradereview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.entity.WorkspaceVersion;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.service.WorkspaceVersionService;
import com.evops.gradereview.common.GradeReviewConstants;
import com.evops.gradereview.dto.MaterialReplaceRequest;
import com.evops.gradereview.dto.MaterialUploadRequest;
import com.evops.gradereview.dto.ReviewConcludeRequest;
import com.evops.gradereview.dto.ReviewNoteRequest;
import com.evops.gradereview.dto.ReviewSubmitRequest;
import com.evops.gradereview.entity.ReviewMaterial;
import com.evops.gradereview.entity.ReviewMaterialVersion;
import com.evops.gradereview.entity.ReviewNotice;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.entity.GradeReviewVersion;
import com.evops.gradereview.mapper.GradeReviewMapper;
import com.evops.gradereview.service.GradeReviewService;
import com.evops.gradereview.service.ReviewExportService;
import com.evops.gradereview.service.ReviewMaterialService;
import com.evops.gradereview.service.ReviewNoticeService;
import com.evops.gradereview.vo.MyMaterialItem;
import com.evops.gradereview.vo.MyNoticeItem;
import com.evops.gradereview.vo.MyReviewDetail;
import com.evops.gradereview.vo.MyReviewItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复核申请端到端：
 * 窗口只卡新受理（关闭后在办可结论、历史可查）→ 受理留版 → 材料按时间排列、附件替换留摘要
 * → 审核岗写依据出结论（版本只追加）→ 通知回执挂回原申请 → 学员只读本人资料
 * → 内部批注不进学员视图与导出 → 纳入工作区快照/回退。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GradeReviewFlowTest {

    @Autowired
    private GradeReviewService reviewService;
    @Autowired
    private ReviewMaterialService materialService;
    @Autowired
    private ReviewNoticeService noticeService;
    @Autowired
    private ReviewExportService exportService;
    @Autowired
    private GradeReviewMapper reviewMapper;
    @Autowired
    private WorkspaceVersionService workspaceVersionService;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;
    @Autowired
    private RegistrationMapper registrationMapper;

    private ExamBatch openBatch;

    /** 幂等造基础数据：多方法共享上下文（AFTER_CLASS 才清），快照回退会清业务表，每方法开头补齐。 */
    private void ensureBase() {
        ExamSite site = siteMapper.selectOne(new QueryWrapper<ExamSite>().eq("code", "SGRV"));
        if (site == null) {
            site = new ExamSite();
            site.setCode("SGRV");
            site.setName("复核考点");
            site.setStatus("ENABLED");
            siteMapper.insert(site);
        }
        openBatch = ensureBatch("BGRV", site.getId(),
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1));
        ensureBatch("BGRVC", site.getId(),
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        ensureBatch("BGRVN", site.getId(), null, null);
        ensureBatch("BGRVF", site.getId(),
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        ensureBatch("BGRVX", site.getId(),
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1));
        ensureReg("GR001", "张三", "BGRV");
        ensureReg("GR002", "李四", "BGRV");
        ensureReg("GR003", "周九", "BGRV");
        ensureReg("GR004", "吴十", "BGRV");
        ensureReg("GR005", "郑一", "BGRV");
        ensureReg("GR006", "冯二", "BGRV");
        ensureReg("GC001", "王五", "BGRVC");
        ensureReg("GN001", "赵六", "BGRVN");
        ensureReg("GF001", "钱七", "BGRVF");
        ensureReg("GX001", "孙八", "BGRVX");
    }

    private ExamBatch ensureBatch(String code, Long siteId, LocalDateTime start, LocalDateTime end) {
        ExamBatch batch = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", code));
        if (batch == null) {
            batch = new ExamBatch();
            batch.setCode(code);
            batch.setName("复核批次" + code);
            batch.setSiteId(siteId);
            batch.setProjectCode("DEFAULT");
            batch.setExamDate(LocalDate.of(2026, 10, 1));
            batch.setStatus("CREATED");
            batch.setReviewStartAt(start);
            batch.setReviewEndAt(end);
            batchMapper.insert(batch);
        }
        return batch;
    }

    private void ensureReg(String regNo, String name, String batchCode) {
        Long count = registrationMapper.selectCount(new QueryWrapper<Registration>().eq("reg_no", regNo));
        if (count != null && count > 0) {
            return;
        }
        ExamBatch batch = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", batchCode));
        Registration reg = new Registration();
        reg.setRegNo(regNo);
        reg.setName(name);
        reg.setIdNo("ID" + regNo);
        reg.setBatchId(batch.getId());
        reg.setStatus("REGISTERED");
        registrationMapper.insert(reg);
    }

    private ReviewSubmitRequest submitRequest(String regNo, Long batchId) {
        ReviewSubmitRequest req = new ReviewSubmitRequest();
        req.setBatchId(batchId);
        req.setRegNo(regNo);
        req.setApplyReason("对成绩有疑问，申请复核");
        return req;
    }

    private MaterialUploadRequest material(String type, String title, String fileRef) {
        MaterialUploadRequest m = new MaterialUploadRequest();
        m.setMaterialType(type);
        m.setTitle(title);
        m.setFileRef(fileRef);
        m.setContentHash("hash-" + title);
        return m;
    }

    private ReviewConcludeRequest concludeRequest(String status, String basis) {
        ReviewConcludeRequest req = new ReviewConcludeRequest();
        req.setConclusionStatus(status);
        req.setBasis(basis);
        return req;
    }

    private void runAs(String operator, Runnable action) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Operator", operator);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            action.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    // ---------- 窗口只卡新受理 ----------

    @Test
    void windowGatesOnlyNewAcceptance() {
        ensureBase();
        ExamBatch noWindow = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", "BGRVN"));
        ExamBatch notOpened = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", "BGRVF"));
        ExamBatch closed = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", "BGRVC"));

        BusinessException e1 = assertThrows(BusinessException.class,
                () -> reviewService.submit(submitRequest("GN001", noWindow.getId())));
        assertEquals("REVIEW_WINDOW_NOT_CONFIGURED", e1.getCode());

        BusinessException e2 = assertThrows(BusinessException.class,
                () -> reviewService.submit(submitRequest("GF001", notOpened.getId())));
        assertEquals("REVIEW_WINDOW_NOT_OPENED", e2.getCode());

        BusinessException e3 = assertThrows(BusinessException.class,
                () -> reviewService.submit(submitRequest("GC001", closed.getId())));
        assertEquals("REVIEW_WINDOW_CLOSED", e3.getCode());
        assertTrue(e3.getMessage().contains("历史内容仍可查看"));
    }

    @Test
    void windowClosed_inFlightStillConcludableAndHistoryReadable() {
        ensureBase();
        ExamBatch batchX = batchMapper.selectOne(new QueryWrapper<ExamBatch>().eq("code", "BGRVX"));
        GradeReview review = reviewService.submit(submitRequest("GX001", batchX.getId()));

        // 窗口关闭：只影响新受理
        batchX.setReviewEndAt(LocalDateTime.now().minusMinutes(1));
        batchMapper.updateById(batchX);

        BusinessException e = assertThrows(BusinessException.class,
                () -> reviewService.submit(submitRequest("GX001", batchX.getId())));
        assertEquals("REVIEW_WINDOW_CLOSED", e.getCode());

        // 在办申请照常结论
        runAs("reviewer01", () -> reviewService.conclude(review.getId(),
                concludeRequest("APPROVED", "卷面核分有误，更正后通过")));
        GradeReview after = reviewService.getReview(review.getId());
        assertEquals(GradeReviewConstants.REVIEW_APPROVED, after.getStatus());
        assertEquals("reviewer01", after.getReviewer());

        // 历史内容照常可查
        assertEquals(1, reviewService.list(batchX.getId(), null).size());
        assertEquals(2, reviewService.listVersions(review.getId()).size());
    }

    // ---------- 受理 → 材料 → 结论 → 回执 全链路 ----------

    @Test
    void fullFlow_materialVersionsAndConclusionBasis() {
        ensureBase();
        ReviewSubmitRequest req = submitRequest("GR001", openBatch.getId());
        req.setMaterials(new ArrayList<>(Arrays.asList(
                material("DOC", "成绩异议说明", "files/gr001/yiyi.pdf"),
                material("PHOTO", "答卷照片", "files/gr001/dajuan.jpg"))));
        GradeReview review = reviewService.submit(req);

        assertTrue(review.getApplyNo().startsWith("FHBGRV-"));
        assertEquals(GradeReviewConstants.REVIEW_SUBMITTED, review.getStatus());
        assertEquals(1, review.getCurrentVersion());

        // 在办防重
        BusinessException dup = assertThrows(BusinessException.class,
                () -> reviewService.submit(submitRequest("GR001", openBatch.getId())));
        assertEquals("REVIEW_DUPLICATE", dup.getCode());

        // 材料按时间排列 + 补传
        materialService.upload(review.getId(), material("OTHER", "补充说明", "files/gr001/buchong.txt"), null);
        List<ReviewMaterial> materials = materialService.listByReview(review.getId());
        assertEquals(3, materials.size());
        assertTrue(materials.get(0).getUploadedAt().compareTo(materials.get(2).getUploadedAt()) <= 0);

        // 附件替换必须留摘要
        Long materialId = materials.get(0).getId();
        MaterialReplaceRequest noSummary = new MaterialReplaceRequest();
        noSummary.setFileRef("files/gr001/yiyi-v2.pdf");
        BusinessException noSum = assertThrows(BusinessException.class,
                () -> materialService.replace(materialId, noSummary));
        assertEquals("CHANGE_SUMMARY_REQUIRED", noSum.getCode());

        MaterialReplaceRequest replace = new MaterialReplaceRequest();
        replace.setFileRef("files/gr001/yiyi-v2.pdf");
        replace.setContentHash("hash-v2");
        replace.setChangeSummary("替换为签字版异议说明");
        materialService.replace(materialId, replace);
        ReviewMaterial replaced = materialService.getMaterial(materialId);
        assertEquals(2, replaced.getCurrentVersion());
        assertEquals("files/gr001/yiyi-v2.pdf", replaced.getFileRef());
        List<ReviewMaterialVersion> mvs = materialService.listVersions(materialId);
        assertEquals(2, mvs.size());
        assertEquals("REPLACE", mvs.get(1).getChangeType());
        assertEquals("替换为签字版异议说明", mvs.get(1).getChangeSummary());

        // 结论必须写依据
        BusinessException noBasis = assertThrows(BusinessException.class,
                () -> reviewService.conclude(review.getId(), concludeRequest("APPROVED", "  ")));
        assertEquals("BASIS_REQUIRED", noBasis.getCode());

        // 审核岗写依据出结论 → 版本 + 通知挂申请
        runAs("reviewer01", () -> reviewService.conclude(review.getId(),
                concludeRequest("APPROVED", "卷面核分漏加 5 分，予以更正")));
        GradeReview concluded = reviewService.getReview(review.getId());
        assertEquals(GradeReviewConstants.REVIEW_APPROVED, concluded.getStatus());
        assertEquals("卷面核分漏加 5 分，予以更正", concluded.getBasis());
        assertEquals(2, concluded.getCurrentVersion());

        List<ReviewNotice> notices = noticeService.listByReview(review.getId());
        assertEquals(1, notices.size());
        assertEquals(review.getId(), notices.get(0).getReviewId());
        assertEquals(GradeReviewConstants.NOTIFY_NEW, notices.get(0).getStatus());
        assertTrue(notices.get(0).getContent().contains("卷面核分漏加 5 分"));

        // 已结论申请的材料只读
        BusinessException readOnly = assertThrows(BusinessException.class,
                () -> materialService.upload(review.getId(), material("DOC", "晚到材料", "f/x.pdf"), null));
        assertEquals("REVIEW_ALREADY_CONCLUDED", readOnly.getCode());

        // 改结论仍须依据，版本只追加
        runAs("reviewer02", () -> reviewService.conclude(review.getId(),
                concludeRequest("REJECTED", "复查原始答卷，核分无误")));
        GradeReview changed = reviewService.getReview(review.getId());
        assertEquals(GradeReviewConstants.REVIEW_REJECTED, changed.getStatus());
        assertEquals(3, changed.getCurrentVersion());
        List<GradeReviewVersion> versions = reviewService.listVersions(review.getId());
        assertEquals(3, versions.size());
        assertEquals("CREATE", versions.get(0).getChangeType());
        assertEquals("CONCLUDE", versions.get(1).getChangeType());
        assertEquals("CONCLUDE", versions.get(2).getChangeType());
        assertEquals("复查原始答卷，核分无误", versions.get(2).getBasis());
        assertEquals(2, noticeService.listByReview(review.getId()).size());
    }

    // ---------- 通知回执挂回原申请 ----------

    @Test
    void notificationReceiptHangsOnRequest() {
        ensureBase();
        GradeReview review = reviewService.submit(submitRequest("GR002", openBatch.getId()));
        runAs("reviewer01", () -> reviewService.conclude(review.getId(),
                concludeRequest("REJECTED", "核分无误，维持原成绩")));

        List<MyNoticeItem> mine = noticeService.myNotices("GR002", false);
        assertEquals(1, mine.size());
        assertEquals(GradeReviewConstants.NOTIFY_NEW, mine.get(0).getStatus());

        // 别人的回执不存在
        BusinessException notMine = assertThrows(BusinessException.class,
                () -> noticeService.markRead("GR001", mine.get(0).getId()));
        assertEquals("NOTICE_NOT_FOUND", notMine.getCode());

        // 本人已读即回执
        ReviewNotice read = noticeService.markRead("GR002", mine.get(0).getId());
        assertEquals(GradeReviewConstants.NOTIFY_READ, read.getStatus());
        assertNotNull(read.getReadAt());
        assertEquals(GradeReviewConstants.NOTIFY_READ,
                noticeService.listByReview(review.getId()).get(0).getStatus());
    }

    // ---------- 学员只读本人资料 ----------

    @Test
    void studentView_isolatedAndMasked() {
        ensureBase();
        ReviewSubmitRequest req = submitRequest("GR003", openBatch.getId());
        req.setMaterials(new ArrayList<>(java.util.Collections.singletonList(
                material("DOC", "异议说明", "files/gr003/a.pdf"))));
        GradeReview r1 = reviewService.submit(req);
        GradeReview r2 = reviewService.submit(submitRequest("GR004", openBatch.getId()));
        reviewService.addNote(r1.getId(), noteReq("内部批注：重点关注该生"));

        List<MyReviewItem> mine = reviewService.myList("GR003");
        assertEquals(1, mine.size());
        assertEquals(r1.getId(), mine.get(0).getId());
        assertEquals("周*", mine.get(0).getMaskedName());

        // 看别人的申请：查无此申请，不暴露存在性
        BusinessException e = assertThrows(BusinessException.class,
                () -> reviewService.myDetail("GR003", r2.getId()));
        assertEquals("REVIEW_NOT_FOUND", e.getCode());
        BusinessException e2 = assertThrows(BusinessException.class,
                () -> materialService.myMaterials("GR003", r2.getId()));
        assertEquals("REVIEW_NOT_FOUND", e2.getCode());

        // 本人详情：结论与材料可读，不含内部批注
        MyReviewDetail detail = reviewService.myDetail("GR003", r1.getId());
        assertEquals("周*", detail.getMaskedName());
        assertEquals(1, detail.getMaterials().size());
        assertNull(detail.getConclusion());

        // 缺 X-Self-Code 拒绝
        BusinessException noCode = assertThrows(BusinessException.class,
                () -> reviewService.myList(null));
        assertEquals("SELF_CODE_REQUIRED", noCode.getCode());
    }

    // ---------- 内部批注不进导出 ----------

    @Test
    void exportCarriesBasisButNeverInternalNotes() {
        ensureBase();
        GradeReview review = reviewService.submit(submitRequest("GR005", openBatch.getId()));
        reviewService.addNote(review.getId(), noteReq("内部批注：该生有前科记录"));
        runAs("reviewer01", () -> reviewService.conclude(review.getId(),
                concludeRequest("APPROVED", "核分漏加，予以更正")));

        String csv = new String(exportService.buildCsv(openBatch.getId()), StandardCharsets.UTF_8);
        assertTrue(csv.contains("申请号,批次编码,报名号,姓名,状态,结论说明,结论依据"));
        assertTrue(csv.contains("核分漏加，予以更正"));
        assertTrue(csv.contains("未读"));
        assertFalse(csv.contains("内部批注"), "导出不得带出内部批注");
        assertFalse(csv.contains("前科"), "导出不得带出批注内容");

        // 批注只追加、内部可查
        reviewService.addNote(review.getId(), noteReq("第二条批注"));
        assertEquals(2, reviewService.listNotes(review.getId()).size());
    }

    // ---------- 纳入工作区快照 / 回退 ----------

    @Test
    void snapshotAndRollback() {
        ensureBase();
        GradeReview review = reviewService.submit(submitRequest("GR006", openBatch.getId()));
        WorkspaceVersion snap = workspaceVersionService.createSnapshot("复核测试快照", "包含复核申请");

        runAs("reviewer01", () -> reviewService.conclude(review.getId(),
                concludeRequest("APPROVED", "核分漏加，予以更正")));
        assertEquals(GradeReviewConstants.REVIEW_APPROVED,
                reviewMapper.selectById(review.getId()).getStatus());

        workspaceVersionService.rollback(snap.getVersionNo(), "回退复核结论");
        GradeReview restored = reviewMapper.selectById(review.getId());
        assertEquals(GradeReviewConstants.REVIEW_SUBMITTED, restored.getStatus());
        assertNull(restored.getBasis());
        ensureBase();
    }

    private ReviewNoteRequest noteReq(String note) {
        ReviewNoteRequest req = new ReviewNoteRequest();
        req.setNote(note);
        return req;
    }
}
