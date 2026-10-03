package com.evops.gradereview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.gradereview.common.GradeReviewConstants;
import com.evops.gradereview.dto.MaterialReplaceRequest;
import com.evops.gradereview.dto.MaterialUploadRequest;
import com.evops.gradereview.entity.ReviewMaterial;
import com.evops.gradereview.entity.ReviewMaterialVersion;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.mapper.ReviewMaterialMapper;
import com.evops.gradereview.mapper.ReviewMaterialVersionMapper;
import com.evops.gradereview.mapper.GradeReviewMapper;
import com.evops.gradereview.vo.MyMaterialItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 复核材料阅读与流转：
 * - 材料进入 evops-base 后按上传时间排列；
 * - 附件版本替换只追加新版本并留下替换摘要，旧版本永不修改；
 * - 在办申请（SUBMITTED）才可补传 / 替换，已结论申请的材料只读。
 */
@Service
public class ReviewMaterialService {

    private static final Set<String> MATERIAL_TYPES = new HashSet<>(Arrays.asList(
            GradeReviewConstants.MT_DOC, GradeReviewConstants.MT_PHOTO,
            GradeReviewConstants.MT_VIDEO, GradeReviewConstants.MT_OTHER));

    private final ReviewMaterialMapper materialMapper;
    private final ReviewMaterialVersionMapper versionMapper;
    private final GradeReviewMapper reviewMapper;

    public ReviewMaterialService(ReviewMaterialMapper materialMapper,
                                 ReviewMaterialVersionMapper versionMapper,
                                 GradeReviewMapper reviewMapper) {
        this.materialMapper = materialMapper;
        this.versionMapper = versionMapper;
        this.reviewMapper = reviewMapper;
    }

    // ---------- 上传（受理时随申请带入，或在办中补传） ----------

    @Transactional
    public ReviewMaterial upload(Long reviewId, MaterialUploadRequest request, String operator) {
        GradeReview review = requireReview(reviewId);
        requireInFlight(review, "补传材料");

        String type = request.getMaterialType() == null
                ? "" : request.getMaterialType().trim().toUpperCase();
        if (!MATERIAL_TYPES.contains(type)) {
            throw new BusinessException("MATERIAL_TYPE_INVALID",
                    "材料类型只支持 DOC 文档 / PHOTO 照片 / VIDEO 视频 / OTHER 其他：" + request.getMaterialType());
        }
        if (isBlank(request.getTitle())) {
            throw new BusinessException("MATERIAL_TITLE_REQUIRED", "材料标题不能为空");
        }
        if (isBlank(request.getFileRef()) && isBlank(request.getContent())) {
            throw new BusinessException("MATERIAL_CONTENT_REQUIRED",
                    "附件类材料请给 fileRef（建议同时给 contentHash），文字材料请给 content 正文");
        }

        String uploader = isBlank(operator) ? OperatorHolder.current() : operator.trim();
        LocalDateTime now = LocalDateTime.now();

        ReviewMaterial material = new ReviewMaterial();
        material.setReviewId(review.getId());
        material.setApplyNo(review.getApplyNo());
        material.setMaterialType(type);
        material.setTitle(request.getTitle().trim());
        material.setFileRef(trimToNull(request.getFileRef()));
        material.setContentHash(trimToNull(request.getContentHash()));
        material.setContent(trimToNull(request.getContent()));
        material.setCurrentVersion(1);
        material.setUploadedBy(uploader);
        material.setUploadedAt(now);
        materialMapper.insert(material);

        appendVersion(material, GradeReviewConstants.MATERIAL_UPLOAD,
                isBlank(request.getSummary()) ? "首次上传" : request.getSummary().trim(), uploader, now);
        return material;
    }

    // ---------- 附件版本替换（必须留替换摘要） ----------

