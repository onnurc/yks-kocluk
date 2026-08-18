import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { createStompClient } from "./stompClient";
import type { MessageResponse, PresenceResponse } from "./messagingTypes";

export type SocketStatus = "connecting" | "connected" | "reconnecting" | "disconnected";

interface UseConversationSocketOptions {
  conversationId: number | null;
  onMessage: (message: MessageResponse) => void;
  onPresence: (presence: PresenceResponse) => void;
  /** Called after a successful *re*connect (not the initial connect) — the caller should
   * re-sync history, since the in-memory broker doesn't buffer/replay anything missed
   * while disconnected. */
  onReconnected: () => void;
}

interface UseConversationSocketResult {
  status: SocketStatus;
  /** Publishes over the live STOMP connection. Returns false (does nothing) if not
   * currently connected — the caller is expected to fall back to the REST send path. */
  sendViaSocket: (content: string) => boolean;
}

/**
 * Connects to the backend's STOMP/WebSocket chat layer (WebSocketConfig, ChatStompController)
 * for a single conversation: subscribes to /topic/conversations/{id} for live message
 * broadcast, and exposes a publish helper for /app/conversations/{id}/send. Auth travels in
 * the STOMP CONNECT frame's native Authorization header (StompAuthChannelInterceptor reads it
 * there only — not on SUBSCRIBE/SEND, and not as a query param).
 *
 * Connection lifecycle (transport, backoff, token refresh) lives in createStompClient, shared
 * with useNotificationSocket.
 */
export function useConversationSocket({
  conversationId,
  onMessage,
  onPresence,
  onReconnected,
}: UseConversationSocketOptions): UseConversationSocketResult {
  const [status, setStatus] = useState<SocketStatus>("disconnected");
  const clientRef = useRef<Client | null>(null);

  // Refs so the effect below only re-runs (tearing down and reconnecting) when the
  // conversation actually changes, not on every render that passes a new callback identity.
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;
  const onPresenceRef = useRef(onPresence);
  onPresenceRef.current = onPresence;
  const onReconnectedRef = useRef(onReconnected);
  onReconnectedRef.current = onReconnected;

  useEffect(() => {
    if (!conversationId) return;

    const client = createStompClient({
      onConnect: (activeClient, isReconnect) => {
        setStatus("connected");
        activeClient.subscribe(`/topic/conversations/${conversationId}`, (frame) => {
          try {
            onMessageRef.current(JSON.parse(frame.body) as MessageResponse);
          } catch {
            // Malformed frame — ignore rather than crash the page.
          }
        });
        activeClient.subscribe(`/topic/conversations/${conversationId}/presence`, (frame) => {
          try {
            onPresenceRef.current(JSON.parse(frame.body) as PresenceResponse);
          } catch {
            // Malformed frame — ignore rather than corrupt presence state.
          }
        });
        if (isReconnect) {
          onReconnectedRef.current();
        }
      },
      onWebSocketClose: (hasConnectedOnce) => {
        setStatus(hasConnectedOnce ? "reconnecting" : "connecting");
      },
      onAuthFailure: () => {
        setStatus("disconnected");
      },
    });

    clientRef.current = client;
    setStatus("connecting");
    client.activate();

    return () => {
      clientRef.current = null;
      void client.deactivate();
      setStatus("disconnected");
    };
  }, [conversationId]);

  const sendViaSocket = useCallback(
    (content: string): boolean => {
      const client = clientRef.current;
      if (!client || !client.connected || !conversationId) return false;
      client.publish({
        destination: `/app/conversations/${conversationId}/send`,
        body: content,
      });
      return true;
    },
    [conversationId]
  );

  return { status, sendViaSocket };
}
