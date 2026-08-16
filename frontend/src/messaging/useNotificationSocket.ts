import { useEffect, useRef } from "react";
import { createStompClient } from "./stompClient";
import type { UserNotificationResponse } from "./messagingTypes";

/**
 * Event re-broadcast on `window` for every incoming user notification, carrying the
 * UserNotificationResponse as its detail. Follows the existing "messages-read" convention rather
 * than introducing a context: the listeners (AppLayout's badge, MessagesPage's list) don't share
 * a subtree, and one CustomEvent is far less machinery than a provider wrapping the router.
 */
export const MESSAGE_NOTIFICATION_EVENT = "message-notification";

interface UseNotificationSocketOptions {
  /** Pass false when there's nothing to listen for (logged out, or an ADMIN — admins observe
   * conversations from the panel and have no inbox badge). No connection is opened. */
  enabled: boolean;
  onNotification: (notification: UserNotificationResponse) => void;
}

/**
 * Subscribes to the user-scoped notification channel (`/user/queue/notifications`) for as long as
 * the app is mounted — this is what makes the unread badge live on every page, not just the one
 * conversation useConversationSocket happens to have open.
 *
 * <p>Deliberately its own connection rather than a shared client: while a conversation is open the
 * user holds two WebSocket connections, which costs one extra CONNECT frame and is well inside
 * every browser's per-host limit. Threading a single client through both consumers would mean a
 * provider around the router and a rewrite of the conversation hook's lifecycle — the part that
 * was hardest to get right and is verified end to end.
 *
 * <p>The server is the sole authority on the unread total: it recomputes and pushes it with every
 * notification, so nothing here increments a counter that could drift across tabs or reconnects.
 */
export function useNotificationSocket({ enabled, onNotification }: UseNotificationSocketOptions): void {
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;

  useEffect(() => {
    if (!enabled) return;

    const client = createStompClient({
      onConnect: (activeClient) => {
        activeClient.subscribe("/user/queue/notifications", (frame) => {
          try {
            const notification = JSON.parse(frame.body) as UserNotificationResponse;
            onNotificationRef.current(notification);
            window.dispatchEvent(
              new CustomEvent<UserNotificationResponse>(MESSAGE_NOTIFICATION_EVENT, {
                detail: notification,
              })
            );
          } catch {
            // Malformed frame — ignore rather than crash the shell that wraps every page.
          }
        });
      },
      // Nothing user-visible hangs off this connection's status (the badge just goes stale until
      // it reconnects), so there is no status state to keep — the client retries on its own.
      onWebSocketClose: () => {},
      onAuthFailure: () => {},
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [enabled]);
}