    @Transactional
    public ReviewMaterial replace(Long materialId, MaterialReplaceRequest request) {
        ReviewMaterial material = requireMaterial(materialId);
        GradeReview review = requireReview(material.getReviewId());
        requireInFlight(review, "替换材料附件");

        if (isBlank(request.getChangeSummary())) {
            throw new BusinessException("CHANGE_SUMMARY_REQUIRED", "附件版本替换必须留下替换摘要");
        }
        if (isBlank(request.getFileRef()) && isBlank(request.getContentHash()) && isBlank(request.getContent())) {
            throw new BusinessException("REPLACE_CONTENT_REQUIRED",
                    "替换至少带来一项新内容：fileRef / contentHash / content");
        }

        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 未给的字段沿用上版，版本行存合并后的完整快照
        material.setFileRef(request.getFileRef() != null ? trimToNull(request.getFileRef()) : material.getFileRef());
        material.setContentHash(request.getContentHash() != null ? trimToNull(request.getContentHash()) : material.getContentHash());
        material.setContent(request.getContent() != null ? trimToNull(request.getContent()) : material.getContent());
        material.setCurrentVersion(material.getCurrentVersion() + 1);
        materialMapper.updateById(material);

        appendVersion(material, GradeReviewConstants.MATERIAL_REPLACE,
                request.getChangeSummary().trim(), operator, now);
        return material;
    }

    // ---------- 阅读（按时间排列；版本链只追加） ----------

    /** 材料清单：进入 evops-base 后按上传时间排列。 */
    public List<ReviewMaterial> listByReview(Long reviewId) {
        requireReview(reviewId);
        return materialMapper.selectList(new QueryWrapper<ReviewMaterial>()
                .eq("review_id", reviewId)
                .orderByAsc("uploaded_at", "id"));
    }

    public ReviewMaterial getMaterial(Long materialId) {
        return requireMaterial(materialId);
    }

    public List<ReviewMaterialVersion> listVersions(Long materialId) {
        requireMaterial(materialId);
        return versionMapper.selectList(new QueryWrapper<ReviewMaterialVersion>()
                .eq("material_id", materialId)
                .orderByAsc("version_no"));
    }

    // ---------- 学员本人材料阅读 ----------

    public List<MyMaterialItem> myMaterials(String selfCode, Long reviewId) {
        GradeReview review = requireReview(reviewId);
        if (selfCode == null || !selfCode.trim().equals(review.getRegNo())) {
            throw new BusinessException("REVIEW_NOT_FOUND", "查无此申请：" + reviewId);
        }
        List<ReviewMaterial> materials = materialMapper.selectList(new QueryWrapper<ReviewMaterial>()
                .eq("review_id", reviewId)
                .orderByAsc("uploaded_at", "id"));
        List<MyMaterialItem> items = new ArrayList<>();
        for (ReviewMaterial m : materials) {
            MyMaterialItem item = new MyMaterialItem();
            item.setId(m.getId());
            item.setMaterialType(m.getMaterialType());
            item.setTitle(m.getTitle());
            item.setFileRef(m.getFileRef());
            item.setContentHash(m.getContentHash());
            item.setContent(m.getContent());
            item.setCurrentVersion(m.getCurrentVersion());
            item.setUploadedAt(m.getUploadedAt());
            items.add(item);
        }
        return items;
    }

    // ---------- 内部 ----------

    private void requireInFlight(GradeReview review, String action) {
        if (!GradeReviewConstants.REVIEW_SUBMITTED.equals(review.getStatus())) {
            throw new BusinessException("REVIEW_ALREADY_CONCLUDED",
                    "申请 " + review.getApplyNo() + " 已结论（" + review.getStatus() + "），不能再" + action);
        }
    }

    private GradeReview requireReview(Long reviewId) {
        GradeReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "复核申请不存在：id=" + reviewId);
        }
        return review;
    }

    private ReviewMaterial requireMaterial(Long materialId) {
        if (materialId == null) {
            throw new BusinessException("MATERIAL_ID_REQUIRED", "材料 id 不能为空");
        }
        ReviewMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在：id=" + materialId);
        }
        return material;
    }

    private void appendVersion(ReviewMaterial material, String changeType,
                               String changeSummary, String operator, LocalDateTime now) {
        ReviewMaterialVersion version = new ReviewMaterialVersion();
        version.setMaterialId(material.getId());
        version.setVersionNo(material.getCurrentVersion());
        version.setChangeType(changeType);
        version.setFileRef(material.getFileRef());
        version.setContentHash(material.getContentHash());
        version.setContent(material.getContent());
        version.setChangeSummary(changeSummary);
        version.setOperator(operator);
        version.setChangedAt(now);
        versionMapper.insert(version);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
