package com.voc.insight.vo;

import lombok.Data;

import java.util.List;

/**
 * 评分归因报告。
 * 统计部分由代码计算（可复现），归纳部分由 LLM 生成。
 */
@Data
public class InsightReport {

    private String productLine;

    private Integer totalFeedback;

    /** 平均评分（无评分数据时为 null） */
    private Double avgRating;

    /** 负面占比 0-1 */
    private Double negativeRatio;

    /** 主题分布，按出现次数降序 */
    private List<TopicStat> topTopics;

    /** AI 归因结论 */
    private String summary;

    /** AI 改进建议 */
    private List<String> suggestions;

    @Data
    public static class TopicStat {
        private String topic;
        private Integer count;
        private Integer negativeCount;
    }
}
