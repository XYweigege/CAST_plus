package com.voc.insight.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户反馈。
 * 字段分四组：原始采集信息、AI 分析输出、人工复核、关联。
 */
@Data
@TableName("feedback")
public class Feedback implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    // ===== 原始采集信息 =====

    /** 标题（问卷/工单可能有） */
    private String title;

    /** 反馈正文 */
    private String content;

    /** 渠道编码 */
    private String source;

    /** 业务系统内唯一 ID，去重依据 */
    private String sourceId;

    /** 原文链接 */
    private String url;

    /** 客户评分 1-5 */
    private Integer rating;

    /** 产品线编码 */
    private String productLine;

    /** 语言：zh-HK / en / mixed */
    private String language;

    /** 客户名（应脱敏） */
    private String authorName;

    /** 反馈发生时间 */
    private LocalDateTime publishedAt;

    /** 入库时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ===== AI 分析输出 =====

    /** 情感倾向 */
    private String sentiment;

    /** 主题标签，JSON 数组字符串 */
    private String topics;

    /** 紧急度 */
    private String urgency;

    /** 定级理由 */
    private String urgencyReason;

    /** 一句话归因 */
    private String aiSummary;

    /** 置信度 0-1 */
    private BigDecimal confidence;

    // ===== 人工复核 =====

    /** 人工修正结果 JSON */
    private String humanLabel;

    /** 是否已复核 */
    private Boolean isReviewed;

    // ===== 关联 =====

    /** 归属主题词 ID */
    private String topicId;
}
