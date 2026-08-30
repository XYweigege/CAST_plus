package com.voc.insight.constant;

import java.util.List;
import java.util.Map;

/**
 * 业务字典。
 * 主题体系是整个 AI 分析层的骨架，Prompt 约束、落库校验、
 * 前端筛选、归因报告共用同一套枚举，需与前端 constants.ts 保持一致。
 */
public final class BusinessDict {

    private BusinessDict() {
    }

    /** 客户反馈主题标签：AI 只能从这些里面选，避免标签爆炸 */
    public static final List<String> TOPIC_TAGS = List.of(
            "理赔时效", "赔付金额争议", "拒赔争议", "核保与投保",
            "客服响应", "服务态度", "条款清晰度", "销售误导",
            "续保与退保", "APP与网站体验", "价格与性价比", "理赔资料繁琐"
    );

    /** 触发预警的紧急度（info / attention 不告警，避免告警疲劳） */
    public static final List<String> ALERT_URGENCIES = List.of("action", "critical");

    /** 置信度低于该阈值的 AI 判定转人工复核 */
    public static final double CONFIDENCE_THRESHOLD = 0.7;

    /** 紧急度排序映射：数值越小越紧急 */
    public static final Map<String, Integer> URGENCY_ORDER = Map.of(
            "critical", 0,
            "action", 1,
            "attention", 2,
            "info", 3
    );

    public static final List<String> SENTIMENTS = List.of("positive", "neutral", "negative");

    public static final List<String> URGENCIES = List.of("info", "attention", "action", "critical");

    public static final List<String> SOURCES = List.of(
            "survey", "claim", "service", "social", "appstore", "email"
    );

    public static final List<String> PRODUCT_LINES = List.of(
            "travel", "medical", "accident", "home", "motor", "pet"
    );
}
