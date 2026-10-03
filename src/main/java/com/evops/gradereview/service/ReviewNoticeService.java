package com.evops.gradereview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.gradereview.common.GradeReviewConstants;
import com.evops.gradereview.entity.ReviewNotice;
import com.evops.gradereview.entity.GradeReview;
import com.evops.gradereview.mapper.ReviewNoticeMapper;
import com.evops.gradereview.vo.MyNoticeItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 复核通知回执：结论通知挂回原申请（review_id），学员已读即回执。
 * 本版先落库（站内通知），短信等外发渠道下版再接。
 */
@Service
public class ReviewNoticeService {

    private final ReviewNoticeMapper noticeMapper;

    public ReviewNoticeService(ReviewNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    // ---------- 结论通知（挂回原申请） ----------

    @Transactional
    public ReviewNotice notifyConclusion(GradeReview review, String operator) {
        boolean approved = GradeReviewConstants.REVIEW_APPROVED.equals(review.getStatus());
        ReviewNotice notice = new ReviewNotice();
        notice.setReviewId(review.getId());
        notice.setApplyNo(review.getApplyNo());
        notice.setBatchId(review.getBatchId());
        notice.setRegistrationId(review.getRegistrationId());
        notice.setRegNo(review.getRegNo());
        notice.setName(review.getName());
        notice.setNotifyType(GradeReviewConstants.NOTIFY_REVIEW_CONCLUSION);
        notice.setTitle("复核结论：" + (approved ? "通过" : "驳回"));
        notice.setContent(String.format("您的复核申请 %s 已结论：%s。依据：%s",
                review.getApplyNo(), review.getConclusion(), review.getBasis()));
        notice.setStatus(GradeReviewConstants.NOTIFY_NEW);
        notice.setNotifiedAt(LocalDateTime.now());
        notice.setOperator(operator);
        noticeMapper.insert(notice);
        return notice;
    }

    // ---------- 查询（流转侧看回执） ----------

    public List<ReviewNotice> listByReview(Long reviewId) {
        return noticeMapper.selectList(new QueryWrapper<ReviewNotice>()
                .eq("review_id", reviewId)
                .orderByDesc("notified_at", "id"));
    }

    // ---------- 学员本人通知（已读即回执） ----------

    public List<MyNoticeItem> myNotices(String selfCode, boolean unreadOnly) {
        QueryWrapper<ReviewNotice> wrapper = new QueryWrapper<ReviewNotice>()
                .eq("reg_no", selfCode.trim())
                .orderByDesc("notified_at", "id");
        if (unreadOnly) {
            wrapper.eq("status", GradeReviewConstants.NOTIFY_NEW);
        }
        return toMyItems(noticeMapper.selectList(wrapper));
    }

    public List<MyNoticeItem> myNoticesByReview(String selfCode, Long reviewId) {
        List<ReviewNotice> notices = noticeMapper.selectList(new QueryWrapper<ReviewNotice>()
                .eq("review_id", reviewId)
                .eq("reg_no", selfCode.trim())
                .orderByDesc("notified_at", "id"));
        return toMyItems(notices);
    }

    /** 标记已读：已读钟点即回执；非本人通知统一“查无此通知”，不暴露存在性。 */
    @Transactional
    public ReviewNotice markRead(String selfCode, Long noticeId) {
        ReviewNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null || selfCode == null || !selfCode.trim().equals(notice.getRegNo())) {
            throw new BusinessException("NOTICE_NOT_FOUND", "查无此通知：" + noticeId);
        }
        if (GradeReviewConstants.NOTIFY_NEW.equals(notice.getStatus())) {
            notice.setStatus(GradeReviewConstants.NOTIFY_READ);
            notice.setReadAt(LocalDateTime.now());
            noticeMapper.updateById(notice);
        }
        return notice;
    }

    // ---------- 内部 ----------

    private List<MyNoticeItem> toMyItems(List<ReviewNotice> notices) {
        List<MyNoticeItem> items = new ArrayList<>();
        for (ReviewNotice n : notices) {
            MyNoticeItem item = new MyNoticeItem();
            item.setId(n.getId());
            item.setNotifyType(n.getNotifyType());
            item.setTitle(n.getTitle());
            item.setContent(n.getContent());
            item.setStatus(n.getStatus());
            item.setNotifiedAt(n.getNotifiedAt());
            item.setReadAt(n.getReadAt());
            items.add(item);
        }
        return items;
    }
}
