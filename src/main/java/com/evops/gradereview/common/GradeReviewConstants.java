package com.evops.gradereview.common;

/**
 * 复核申请与材料流转业务常量。
 */
public final class GradeReviewConstants {

    private GradeReviewConstants() {
    }

    /** 学员本人视图请求头：传报名号即只看本人资料。 */
    public static final String SELF_HEADER = "X-Self-Code";

    // 复核申请状态（结论状态）
    /** 已受理待结论：窗口期内提交即受理。 */
    public static final String REVIEW_SUBMITTED = "SUBMITTED";
    /** 复核通过。 */
    public static final String REVIEW_APPROVED = "APPROVED";
    /** 复核驳回。 */
    public static final String REVIEW_REJECTED = "REJECTED";

    // 申请版本链变更类型
    /** 受理（提交即受理，窗口只卡新受理）。 */
    public static final String CHANGE_CREATE = "CREATE";
    /** 结论（含首次结论与改结论，依据必填）。 */
    public static final String CHANGE_CONCLUDE = "CONCLUDE";

    // 材料版本变更类型
    /** 首次上传。 */
    public static final String MATERIAL_UPLOAD = "UPLOAD";
    /** 附件版本替换（必须留替换摘要）。 */
    public static final String MATERIAL_REPLACE = "REPLACE";

    // 材料类型
    public static final String MT_DOC = "DOC";
    public static final String MT_PHOTO = "PHOTO";
    public static final String MT_VIDEO = "VIDEO";
    public static final String MT_OTHER = "OTHER";

    // 通知类型 / 回执状态
    /** 复核结论通知（回执挂回原申请）。 */
    public static final String NOTIFY_REVIEW_CONCLUSION = "REVIEW_CONCLUSION";
    public static final String NOTIFY_NEW = "NEW";
    public static final String NOTIFY_READ = "READ";

    // 复核窗口状态（计算值，不入库；窗口口径与批次 review_start_at / review_end_at 一致）
    public static final String WINDOW_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String WINDOW_NOT_OPENED = "NOT_OPENED";
    public static final String WINDOW_OPEN = "OPEN";
    public static final String WINDOW_CLOSED = "CLOSED";
}
