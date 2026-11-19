import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import type { ConversationDetail, ConversationSummary, OperatorMessage } from './conversations';
import { openStream } from './http-client';
import { readServerSentEvents } from './sse';

const EVENTS_PATH = '/api/v1/conversations/events';
// Waits after a dropped stream, growing up to the last value while the server stays unreachable
const RETRY_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000];
const MIN_HEALTHY_STREAM_MS = 5_000;

/**
 * Keeps conversation queries current while the panel is open: the server pushes every change of the workspace's
 * conversations, and this updates the cached detail and refreshes the lists. The server ends the stream every
 * 15 minutes; it is opened again at once, which also renews the access token when needed.
 */
export function useLiveConversations(enabled: boolean): void {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const controller = new AbortController();
    void listen(queryClient, controller.signal);
    return () => controller.abort();
  }, [enabled, queryClient]);
}

async function listen(queryClient: QueryClient, signal: AbortSignal): Promise<void> {
  let failures = 0;
  while (!signal.aborted) {
    try {
      const response = await openStream(EVENTS_PATH, signal);
      const openedAt = Date.now();
      // Whatever happened while disconnected is fetched again
      void queryClient.invalidateQueries({ queryKey: ['conversations'] });
      for await (const event of readServerSentEvents(response.body!)) {
        apply(queryClient, event.event, event.data);
      }
      // A stream the server ends at once (e.g. a proxy that does not stream) must not become a tight loop
      failures = Date.now() - openedAt < MIN_HEALTHY_STREAM_MS ? failures + 1 : 0;
    } catch {
      if (signal.aborted) {
        return;
      }
      failures++;
    }
    if (failures > 0) {
      await wait(RETRY_DELAYS_MS[Math.min(failures, RETRY_DELAYS_MS.length) - 1]!, signal);
    }
  }
}

export function apply(queryClient: QueryClient, event: string, data: string): void {
  if (event === 'conversation') {
    const summary = JSON.parse(data) as ConversationSummary;
    queryClient.setQueryData<ConversationDetail>(['conversation', summary.id], (detail) =>
      detail ? { ...detail, conversation: summary } : detail,
    );
    void queryClient.invalidateQueries({ queryKey: ['conversations'] });
  } else if (event === 'message') {
    const { conversationId, message } = JSON.parse(data) as { conversationId: string; message: OperatorMessage };
    queryClient.setQueryData<ConversationDetail>(['conversation', conversationId], (detail) =>
      detail && !detail.messages.some((known) => known.id === message.id)
        ? { ...detail, messages: [...detail.messages, message] }
        : detail,
    );
  }
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}
