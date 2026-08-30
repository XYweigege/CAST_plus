package com.voc.insight.vo;

import lombok.Data;

import java.util.Map;

/** 概览统计 */
@Data
public class FeedbackStatsVO {

    private Long total;

    private Long today;

    private Long negative;

    private Double negativeRatio;

    private Long pendingAlert;

    private Long pendingReview;

    private Double avgRating;

    private Map<String, Long> bySentiment;

    private Map<String, Long> bySource;

    private Map<String, Long> byProduct;
}
