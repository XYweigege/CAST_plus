package com.voc.insight.mq;

import com.voc.insight.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 分析任务生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(AnalyzeTaskMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ANALYZE_EXCHANGE,
                RabbitMQConfig.ANALYZE_ROUTING_KEY,
                message);
    }
}
