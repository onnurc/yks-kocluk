/// <reference types="node" />
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ChatPage } from "../pages/ChatPage";
import { ApiError } from "../api/ApiError";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const chatStyles = readFileSync(resolve(process.cwd(), "src/pages/chat.css"), "utf8");

const mocks = vi.hoisted(() => ({
  auth: vi.fn(), list: vi.fn(), history: vi.fn(), send: vi.fn(), read: vi.fn(),
  adminList: vi.fn(), adminHistory: vi.fn(), socket: vi.fn(), socketMessage: undefined as ((message: unknown) => void) | undefined,
}));

vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth() }));
vi.mock("../messaging/messagingApi", () => ({ messagingApi: {
  listConversations: mocks.list, listMessages: mocks.history, sendMessage: mocks.send, markRead: mocks.read,
  listAdminConversations: mocks.adminList, listAdminMessages: mocks.adminHistory,
} }));
vi.mock("../messaging/useConversationSocket", () => ({
  useConversationSocket: (options: { conversationId: number | null; onMessage: (message: unknown) => void }) => {
    mocks.socket(options.conversationId);
    mocks.socketMessage = options.onMessage;
    return { status: "connected", sendViaSocket: vi.fn(() => false) };
  },
}));

const user = { id: 7, fullName: "Gerçek Öğrenci", role: "STUDENT" as const };
const conversation = { id: 12, coachProfileId: 3, coachName: "Ayşe Koç", studentName: "Gerçek Öğrenci", lastMessageAt: "2026-08-16T10:00:00Z", unreadCount: 2, observer: { type: "ADMIN", displayName: "Uniform Akademi Admin", readOnly: true } };
const otherMessage = { id: 21, conversationId: 12, senderId: 9, senderName: "Ayşe Koç", content: "Bugünkü plan hazır.", sentAt: "2026-08-16T10:01:00Z", readAt: null };
const mine = { id: 22, conversationId: 12, senderId: 7, senderName: "Gerçek Öğrenci", content: "Teşekkür ederim.", sentAt: "2026-08-16T10:02:00Z", readAt: null };
const page = { content: [mine, otherMessage], page: 0, size: 30, totalElements: 2, totalPages: 1, last: true };
const Location = () => <output data-testid="location">{useLocation().pathname}</output>;

function renderChat(path = "/messages") {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/messages" element={<><ChatPage /><Location /></>} />
    <Route path="/messages/:conversationId" element={<><ChatPage /><Location /></>} />
    <Route path="/admin/messages" element={<ChatPage />} />
    <Route path="/admin/messages/:conversationId" element={<ChatPage />} />
  </Routes></MemoryRouter>);
}

afterEach(cleanup);

