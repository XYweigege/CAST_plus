package com.voc.insight.mq;

import com.voc.insight.config.RabbitMQConfig;
import com.voc.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 归因任务消费者。
 * 并发消费（并发数见 application.yml spring.rabbitmq.listener.simple），
 * 读库 → 耗时的 AI 归因在这里执行 → 写回同一行，失败由重试配置兜底（3 次后丢弃并记日志）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeTaskConsumer {

    private final InsightService insightService;

    @RabbitListener(queues = RabbitMQConfig.ANALYZE_QUEUE)
    public void onMessage(AnalyzeTaskMessage message) {
        try {
            insightService.processAnalyzeTask(message.getFeedbackId());
        } catch (Exception e) {
            log.error("归因任务消费失败 (feedbackId={}): {}", message.getFeedbackId(), e.getMessage());
            throw e; // 抛出让容器按重试策略处理
        }
    }
}
