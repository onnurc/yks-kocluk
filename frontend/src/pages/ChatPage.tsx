import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { useAuth } from "../auth/AuthProvider";
import { messagingApi } from "../messaging/messagingApi";
import type { AdminConversationSummary, ConversationResponse, MessageResponse, PresenceResponse } from "../messaging/messagingTypes";
import { useConversationSocket } from "../messaging/useConversationSocket";
import "./chat.css";

const HISTORY_PAGE_SIZE = 30;

type ChatListItem = {
  id: number;
  name: string;
  detail?: string;
  lastMessage: string;
  lastMessageAt: string | null;
  unreadCount: number;
  messageCount?: number;
  counterpartUserId?: number;
  counterpartOnline?: boolean;
};

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");

const safeError = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    if (error.status === 403) return "Bu konuşmaya erişim yetkiniz bulunmuyor.";
    if (error.status === 404) return "Konuşma bulunamadı.";
  }
  return fallback;
};

const formatListTime = (value: string | null) => {
  if (!value) return "";
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
  const yesterday = new Date(now); yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return "Dün";
  return date.toLocaleDateString("tr-TR", { day: "numeric", month: "short" });
};

const dayLabel = (value: string) => {
  const date = new Date(value);
  const today = new Date();
  if (date.toDateString() === today.toDateString()) return "Bugün";
  return date.toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: date.getFullYear() === today.getFullYear() ? undefined : "numeric" });
};

