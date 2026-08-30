package com.voc.insight.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通知服务（SSE / Server-Sent Events）。
 *
 * 为什么用 SSE 而不是 WebSocket：
 * 本系统只需服务端单向推送（新反馈、预警），客户端不需要反向发消息。
 * SSE 基于 HTTP 长连接，浏览器 EventSource 原生支持且自动重连，
 * 无需处理 WebSocket 握手、协议与心跳，实现成本与运维成本都更低。
 *
 * 生产者调用 push() 向所有已连接客户端广播事件。
 */
@Slf4j
@Service
public class NotifyService {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /**
     * 注册一个 SSE 连接。
     * 超时设为 0（不超时），由心跳与客户端重连维持连接。
     */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** 向所有客户端广播事件 */
    public void push(String event, Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event).data(payload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(builder);
            } catch (Exception e) {
                // 发送失败说明连接已断开，移除
                emitters.remove(emitter);
            }
        }
    }
}
