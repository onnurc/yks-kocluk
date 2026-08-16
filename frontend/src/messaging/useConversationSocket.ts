import { useCallback, useEffect, useRef, useState } from "react";
import { Client, ReconnectionTimeMode } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getApiBaseUrl, ensureFreshToken } from "../api/httpClient";
import { getAccessToken } from "../auth/tokenStorage";
import type { MessageResponse } from "./messagingTypes";

export type SocketStatus = "connecting" | "connected" | "reconnecting" | "disconnected";

const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;

// In dev, go through the Vite proxy (same origin as the page) rather than straight to
// VITE_API_BASE_URL — see the proxy comment in vite.config.ts for why: sockjs-client's /info
// precheck can't be made to skip withCredentials on a cross-origin URL, which the backend's
// CORS config (deliberately allowCredentials=false) then rejects. A production build has no
// dev proxy, so it connects directly; that deployment's CORS/origin setup is a separate,
// deliberate decision for whoever configures it, not something to route around here.
const getSocketBaseUrl = (): string => (import.meta.env.DEV ? window.location.origin : getApiBaseUrl());

interface UseConversationSocketOptions {
  conversationId: number | null;
  onMessage: (message: MessageResponse) => void;
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
 */
export function useConversationSocket({
  conversationId,
  onMessage,
  onReconnected,
}: UseConversationSocketOptions): UseConversationSocketResult {
  const [status, setStatus] = useState<SocketStatus>("disconnected");
  const clientRef = useRef<Client | null>(null);

  // Refs so the effect below only re-runs (tearing down and reconnecting) when the
  // conversation actually changes, not on every render that passes a new callback identity.
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;
  const onReconnectedRef = useRef(onReconnected);
  onReconnectedRef.current = onReconnected;

  useEffect(() => {
    if (!conversationId) return;

    let hasConnectedOnce = false;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${getSocketBaseUrl()}/ws`),
      // stompjs's own exponential backoff (doubles reconnectDelay up to maxReconnectDelay)
      // rather than a hand-rolled timer.
      reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      // Runs before the initial connect and every automatic reconnect — re-reads the token
      // from storage each time so a refresh (triggered below, or by any unrelated REST call
      // going through httpClient's central 401 handling) is picked up on the next attempt.
      beforeConnect: () => {
        client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ""}` };
      },
      onConnect: () => {
        setStatus("connected");
        client.subscribe(`/topic/conversations/${conversationId}`, (frame) => {
          try {
            onMessageRef.current(JSON.parse(frame.body) as MessageResponse);
          } catch {
            // Malformed frame — ignore rather than crash the page.
          }
        });
        if (hasConnectedOnce) {
          onReconnectedRef.current();
        }
        hasConnectedOnce = true;
      },
      onWebSocketClose: () => {
        setStatus(hasConnectedOnce ? "reconnecting" : "connecting");
      },
      onStompError: () => {
        // The most likely cause of a rejected CONNECT is an expired access token — refresh
        // through the same shared mechanism httpClient's 401 handling uses (a single in-flight
        // refresh serves every caller). If the refresh token itself is dead, ensureFreshToken
        // tears down the session and fires "session-expired" (AuthProvider redirects to
        // /login) — stop retrying here rather than spinning against a permanently dead session.
        void ensureFreshToken().then((token) => {
          if (!token) {
            void client.deactivate();
            setStatus("disconnected");
          }
        });
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
