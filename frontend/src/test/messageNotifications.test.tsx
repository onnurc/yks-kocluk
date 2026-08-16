import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppLayout } from "../components/AppLayout";
import { MessagesPage } from "../pages/MessagesPage";
import { MESSAGE_NOTIFICATION_EVENT } from "../messaging/useNotificationSocket";
import { TestAuthProvider } from "./TestAuthProvider";
import type { UserNotificationResponse } from "../messaging/messagingTypes";

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  getDashboardData: vi.fn(),
  subscribe: vi.fn(),
  activate: vi.fn(),
  deactivate: vi.fn(),
  createStompClient: vi.fn(),
}));

vi.mock("../messaging/messagingApi", () => ({
  messagingApi: { listConversations: mocks.listConversations },
}));
vi.mock("../studentDashboard/studentDashboardApi", () => ({
  studentDashboardApi: { getDashboardData: mocks.getDashboardData },
}));
vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: () => ({ documents: {}, loading: false, error: null, reload: vi.fn() }),
}));
// Stand in for the real STOMP client: capture the handlers the hook registers so a server push
// can be simulated without a broker.
vi.mock("../messaging/stompClient", () => ({
  createStompClient: mocks.createStompClient,
}));

const student = {
  id: 1, email: "student@example.com", fullName: "Student", role: "STUDENT" as const,
  status: "ACTIVE" as const, emailVerified: true, legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

const conversation = (unreadCount: number) => ({
  id: 12,
  studentId: 1,
  studentName: "Student",
  coachProfileId: 5,
  coachName: "Koç Ayşe",
  lastMessageAt: "2026-08-16T10:00:00Z",
  unreadCount,
  observer: { type: "ADMIN" as const, displayName: "Platform Yöneticisi", readOnly: true as const },
});

/** Delivers a frame on the subscription the hook opened, as the server would. */
const pushNotification = async (notification: UserNotificationResponse) => {
  const [, handler] = mocks.subscribe.mock.calls.at(-1)!;
  await act(async () => {
    handler({ body: JSON.stringify(notification) });
  });
};

afterEach(cleanup);

describe("live message notifications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([conversation(0)]);
    mocks.getDashboardData.mockResolvedValue({ subscription: { status: "ACTIVE" } });
    mocks.createStompClient.mockImplementation(({ onConnect }: any) => {
      const client = { subscribe: mocks.subscribe, activate: mocks.activate, deactivate: mocks.deactivate };
      // Connect synchronously on activate, the way a live broker would shortly after.
      mocks.activate.mockImplementation(() => onConnect(client, false));
      return client;
    });
  });

  it("subscribes to the user queue and updates the badge from the pushed total", async () => {
    render(
      <TestAuthProvider value={{ user: student, isAuthenticated: true }}>
        <MemoryRouter><AppLayout /></MemoryRouter>
      </TestAuthProvider>
    );

    await waitFor(() => expect(mocks.subscribe).toHaveBeenCalled());
    expect(mocks.subscribe.mock.calls[0][0]).toBe("/user/queue/notifications");

    await pushNotification({ type: "NEW_MESSAGE", conversationId: 12, unreadTotal: 3 });

    expect(await screen.findByText("3")).toBeInTheDocument();
  });

  it("assigns the server total rather than incrementing, so two pushes cannot drift", async () => {
    render(
      <TestAuthProvider value={{ user: student, isAuthenticated: true }}>
        <MemoryRouter><AppLayout /></MemoryRouter>
      </TestAuthProvider>
    );
    await waitFor(() => expect(mocks.subscribe).toHaveBeenCalled());

    await pushNotification({ type: "NEW_MESSAGE", conversationId: 12, unreadTotal: 2 });
    await pushNotification({ type: "NEW_MESSAGE", conversationId: 12, unreadTotal: 3 });

    // Incrementing would show 5 here.
    expect(await screen.findByText("3")).toBeInTheDocument();
  });

  it("opens no connection for a user with no inbox", async () => {
    render(
      <TestAuthProvider value={{ user: { ...student, role: "ADMIN" as const }, isAuthenticated: true }}>
        <MemoryRouter><AppLayout /></MemoryRouter>
      </TestAuthProvider>
    );

    await waitFor(() => expect(mocks.createStompClient).not.toHaveBeenCalled());
  });

  it("refetches the inbox when a notification arrives, and shows the per-conversation badge", async () => {
    render(
      <TestAuthProvider value={{ user: student, isAuthenticated: true }}>
        <MemoryRouter><MessagesPage /></MemoryRouter>
      </TestAuthProvider>
    );
    await waitFor(() => expect(mocks.listConversations).toHaveBeenCalledTimes(1));

    mocks.listConversations.mockResolvedValue([conversation(4)]);
    await act(async () => {
      window.dispatchEvent(
        new CustomEvent(MESSAGE_NOTIFICATION_EVENT, {
          detail: { type: "NEW_MESSAGE", conversationId: 12, unreadTotal: 4 },
        })
      );
    });

    // unreadCount has been on the DTO all along and was never rendered — this is the badge.
    expect(await screen.findByLabelText("4 okunmamış mesaj")).toBeInTheDocument();
    // A live refresh must not blank the list out behind a loading state.
    expect(screen.queryByText("Mesajlaşmalarınız yükleniyor...")).not.toBeInTheDocument();
  });
});
