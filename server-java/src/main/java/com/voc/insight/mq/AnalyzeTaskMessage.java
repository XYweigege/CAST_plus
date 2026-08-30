package com.voc.insight.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 异步归因任务消息。
 * 一条消息 = 一条已落库的待归因反馈，消费者读库 → AI 归因 → UPDATE 写回同一行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeTaskMessage implements Serializable {

    /** 待归因反馈 ID（feedback 表主键） */
    private String feedbackId;
}
