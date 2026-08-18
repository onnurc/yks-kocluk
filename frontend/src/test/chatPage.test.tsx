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
  socketPresence: undefined as ((presence: unknown) => void) | undefined,
  socketReconnect: undefined as (() => void) | undefined,
}));

vi.mock("../auth/AuthProvider", () => ({ useAuth: () => mocks.auth() }));
vi.mock("../messaging/messagingApi", () => ({ messagingApi: {
  listConversations: mocks.list, listMessages: mocks.history, sendMessage: mocks.send, markRead: mocks.read,
  listAdminConversations: mocks.adminList, listAdminMessages: mocks.adminHistory,
} }));
vi.mock("../messaging/useConversationSocket", () => ({
  useConversationSocket: (options: { conversationId: number | null; onMessage: (message: unknown) => void; onPresence: (presence: unknown) => void; onReconnected: () => void }) => {
    mocks.socket(options.conversationId);
    mocks.socketMessage = options.onMessage;
    mocks.socketPresence = options.onPresence;
    mocks.socketReconnect = options.onReconnected;
    return { status: "connected", sendViaSocket: vi.fn(() => false) };
  },
}));

const user = { id: 7, fullName: "Gerçek Öğrenci", role: "STUDENT" as const };
const conversation = { id: 12, coachProfileId: 3, coachName: "Ayşe Koç", studentName: "Gerçek Öğrenci", lastMessage: "Gerçek son mesaj", lastMessageAt: "2026-08-16T10:00:00Z", unreadCount: 2, counterpartUserId: 9, counterpartOnline: false, observer: { type: "ADMIN", displayName: "Uniform Akademi Admin", readOnly: true } };
const emptyConversation = { ...conversation, id: 13, coachName: "Boş Koç", lastMessage: null, lastMessageAt: null, unreadCount: 0, counterpartUserId: 10 };
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
    vi.clearAllMocks(); mocks.socketMessage = undefined; mocks.socketPresence = undefined; mocks.socketReconnect = undefined;
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
    expect(screen.getByText("Gerçek son mesaj")).toBeInTheDocument();
    expect(screen.queryByText("Konuşmayı aç")).not.toBeInTheDocument();
  });

  it("renders the empty preview fallback and keeps long previews in the truncating element", async () => {
    const longPreview = "Bu, konuşma kartının genişliğini bozmaması gereken oldukça uzun bir gerçek son mesaj önizlemesidir.";
    mocks.list.mockResolvedValue([emptyConversation, { ...conversation, id: 14, coachName: "Uzun Koç", lastMessage: longPreview }]);
    renderChat();
    expect(await screen.findByText("Henüz mesaj yok")).toBeInTheDocument();
    const preview = screen.getByTitle(longPreview);
    expect(preview).toHaveClass("chat-list-item__preview");
    expect(chatStyles).toMatch(/\.chat-list-item__preview\s*\{[^}]*text-overflow:ellipsis/);
  });

  it("selects a conversation, loads paged history, marks read, and supports mobile back navigation", async () => {
    renderChat();
    fireEvent.click(await screen.findByText("Ayşe Koç"));
    await waitFor(() => expect(mocks.history).toHaveBeenCalledWith(12, 0, 30));
    expect(mocks.read).toHaveBeenCalledWith(12);
    expect(await screen.findByText("Bugünkü plan hazır.")).toBeInTheDocument();
    expect(screen.getByText("Teşekkür ederim.").closest(".chat-message")).toHaveClass("chat-message--mine");
    expect(screen.getByText("Bugünkü plan hazır.").closest(".chat-message")).not.toHaveClass("chat-message--mine");
    expect(screen.getByRole("note")).toHaveTextContent(/^Mesajlar yöneticiler tarafından görüntülenebilir\.$/);
    expect(screen.queryByText(/Gizlilik bilgisi:/)).not.toBeInTheDocument();
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
    expect((await screen.findAllByText("Canlı mesaj")).length).toBeGreaterThanOrEqual(2);
    await waitFor(() => expect(mocks.read).toHaveBeenCalledWith(12));
  });

  it("updates the real preview and timestamp from the existing live message event", async () => {
    renderChat("/messages/12");
    await screen.findByText("Gerçek son mesaj");
    const sentAt = new Date().toISOString();
    mocks.socketMessage?.({ ...otherMessage, id: 100, content: "Yeni gerçek önizleme", sentAt });
    const previews = await screen.findAllByText("Yeni gerçek önizleme");
    expect(previews.some((node) => node.classList.contains("chat-list-item__preview"))).toBe(true);
    expect(document.querySelector(".chat-list-item time")).toHaveTextContent(
      new Date(sentAt).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" })
    );
  });

  it("shows counterpart presence from backend state, not own socket connection, and updates live", async () => {
    renderChat("/messages/12");
    expect(await screen.findByText("Çevrim dışı")).toBeInTheDocument();
    expect(screen.queryByText("Bağlı")).not.toBeInTheDocument();
    mocks.socketPresence?.({ userId: 9, online: true });
    expect(await screen.findByText("Çevrim içi")).toBeInTheDocument();
    mocks.socketPresence?.({ userId: 7, online: false });
    expect(screen.getByText("Çevrim içi")).toBeInTheDocument();
  });

  it("resynchronizes backend presence on reconnect instead of assuming online", async () => {
    renderChat("/messages/12");
    await screen.findByText("Çevrim dışı");
    mocks.list.mockResolvedValue([{ ...conversation, counterpartOnline: true }]);
    mocks.socketReconnect?.();
    expect(await screen.findByText("Çevrim içi")).toBeInTheDocument();
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
