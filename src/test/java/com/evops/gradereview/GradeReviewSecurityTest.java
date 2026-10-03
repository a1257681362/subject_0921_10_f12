package com.evops.gradereview;

import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.gradereview.dto.ReviewNoteRequest;
import com.evops.gradereview.dto.ReviewSubmitRequest;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.service.GradeReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复核窗口授权边界：
 * - 审核岗 reviewer 只能访问 /api/grade-review/**；访问其他接口 403；
 * - 用人单位 employer、档案岗 archiver 访问复核窗口 403；
 * - 内部管理 bootstrap 全部可访问；未认证 401；
 * - 学员凭 X-Self-Code 只读本人资料；导出不带内部批注。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GradeReviewSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GradeReviewService reviewService;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;
    @Autowired
    private RegistrationMapper registrationMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 幂等种子：两个测试方法共享同一 Spring 上下文，缺什么补什么。 */
    private Long seedReview() {
        ExamSite site = siteMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamSite>().eq("code", "SGRS"));
        if (site == null) {
            site = new ExamSite();
            site.setCode("SGRS");
            site.setName("复核安全考点");
            site.setStatus("ENABLED");
            siteMapper.insert(site);
        }
        ExamBatch batch = batchMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamBatch>().eq("code", "BGRS"));
        if (batch == null) {
            batch = new ExamBatch();
            batch.setCode("BGRS");
            batch.setName("复核安全批次");
            batch.setSiteId(site.getId());
            batch.setProjectCode("DEFAULT");
            batch.setExamDate(LocalDate.of(2026, 10, 1));
            batch.setStatus("CREATED");
            batch.setReviewStartAt(LocalDateTime.now().minusHours(1));
            batch.setReviewEndAt(LocalDateTime.now().plusDays(1));
            batchMapper.insert(batch);
        }
        Registration reg = registrationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Registration>().eq("reg_no", "GS001"));
        if (reg == null) {
            reg = new Registration();
            reg.setRegNo("GS001");
            reg.setName("安测学员");
            reg.setBatchId(batch.getId());
            reg.setStatus("REGISTERED");
            registrationMapper.insert(reg);
        }

        final Long batchId = batch.getId();
        GradeReview review = reviewService.list(batchId, null).stream()
                .findFirst().orElseGet(() -> {
                    ReviewSubmitRequest submit = new ReviewSubmitRequest();
                    submit.setBatchId(batchId);
                    submit.setRegNo("GS001");
                    submit.setApplyReason("安全测试申请");
                    return reviewService.submit(submit);
                });
        if (reviewService.listNotes(review.getId()).isEmpty()) {
            ReviewNoteRequest note = new ReviewNoteRequest();
            note.setNote("内部批注：安全测试专用暗号");
            reviewService.addNote(review.getId(), note);
        }
        return review.getId();
    }

    @Test
    void reviewWindow_roleBoundaries() {
        Long reviewId = seedReview();
        String listPath = "/api/grade-review/requests";
        String detailPath = "/api/grade-review/requests/" + reviewId;
        String otherPath = "/api/discipline/cases";

        // 未认证 → 401
        assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.getForEntity(url(listPath), String.class).getStatusCode());

        // 审核岗 reviewer：复核窗口可访问
        assertEquals(HttpStatus.OK, restTemplate.withBasicAuth("reviewer", "reviewer")
                .getForEntity(url(listPath), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.withBasicAuth("reviewer", "reviewer")
                .getForEntity(url(detailPath), String.class).getStatusCode());

        // 审核岗访问其他窗口 → 403
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.withBasicAuth("reviewer", "reviewer")
                .getForEntity(url(otherPath), String.class).getStatusCode());

        // 用人单位 / 档案岗访问复核窗口 → 403
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url(listPath), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.withBasicAuth("archiver", "archiver")
                .getForEntity(url(listPath), String.class).getStatusCode());

        // 内部管理 bootstrap：复核窗口与其他接口都可访问
        assertEquals(HttpStatus.OK, restTemplate.withBasicAuth("bootstrap", "bootstrap")
                .getForEntity(url(listPath), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.withBasicAuth("bootstrap", "bootstrap")
                .getForEntity(url(otherPath), String.class).getStatusCode());
    }

    @Test
    void studentSelfViewAndExport() {
        Long reviewId = seedReview();

        // 学员视图：带 X-Self-Code 只回本人；不带 → 可读失败原因（统一 ApiResponse）
        HttpHeaders selfHeaders = new HttpHeaders();
        selfHeaders.add("X-Self-Code", "GS001");
        ResponseEntity<String> my = restTemplate.withBasicAuth("reviewer", "reviewer")
                .exchange(url("/api/grade-review/my/requests"), HttpMethod.GET,
                        new HttpEntity<>(selfHeaders), String.class);
        assertEquals(HttpStatus.OK, my.getStatusCode());
        assertTrue(my.getBody().contains("安***"));
        assertFalse(my.getBody().contains("内部批注"), "学员视图不得带出内部批注");

        ResponseEntity<String> noCode = restTemplate.withBasicAuth("reviewer", "reviewer")
                .getForEntity(url("/api/grade-review/my/requests"), String.class);
        assertEquals(HttpStatus.OK, noCode.getStatusCode());
        assertTrue(noCode.getBody().contains("SELF_CODE_REQUIRED"));

        // 导出：审核岗可下载，且不含内部批注
        Long batchId = reviewService.getReview(reviewId).getBatchId();
        ResponseEntity<String> csv = restTemplate.withBasicAuth("reviewer", "reviewer")
                .getForEntity(url("/api/grade-review/export.csv?batchId=" + batchId), String.class);
        assertEquals(HttpStatus.OK, csv.getStatusCode());
        assertTrue(csv.getBody().contains("GS001"));
        assertFalse(csv.getBody().contains("内部批注"), "导出文件不得带出内部批注");
        assertFalse(csv.getBody().contains("安全测试专用暗号"), "导出文件不得带出批注内容");

        // 用人单位连导出窗口也进不去
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url("/api/grade-review/export.csv?batchId=" + batchId), String.class)
                .getStatusCode());
    }
}