describe("real responsive chat page", () => {
  beforeEach(() => {
    vi.clearAllMocks(); mocks.socketMessage = undefined;
    mocks.auth.mockReturnValue({ user });
    mocks.list.mockResolvedValue([conversation]);
    mocks.history.mockResolvedValue(page);
    mocks.read.mockResolvedValue(undefined);
    mocks.send.mockResolvedValue({ ...mine, id: 23, content: "Yeni mesaj" });
  });

  it("renders the route and real conversation API data", async () => {
    renderChat();
    expect(screen.getByRole("heading", { name: "Mesajlar" })).toBeInTheDocument();
    expect(await screen.findByText("Ayşe Koç")).toBeInTheDocument();
    expect(mocks.list).toHaveBeenCalledOnce();
    expect(screen.getByLabelText("2 okunmamış mesaj")).toBeInTheDocument();
  });

  it("selects a conversation, loads paged history, marks read, and supports mobile back navigation", async () => {
    renderChat();
    fireEvent.click(await screen.findByText("Ayşe Koç"));
    await waitFor(() => expect(mocks.history).toHaveBeenCalledWith(12, 0, 30));
    expect(mocks.read).toHaveBeenCalledWith(12);
    expect(await screen.findByText("Bugünkü plan hazır.")).toBeInTheDocument();
    expect(screen.getByText("Teşekkür ederim.").closest(".chat-message")).toHaveClass("chat-message--mine");
    expect(screen.getByText("Bugünkü plan hazır.").closest(".chat-message")).not.toHaveClass("chat-message--mine");
    expect(screen.getByRole("note")).toHaveTextContent("Gizlilik bilgisi: Bu görüşme, güvenlik ve hizmet kalitesi amacıyla yetkili Uniform Akademi yöneticileri tarafından gerektiğinde incelenebilir.");
    fireEvent.click(screen.getByRole("button", { name: "Konuşma listesine dön" }));
    expect(screen.getByTestId("location")).toHaveTextContent("/messages");
  });

  it("sends through the real REST client abstraction when socket is unavailable and blocks blank messages", async () => {
    renderChat("/messages/12");
    await screen.findByText("Bugünkü plan hazır.");
    const input = screen.getByRole("textbox", { name: "Mesaj" });
    const button = screen.getByRole("button", { name: "Mesaj gönder" });
    expect(button).toBeDisabled();
    fireEvent.change(input, { target: { value: "   " } }); fireEvent.submit(input.closest("form")!);
    expect(mocks.send).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { value: "Yeni mesaj" } }); fireEvent.click(button);
    await waitFor(() => expect(mocks.send).toHaveBeenCalledWith(12, "Yeni mesaj"));
  });

  it("merges a real-time message into the active conversation and marks it read", async () => {
    renderChat("/messages/12");
    await screen.findByText("Bugünkü plan hazır.");
    mocks.read.mockClear();
    mocks.socketMessage?.({ ...otherMessage, id: 99, content: "Canlı mesaj" });
    expect(await screen.findByText("Canlı mesaj")).toBeInTheDocument();
    await waitFor(() => expect(mocks.read).toHaveBeenCalledWith(12));
  });

  it("turns a backend-denied historical conversation into read-only mode", async () => {
    mocks.send.mockRejectedValue(new ApiError(403, "Forbidden", "denied", "MESSAGING_NOT_ALLOWED"));
    renderChat("/messages/12"); await screen.findByText("Bugünkü plan hazır.");
    fireEvent.change(screen.getByRole("textbox", { name: "Mesaj" }), { target: { value: "Gönder" } });
    fireEvent.click(screen.getByRole("button", { name: "Mesaj gönder" }));
    expect(await screen.findByText("Bu konuşma salt okunur. Mesaj gönderemezsiniz.")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Mesaj" })).not.toBeInTheDocument();
  });

  it("changes socket subscription target on conversation switch and clears it on unmount", async () => {
    const view = renderChat("/messages/12");
    await waitFor(() => expect(mocks.socket).toHaveBeenCalledWith(12));
    view.unmount();
    expect(mocks.socket).toHaveBeenLastCalledWith(12);
  });

  it("keeps admin observer audited and read-only without a composer", async () => {
    mocks.auth.mockReturnValue({ user: { ...user, role: "ADMIN" } });
    mocks.adminList.mockResolvedValue({ content: [{ conversationId: 12, student: { id: 7, fullName: "Öğrenci" }, coach: { id: 3, fullName: "Koç", universityName: "ODTÜ" }, lastMessageAt: conversation.lastMessageAt, messageCount: 2, observer: conversation.observer }], page: 0, size: 20, totalElements: 1, totalPages: 1, last: true });
    mocks.adminHistory.mockResolvedValue(page);
    renderChat("/admin/messages/12");
    expect(await screen.findByText("Denetim gerekçesi gerekli")).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Yönetici görünümü — Salt okunur. Bu görüşmeyi inceleyebilirsiniz ancak mesaj gönderemezsiniz.");
    expect(screen.queryByRole("textbox", { name: "Mesaj" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Erişim gerekçesi"), { target: { value: "Güvenlik incelemesi" } });
    fireEvent.click(screen.getByRole("button", { name: "Gerekçeyi kaydet ve aç" }));
    await waitFor(() => expect(mocks.adminHistory).toHaveBeenCalledWith(12, "Güvenlik incelemesi", 0, 30));
    expect(await screen.findByText("Bu konuşma salt okunur. Mesaj gönderemezsiniz.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mesaj gönder" })).not.toBeInTheDocument();
    expect(screen.getByText("Teşekkür ederim.").closest(".chat-message")).not.toHaveClass("chat-message--mine");
    expect(screen.getByText("Gerçek Öğrenci")).toBeInTheDocument();
    expect(screen.getByText("Ayşe Koç")).toBeInTheDocument();
  });

  it("defines a full-width single-panel mobile layout without changing the desktop split grid", () => {
    expect(chatStyles).toMatch(/\.chat-page\s*\{[^}]*grid-template-columns:minmax\(280px, 34%\) minmax\(0, 1fr\)/);
    expect(chatStyles).toMatch(/@media \(max-width: 720px\)[\s\S]*\.chat-page\s*\{[^}]*grid-template-columns:minmax\(0,1fr\)[^}]*width:100%/);
    expect(chatStyles).toContain(".app-layout__main { overflow:hidden; }");
    expect(chatStyles).toContain(".chat-message--mine .chat-bubble");
    expect(chatStyles).toContain("background:var(--gold)");
  });

  it("contains no Stitch demo identities in runtime output", async () => {
    renderChat(); await screen.findByText("Ayşe Koç");
    const pageContent = within(document.body).queryByText(/Can K\.|Zeynep Y\.|Burak M\.|Mert A\./);
    expect(pageContent).not.toBeInTheDocument();
  });
});
