package com.voc.insight.mq;

import com.voc.insight.dto.FeedbackInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 异步分析任务消息。
 * 一条消息 = 一条反馈 × 一个主题词，消费者执行预匹配 + AI 分析 + 归属 + 落库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeTaskMessage implements Serializable {

    /** 目标主题词 ID */
    private String topicId;

    /** 待分析反馈 */
    private FeedbackInput feedback;
}