export const ChatPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId?: string }>();
  const selectedId = conversationId ? Number(conversationId) : null;
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const basePath = isAdmin ? "/admin/messages" : "/messages";
  const [conversations, setConversations] = useState<ChatListItem[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [hasOlder, setHasOlder] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [readOnly, setReadOnly] = useState(isAdmin);
  const [adminReason, setAdminReason] = useState("");
  const [approvedReason, setApprovedReason] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const sendTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const active = conversations.find((item) => item.id === selectedId) ?? null;
  const filtered = useMemo(() => conversations.filter((item) => item.name.toLocaleLowerCase("tr-TR").includes(query.trim().toLocaleLowerCase("tr-TR"))), [conversations, query]);

  const mergeMessages = useCallback((incoming: MessageResponse[]) => {
    setMessages((current) => {
      const byId = new Map(current.map((message) => [message.id, message]));
      incoming.forEach((message) => byId.set(message.id, message));
      return [...byId.values()].sort((a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime());
    });
  }, []);

  const loadList = useCallback(async () => {
    setListLoading(true); setListError(null);
    try {
      if (isAdmin) {
        const page = await messagingApi.listAdminConversations();
        setConversations(page.content.map((conversation: AdminConversationSummary) => ({
          id: conversation.conversationId,
          name: `${conversation.student.fullName} — ${conversation.coach.fullName}`,
          detail: conversation.coach.universityName,
          lastMessage: `${conversation.messageCount} mesaj`,
          lastMessageAt: conversation.lastMessageAt,
          unreadCount: 0,
          messageCount: conversation.messageCount,
        })));
      } else {
        const list = await messagingApi.listConversations();
        setConversations(list.map((conversation: ConversationResponse) => ({
          id: conversation.id,
          name: user?.role === "COACH" ? conversation.studentName : conversation.coachName,
          lastMessage: conversation.lastMessage?.trim() || "Henüz mesaj yok",
          lastMessageAt: conversation.lastMessageAt,
          unreadCount: conversation.unreadCount,
          counterpartUserId: conversation.counterpartUserId,
          counterpartOnline: conversation.counterpartOnline,
        })));
      }
    } catch (error) { setListError(safeError(error, "Konuşmalar yüklenemedi. Lütfen tekrar deneyin.")); }
    finally { setListLoading(false); }
  }, [isAdmin, user?.role]);

  useEffect(() => { void loadList(); }, [loadList]);

  const loadHistory = useCallback(async (page: number, append: boolean, reason = approvedReason) => {
    if (!selectedId || (isAdmin && !reason)) return;
    setHistoryLoading(true); setHistoryError(null);
    try {
      const result = isAdmin
        ? await messagingApi.listAdminMessages(selectedId, reason!, page, HISTORY_PAGE_SIZE)
        : await messagingApi.listMessages(selectedId, page, HISTORY_PAGE_SIZE);
      const chronological = [...result.content].reverse();
      if (append) mergeMessages(chronological); else setMessages(chronological);
      setHistoryPage(page); setHasOlder(!result.last);
      if (!isAdmin) {
        await messagingApi.markRead(selectedId);
        setConversations((items) => items.map((item) => item.id === selectedId ? { ...item, unreadCount: 0 } : item));
        window.dispatchEvent(new Event("messages-read"));
      }
    } catch (error) { setHistoryError(safeError(error, "Mesajlar yüklenemedi. Lütfen tekrar deneyin.")); }
    finally { setHistoryLoading(false); }
  }, [approvedReason, isAdmin, mergeMessages, selectedId]);

  useEffect(() => {
    if (sendTimeoutRef.current) clearTimeout(sendTimeoutRef.current);
    sendTimeoutRef.current = null; setSending(false); setInput("");
    setMessages([]); setHistoryPage(0); setHasOlder(false); setHistoryError(null); setSendError(null); setReadOnly(isAdmin); setApprovedReason(null); setAdminReason("");
    if (selectedId && !isAdmin) void loadHistory(0, false, null);
  }, [selectedId, isAdmin]);

  const handleLiveMessage = useCallback((message: MessageResponse) => {
    mergeMessages([message]);
    if (message.senderId === user?.id) {
      if (sendTimeoutRef.current) clearTimeout(sendTimeoutRef.current);
      sendTimeoutRef.current = null;
      setInput(""); setSending(false);
    }
    setConversations((items) => items.map((item) => item.id === message.conversationId ? { ...item, lastMessage: message.content, lastMessageAt: message.sentAt, unreadCount: message.senderId === user?.id || item.id === selectedId ? 0 : item.unreadCount + 1 } : item));
    if (!isAdmin && message.senderId !== user?.id && message.conversationId === selectedId) {
      void messagingApi.markRead(message.conversationId).then(() => window.dispatchEvent(new Event("messages-read"))).catch(() => undefined);
    }
  }, [isAdmin, mergeMessages, selectedId, user?.id]);

  const handlePresence = useCallback((presence: PresenceResponse) => {
    setConversations((items) => items.map((item) =>
      item.counterpartUserId === presence.userId
        ? { ...item, counterpartOnline: presence.online }
        : item));
  }, []);

  useEffect(() => () => { if (sendTimeoutRef.current) clearTimeout(sendTimeoutRef.current); }, []);

  const { sendViaSocket } = useConversationSocket({
    conversationId: selectedId && (!isAdmin || !!approvedReason) ? selectedId : null,
    onMessage: handleLiveMessage,
    onPresence: handlePresence,
    onReconnected: () => {
      if (selectedId) void loadHistory(0, true);
      void loadList();
    },
  });

  useEffect(() => {
    if (typeof endRef.current?.scrollIntoView === "function") endRef.current.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const openAdminHistory = async (event: React.FormEvent) => {
    event.preventDefault();
    const reason = adminReason.trim();
    if (!reason) return;
    setApprovedReason(reason);
    await loadHistory(0, false, reason);
  };

  const send = async (event: React.FormEvent) => {
    event.preventDefault();
    const content = input.trim();
    if (!content || sending || readOnly || isAdmin) return;
    setSending(true); setSendError(null);
    try {
      if (sendViaSocket(content)) {
        sendTimeoutRef.current = setTimeout(() => {
          sendTimeoutRef.current = null;
          setSending(false);
          setSendError("Mesaj onaylanamadı. Metniniz korundu; bağlantınızı kontrol edip tekrar deneyin.");
        }, 6000);
        return;
      } else {
        const response = await messagingApi.sendMessage(selectedId!, content);
        mergeMessages([response]); setInput("");
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === "MESSAGING_NOT_ALLOWED") setReadOnly(true);
      setSendError(safeError(error, "Mesaj gönderilemedi. Metniniz korundu; tekrar deneyebilirsiniz."));
    } finally { if (!sendTimeoutRef.current) setSending(false); }
  };

  let previousDay = "";
  return (
    <section className={`chat-page ${selectedId ? "chat-page--active" : ""}`} aria-label="Mesajlar">
      <aside className="chat-sidebar">
        <div className="chat-sidebar__heading"><h1>Mesajlar</h1>{isAdmin && <span className="chat-readonly-chip">Salt okunur</span>}</div>
        <label className="chat-search"><span aria-hidden="true">⌕</span><input aria-label="Konuşmalarda ara" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Kişi ara…" /></label>
        <div className="chat-list" aria-live="polite">
          {listLoading && <div className="chat-state">Konuşmalar yükleniyor…</div>}
          {listError && <div className="chat-state chat-state--error">{listError}<button onClick={() => void loadList()}>Tekrar dene</button></div>}
          {!listLoading && !listError && filtered.length === 0 && <div className="chat-state">{query ? "Aramanızla eşleşen konuşma yok." : "Henüz bir konuşmanız bulunmuyor."}</div>}
          {filtered.map((conversation) => <button key={conversation.id} className={`chat-list-item ${selectedId === conversation.id ? "is-active" : ""}`} onClick={() => navigate(`${basePath}/${conversation.id}`)}>
            <span className="chat-avatar" aria-hidden="true">{initials(conversation.name)}</span>
            <span className="chat-list-item__body"><span className="chat-list-item__top"><strong>{conversation.name}</strong><time>{formatListTime(conversation.lastMessageAt)}</time></span><span className="chat-list-item__preview" title={conversation.lastMessage}>{conversation.lastMessage}</span></span>
            {conversation.unreadCount > 0 && <span className="chat-unread" aria-label={`${conversation.unreadCount} okunmamış mesaj`}>{conversation.unreadCount > 99 ? "99+" : conversation.unreadCount}</span>}
          </button>)}
        </div>
      </aside>

      <article className="chat-conversation">
        {!selectedId || !active ? <div className="chat-empty-selection"><span className="chat-empty-selection__icon">✦</span><h2>Bir konuşma seçin</h2><p>Mesaj geçmişini görüntülemek için soldaki listeden seçim yapın.</p></div> : <>
          <header className="chat-conversation__header">
            <button className="chat-back" onClick={() => navigate(basePath)} aria-label="Konuşma listesine dön">←</button>
            <span className="chat-avatar">{initials(active.name)}</span>
            <div><h2>{active.name}</h2>{active.detail && <p>{active.detail}</p>}{isAdmin && <p>Uniform Akademi Admin · görünmez gözlemci</p>}</div>
            {!isAdmin && <span className={`chat-presence ${active.counterpartOnline ? "chat-presence--online" : "chat-presence--offline"}`}>{active.counterpartOnline ? "Çevrim içi" : "Çevrim dışı"}</span>}
          </header>

          <div className={`chat-moderation-notice ${isAdmin ? "chat-moderation-notice--admin" : ""}`} role="note">
            {isAdmin
              ? "Yönetici görünümü — Salt okunur. Bu görüşmeyi inceleyebilirsiniz ancak mesaj gönderemezsiniz."
              : "Mesajlar yöneticiler tarafından görüntülenebilir."}
          </div>

          {isAdmin && !approvedReason ? <form className="chat-admin-gate" onSubmit={openAdminHistory}><span className="chat-readonly-chip">Salt okunur gözlem</span><h2>Denetim gerekçesi gerekli</h2><p>Mesaj içerikleri açılmadan önce erişim gerekçeniz güvenlik kaydına yazılır.</p><label htmlFor="admin-chat-reason">Erişim gerekçesi</label><textarea id="admin-chat-reason" value={adminReason} onChange={(event) => setAdminReason(event.target.value)} maxLength={500} /><button disabled={!adminReason.trim() || historyLoading}>{historyLoading ? "Açılıyor…" : "Gerekçeyi kaydet ve aç"}</button>{historyError && <p role="alert" className="chat-error">{historyError}</p>}</form> : <>
            <div className="chat-history" aria-live="polite">
              {hasOlder && <button className="chat-load-older" disabled={historyLoading} onClick={() => void loadHistory(historyPage + 1, true)}>{historyLoading ? "Yükleniyor…" : "Daha eski mesajları yükle"}</button>}
              {historyLoading && messages.length === 0 && <div className="chat-state">Mesajlar yükleniyor…</div>}
              {historyError && <div className="chat-state chat-state--error">{historyError}<button onClick={() => void loadHistory(0, false)}>Tekrar dene</button></div>}
              {!historyLoading && !historyError && messages.length === 0 && <div className="chat-state">Bu konuşmada henüz mesaj yok.</div>}
              {messages.map((message) => {
                const label = dayLabel(message.sentAt); const showDay = label !== previousDay; previousDay = label;
                const mine = !isAdmin && message.senderId === user?.id;
                return <React.Fragment key={message.id}>{showDay && <div className="chat-day">{label}</div>}<div className={`chat-message ${mine ? "chat-message--mine" : ""}`}><div className="chat-bubble">{isAdmin && <strong>{message.senderName}</strong>}<span>{message.content}</span><time>{new Date(message.sentAt).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" })}</time></div></div></React.Fragment>;
              })}
              <div ref={endRef} />
            </div>
            {readOnly || isAdmin ? <div className="chat-readonly-bar">Bu konuşma salt okunur. Mesaj gönderemezsiniz.</div> : <form className="chat-composer" onSubmit={send}><textarea aria-label="Mesaj" value={input} onChange={(event) => setInput(event.target.value)} maxLength={4000} rows={1} placeholder="Mesajınızı yazın…" onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} /><span className="chat-character-count">{input.length}/4000</span><button aria-label="Mesaj gönder" disabled={!input.trim() || sending}>{sending ? "…" : "➤"}</button>{sendError && <p role="alert" className="chat-error">{sendError}</p>}</form>}
          </>}
        </>}
      </article>
    </section>
  );
};
