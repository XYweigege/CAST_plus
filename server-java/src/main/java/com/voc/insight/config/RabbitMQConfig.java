package com.voc.insight.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：异步分析队列。
 * 定时任务 / 手动触发只做采集与投递，耗时的 AI 分析由消费者并发执行。
 */
@Configuration
public class RabbitMQConfig {

    public static final String ANALYZE_EXCHANGE = "voc.analyze.exchange";
    public static final String ANALYZE_QUEUE = "voc.analyze.queue";
    public static final String ANALYZE_ROUTING_KEY = "feedback.analyze";

    @Bean
    public DirectExchange analyzeExchange() {
        return new DirectExchange(ANALYZE_EXCHANGE, true, false);
    }

    @Bean
    public Queue analyzeQueue() {
        return new Queue(ANALYZE_QUEUE, true);
    }

    @Bean
    public Binding analyzeBinding(Queue analyzeQueue, DirectExchange analyzeExchange) {
        return BindingBuilder.bind(analyzeQueue).to(analyzeExchange).with(ANALYZE_ROUTING_KEY);
    }

    /** 消息体统一走 JSON，生产者与监听器容器都会自动使用该转换器 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
