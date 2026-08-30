import { io, Socket } from 'socket.io-client';

let socket: Socket | null = null;

export function getSocket(): Socket {
  if (!socket) {
    socket = io(window.location.origin, {
      path: '/socket.io',
      transports: ['websocket', 'polling']
    });

    socket.on('connect', () => {
      console.log('Socket connected:', socket?.id);
    });

    socket.on('disconnect', () => {
      console.log('Socket disconnected');
    });

    socket.on('connect_error', (error) => {
      console.error('Socket connection error:', error);
    });
  }

  return socket;
}

export function subscribeToTopics(topics: string[]): void {
  getSocket().emit('subscribe', topics);
}

export function unsubscribeFromTopics(topics: string[]): void {
  getSocket().emit('unsubscribe', topics);
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
  const s = getSocket();
  s.on('feedback:new', callback);
  return () => s.off('feedback:new', callback);
}

export function onAlert(callback: (alert: AlertEvent) => void): () => void {
  const s = getSocket();
  s.on('alert', callback);
  return () => s.off('alert', callback);
}

export function disconnectSocket(): void {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
}
