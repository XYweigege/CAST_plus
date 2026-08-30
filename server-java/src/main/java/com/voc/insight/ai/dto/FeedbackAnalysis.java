package com.voc.insight.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 结构化分析结果（六元组）。
 */
@Data
public class FeedbackAnalysis {

    /** 情感倾向 */
    private String sentiment;

    /** 业务主题标签 */
    private List<String> topics;

    /** 紧急度 */
    private String urgency;

    /** 定级理由 */
    private String urgencyReason;

    /** 一句话归因 */
    private String aiSummary;

    /** 置信度 0-1 */
    private Double confidence;
}
