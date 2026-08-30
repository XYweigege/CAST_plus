package com.voc.insight.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.common.PageResult;
import com.voc.insight.dto.AnalyzeDTO;
import com.voc.insight.dto.FeedbackQueryDTO;
import com.voc.insight.dto.ReviewDTO;
import com.voc.insight.entity.Feedback;
import com.voc.insight.vo.FeedbackStatsVO;
import com.voc.insight.vo.InsightReport;

/**
 * 客户反馈服务。
 */
public interface FeedbackService extends IService<Feedback> {

    /** 多维筛选 + 排序 + 分页 */
    PageResult<Feedback> page(FeedbackQueryDTO query);

    /** 概览统计 */
    FeedbackStatsVO stats();

    /** 评分归因报告 */
    InsightReport insight(String productLine);

    /** 单条文本即时分析（不落库，用于验证 Prompt 效果） */
    FeedbackAnalysis analyze(AnalyzeDTO dto);

    /** 导入外部反馈（CSV / JSON） */
    int importData(String content, String format);

    /** 生成演示数据 */
    int generateDemo(Integer count);

    /** 人工复核 */
    Feedback review(String id, ReviewDTO dto);
}
