/**
 * 实时推送通道（SSE / Server-Sent Events）。
 * Java 后端通过 GET /api/notify/stream 单向广播，浏览器 EventSource 自动重连。
 * 导出的函数签名与原 Socket.IO 版本保持一致，调用方无需改动。
 *
 * 认证：EventSource 无法设置自定义请求头，token 通过 URL 查询参数传递。
 */
import { getToken } from './auth';

let eventSource: EventSource | null = null;

function getEventSource(): EventSource {
  if (!eventSource || eventSource.readyState === EventSource.CLOSED) {
    const token = getToken();
    const url = token
      ? `/api/notify/stream?token=${encodeURIComponent(token)}`
      : '/api/notify/stream';
    eventSource = new EventSource(url);

    eventSource.onopen = () => {
      console.log('SSE connected');
    };

    eventSource.onerror = () => {
      // EventSource 会自动重连，仅记录
      console.warn('SSE connection error, retrying...');
    };
  }
  return eventSource;
}

function addListener<T>(event: string, callback: (payload: T) => void): () => void {
  const es = getEventSource();
  const handler = (e: MessageEvent) => {
    try {
      callback(JSON.parse(e.data));
    } catch {
      // 忽略无法解析的消息
    }
  };
  es.addEventListener(event, handler);
  return () => es.removeEventListener(event, handler);
}

/** SSE 为全量广播，无订阅概念，保留空实现兼容原调用 */
export function subscribeToTopics(_topics: string[]): void {
  getEventSource(); // 确保连接已建立
}

export function unsubscribeFromTopics(_topics: string[]): void {
  // no-op
}

export interface FeedbackEvent {
  id: string;
  content: string;
  urgency: string;
  sentiment: string;
  aiSummary: string | null;
}

export interface AlertEvent {
  id: string;
  title: string;
  content: string;
  urgency: string;
  feedbackId?: string;
}

export function onNewFeedback(callback: (feedback: FeedbackEvent) => void): () => void {
  return addListener<FeedbackEvent>('feedback:new', callback);
}

export function onAlert(callback: (alert: AlertEvent) => void): () => void {
  return addListener<AlertEvent>('alert', callback);
}

export function disconnectSocket(): void {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}
