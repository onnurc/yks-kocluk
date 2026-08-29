import React, { useEffect, useMemo, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { AccountProfileProvider, useAccountProfile } from "../account/accountProfileContext";
import { useAuth } from "../auth/AuthProvider";
import type { UserRole } from "../auth/authTypes";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { messagingApi } from "../messaging/messagingApi";
import { useNotificationSocket } from "../messaging/useNotificationSocket";
import { BrandLogo } from "../public/BrandLogo";
import "./app-layout.css";

type NavigationItem = {
  label: string;
  to: string;
  icon: "dashboard" | "coaches" | "messages" | "meetings" | "account" | "settings" | "applications" | "safety" | "finance" | "users";
  showUnread?: boolean;
};

const navigationByRole: Record<UserRole, NavigationItem[]> = {
  STUDENT: [
    { label: "Panel", to: "/dashboard", icon: "dashboard" },
    { label: "Koçlar", to: "/coaches", icon: "coaches" },
    { label: "Mesajlar", to: "/messages", icon: "messages", showUnread: true },
    { label: "Görüşmeler", to: "/bookings", icon: "meetings" },
    { label: "Hesabım", to: "/account", icon: "account" },
  ],
  COACH: [
    { label: "Panel", to: "/dashboard", icon: "dashboard" },
    { label: "Ücretsiz Görüşme Uygunluğu", to: "/coach/trial-availability", icon: "meetings" },
    { label: "Mesajlar", to: "/messages", icon: "messages", showUnread: true },
    { label: "Hesabım", to: "/account", icon: "account" },
  ],
  ADMIN: [
    { label: "Admin Paneli", to: "/admin", icon: "dashboard" },
    { label: "Kullanıcılar", to: "/admin/users", icon: "users" },
    { label: "Koçlar", to: "/admin/coaches", icon: "coaches" },
    { label: "Koç Başvuruları", to: "/admin/coach-applications", icon: "applications" },
    { label: "Abonelik & Finans", to: "/admin/finance", icon: "finance" },
    { label: "Seanslar", to: "/admin/sessions", icon: "meetings" },
    { label: "Raporlar", to: "/admin/reports", icon: "safety" },
    { label: "Mesaj Gözlemi", to: "/admin/messages", icon: "messages" },
  ],
};

function AppIcon({ name }: { name: NavigationItem["icon"] | "menu" | "close" | "notification" | "profile" | "logout" }) {
  const paths: Record<string, React.ReactNode> = {
    dashboard: <><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></>,
    coaches: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3.5 19c.4-4 2.3-6 5.5-6s5.1 2 5.5 6M14.5 14c3.3-.7 5.3 1 6 4" /></>,
    users: <><circle cx="8" cy="8" r="3" /><circle cx="16" cy="8" r="3" /><path d="M2.5 20c.4-4.3 2.2-6.5 5.5-6.5s5.1 2.2 5.5 6.5M10.5 20c.4-4.3 2.2-6.5 5.5-6.5s5.1 2.2 5.5 6.5" /></>,
    messages: <><path d="M4 5h16v11H9l-5 4V5Z" /><path d="M8 9h8M8 12h6" /></>,
    meetings: <><rect x="4" y="6" width="16" height="14" rx="2" /><path d="M8 3v6M16 3v6M4 11h16" /></>,
    account: <><circle cx="12" cy="8" r="3" /><path d="M6 20c.3-4.2 2.3-6.3 6-6.3s5.7 2.1 6 6.3" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M19 13.5v-3l-2.2-.7-.7-1.7 1.1-2-2.1-2.1-2 1.1-1.7-.7L10.5 2h-3l-.7 2.2-1.7.7-2-1.1L1 5.9l1.1 2-.7 1.7L-.8 10.5v3l2.2.7.7 1.7-1.1 2L3.1 20l2-1.1 1.7.7.7 2.2h3l.7-2.2 1.7-.7 2 1.1 2.1-2.1-1.1-2 .7-1.7 2.4-.7Z" transform="translate(3 0) scale(.75)" /></>,
    applications: <><path d="M7 4h10v4H7z" /><path d="M5 6h14v15H5zM8 12h8M8 16h5" /></>,
    safety: <><path d="M12 3 5 6v5c0 4.7 2.8 8 7 10 4.2-2 7-5.3 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></>,
    finance: <><rect x="3" y="6" width="18" height="13" rx="2" /><path d="M3 10h18M7 15h3" /></>,
    menu: <path d="M4 7h16M4 12h16M4 17h16" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    notification: <><path d="M7 17h10l-1.2-1.8V10a3.8 3.8 0 0 0-7.6 0v5.2L7 17Z" /><path d="M10 20h4" /></>,
    profile: <><circle cx="12" cy="8" r="3" /><path d="M6 20c.3-4.2 2.3-6.3 6-6.3s5.7 2.1 6 6.3" /></>,
    logout: <><path d="M10 5H5v14h5M14 8l4 4-4 4M18 12H9" /></>,
  };

  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

const roleLabels: Record<UserRole, string> = {
  STUDENT: "Öğrenci hesabı",
  COACH: "Koç hesabı",
  ADMIN: "Yönetici hesabı",
};

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");

function ProfileAvatar({ fullName, imageUrl }: { fullName: string; imageUrl: string | null }) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const availableImageUrl = imageUrl && imageUrl !== failedImageUrl ? imageUrl : null;

  return (
    <span className="app-layout__avatar" aria-hidden="true">
      {availableImageUrl ? <img src={availableImageUrl} alt="" onError={() => setFailedImageUrl(availableImageUrl)} /> : initials(fullName)}
    </span>
  );
}

const AppLayoutContent: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const { profileImageUrl } = useAccountProfile();
  const navigate = useNavigate();
  const footerDocuments = useLegalDocuments(["KVKK_NOTICE", "PRIVACY_POLICY", "TERMS_OF_USE"]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const canSeeMessages = isAuthenticated && (user?.role === "STUDENT" || user?.role === "COACH");
  const displayedUnreadCount = canSeeMessages ? unreadCount : 0;
  const navigation = useMemo(() => user ? navigationByRole[user.role] : [], [user]);

  useEffect(() => {
    if (!canSeeMessages) {
      return;
    }
    let cancelled = false;
    const fetchUnreadCount = () => {
      messagingApi.listConversations().then((list) => {
        if (!cancelled) setUnreadCount((list || []).reduce((sum, conversation) => sum + (conversation.unreadCount || 0), 0));
      }).catch(() => undefined);
    };
    fetchUnreadCount();
    window.addEventListener("messages-read", fetchUnreadCount);
    return () => {
      cancelled = true;
      window.removeEventListener("messages-read", fetchUnreadCount);
    };
  }, [canSeeMessages]);

  useNotificationSocket({
    enabled: canSeeMessages,
    onNotification: (notification) => setUnreadCount(notification.unreadTotal),
  });

  useEffect(() => {
    if (!navigationOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setNavigationOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [navigationOpen]);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  if (!isAuthenticated || !user) {
    return (
      <div className="app-layout app-layout--guest">
        <header className="app-layout__header"><BrandLogo size="compact" to="/" /><Link to="/login">Giriş Yap</Link></header>
        <main className="app-layout__main"><Outlet /></main>
      </div>
    );
  }

  const messagePath = user.role === "ADMIN" ? "/admin/messages" : "/messages";

  return (
    <div className="app-layout">
      <a className="app-layout__skip-link" href="#authenticated-content">İçeriğe geç</a>
      <header className="app-layout__header">
        <div className="app-layout__header-start">
          <button type="button" className="app-layout__icon-button app-layout__menu-button" aria-label={navigationOpen ? "Menüyü kapat" : "Menüyü aç"} aria-controls="authenticated-navigation" aria-expanded={navigationOpen} onClick={() => setNavigationOpen((open) => !open)}>
            <AppIcon name={navigationOpen ? "close" : "menu"} />
          </button>
          <BrandLogo className="app-layout__brand" size="compact" to="/dashboard" />
          <span className="app-layout__product-name">Mentorluk</span>
        </div>

        <div className="app-layout__header-actions">
          {canSeeMessages && (
            <Link className="app-layout__icon-button app-layout__notification" to={messagePath} onClick={() => setNavigationOpen(false)} aria-label={displayedUnreadCount > 0 ? `${displayedUnreadCount} okunmamış mesaj, Mesajlara git` : "Mesajlara git"}>
              <AppIcon name="notification" />
              {displayedUnreadCount > 0 && <span className="app-layout__notification-dot" aria-hidden="true" />}
            </Link>
          )}
          <div className="app-layout__identity">
            <ProfileAvatar fullName={user.fullName} imageUrl={profileImageUrl} />
            <span><strong>{user.fullName}</strong><small>{roleLabels[user.role]}</small></span>
          </div>
          <button type="button" className="app-layout__icon-button app-layout__profile-button" aria-label="Hesap menüsünü aç" aria-controls="authenticated-navigation" aria-expanded={navigationOpen} onClick={() => setNavigationOpen(true)}>
            <ProfileAvatar fullName={user.fullName} imageUrl={profileImageUrl} />
          </button>
        </div>
      </header>

      <div className="app-layout__frame">
        <aside id="authenticated-navigation" className={`app-layout__sidebar ${navigationOpen ? "is-open" : ""}`}>
          <div className="app-layout__mobile-account">
            <ProfileAvatar fullName={user.fullName} imageUrl={profileImageUrl} />
            <span><strong>{user.fullName}</strong><small>{roleLabels[user.role]}</small></span>
          </div>
          <nav className="app-layout__navigation" aria-label="Ürün navigasyonu">
            {navigation.map((item) => (
              <NavLink key={item.to} to={item.to} onClick={() => setNavigationOpen(false)} className={({ isActive }) => `app-layout__nav-link ${isActive ? "is-active" : ""}`} end={item.to === "/dashboard" || item.to === "/admin"}>
                <AppIcon name={item.icon} />
                <span>{item.label}</span>
                {item.showUnread && displayedUnreadCount > 0 && <span className="app-layout__unread-badge">{displayedUnreadCount > 99 ? "99+" : displayedUnreadCount}</span>}
              </NavLink>
            ))}
          </nav>

          <div className="app-layout__sidebar-bottom">
            <Link className="app-layout__privacy-link" to="/privacy" onClick={() => setNavigationOpen(false)}>Gizlilik tercihleri</Link>
            <button type="button" className="app-layout__logout" onClick={() => void handleLogout()}>
              <AppIcon name="logout" />
              <span>Çıkış yap</span>
            </button>
          </div>
        </aside>

        {navigationOpen && <button type="button" className="app-layout__backdrop" aria-label="Menü dışını kapat" onClick={() => setNavigationOpen(false)} />}

        <div className="app-layout__content-column">
          <main id="authenticated-content" className="app-layout__main"><Outlet /></main>
          <footer className="app-layout__footer">
            <span>© {new Date().getFullYear()} Uniform Akademi</span>
            <div>
              <LegalDocumentViewer label="KVKK" document={footerDocuments.documents.KVKK_NOTICE} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
              <LegalDocumentViewer label="Gizlilik" document={footerDocuments.documents.PRIVACY_POLICY} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
              <LegalDocumentViewer label="Kullanım Koşulları" document={footerDocuments.documents.TERMS_OF_USE} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
            </div>
          </footer>
        </div>
      </div>
    </div>
  );
};

export const AppLayout: React.FC = () => (
  <AccountProfileProvider>
    <AppLayoutContent />
  </AccountProfileProvider>
);
