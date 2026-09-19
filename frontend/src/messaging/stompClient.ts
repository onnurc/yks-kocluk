import { Client, ReconnectionTimeMode } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getApiBaseUrl, ensureFreshToken } from "../api/httpClient";
import { getAccessToken } from "../auth/tokenStorage";

const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;

// In dev, go through the Vite proxy (same origin as the page) rather than straight to
// VITE_API_BASE_URL — sockjs-client's /info precheck always sends withCredentials on a
// cross-origin URL and has no option to disable it. The backend permits that today
// (SecurityConfig's corsConfigurationSource sets allowCredentials=true against an exact
// origin allowlist defaulting to http://localhost:5173), so the proxy predates the current
// CORS setup rather than being required by it. A production build has no proxy and connects
// directly, so the deployed frontend origin must be listed in CORS_ALLOWED_ORIGINS.
const getSocketBaseUrl = (): string => (import.meta.env.DEV ? window.location.origin : getApiBaseUrl());

interface CreateStompClientOptions {
  /** Runs on every successful connect. `isReconnect` is false only for the very first one. */
  onConnect: (client: Client, isReconnect: boolean) => void;
  onWebSocketClose: (hasConnectedOnce: boolean) => void;
  /** The session is gone for good: the client has been deactivated and will not retry. */
  onAuthFailure: () => void;
}

/**
 * Builds a configured STOMP client: SockJS transport, exponential reconnect backoff, a token
 * re-read before every (re)connect attempt, and the expired-token recovery path.
 *
 * <p>Extracted verbatim from useConversationSocket when a second consumer appeared
 * (useNotificationSocket) — the connection lifecycle is the subtle part of that hook and is not
 * worth maintaining in two copies. Behaviour is unchanged; only the per-connection work
 * (subscribing, status reporting) stays with the callers.
 *
 * <p>The client is returned inactive: callers `activate()` it and must `deactivate()` on cleanup.
 */
export function createStompClient({
  onConnect,
  onWebSocketClose,
  onAuthFailure,
}: CreateStompClientOptions): Client {
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
      onConnect(client, hasConnectedOnce);
      hasConnectedOnce = true;
    },
    onWebSocketClose: () => {
      onWebSocketClose(hasConnectedOnce);
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
          onAuthFailure();
        }
      });
    },
  });

  return client;
}
