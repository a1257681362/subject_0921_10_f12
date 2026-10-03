package com.evops.gradereview.controller;

import com.evops.gradereview.service.ReviewExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;

/**
 * 复核申请导出：批次台账 CSV。导出文件不得带出内部批注。
 */
@RestController
@RequestMapping("/api/grade-review")
public class ReviewExportController {

    private final ReviewExportService exportService;

    public ReviewExportController(ReviewExportService exportService) {
        this.exportService = exportService;
    }

    /** 批次复核台账 CSV（申请号 / 状态 / 结论 / 依据 / 材料数 / 通知回执；不含内部批注）。 */
    @GetMapping(value = "/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam Long batchId) {
        byte[] body = exportService.buildCsv(batchId);
        String fileName;
        try {
            fileName = URLEncoder.encode("fushen-" + batchId + ".csv", "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            fileName = "grade-review-" + batchId + ".csv";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"grade-review-" + batchId + ".csv\"; filename*=UTF-8''" + fileName);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
