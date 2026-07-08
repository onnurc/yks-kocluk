# YKS Coaching Platform - Frontend Foundation (Phase 0)

This directory contains the React + Vite + TypeScript + ESLint frontend codebase for the YKS Coaching platform.

---

## 1. How to Run Locally

### Prerequisites
*   Node.js (v18 or higher is recommended)

### Step 1: Install Dependencies
Run from the `frontend/` directory:
```bash
npm install
```

### Step 2: Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Ensure `VITE_API_BASE_URL` points to your running backend application (defaults to `http://localhost:8080`).

### Step 3: Run Development Server
```bash
npm run dev
```

### Step 4: Build for Production
```bash
npm run build
```

---

## 2. Scope & Foundation of Phase 0

Phase 0 sets up the core architecture of the client application:
*   **API Client (`src/api/httpClient.ts`):** Simple wrapper around native `fetch` supporting JSON parsing, problem-details format handling (`application/problem+json`), and auto-injecting Bearer JWT headers on demand.
*   **Auth Token Storage (`src/auth/tokenStorage.ts`):** Manages local storage of tokens with comments on cookie transition plans.
*   **Global Auth Context (`src/auth/AuthProvider.tsx`):** Tracks session, role, active user, loading, and suspended account redirects.
*   **Route Protection:** Provides `ProtectedRoute` (unauthenticated user redirect) and `RoleRoute` (role-based view authorization).
*   **Placeholder Pages:** Login, Register, Dashboard, Suspended User details, Admin views, and wildcard 404 screens.

---

## 3. Next Steps & Phase Roadmap

*   **FE Phase 1 — Authentication:** Enhance registration forms, profile verification, and token expiration handling.
*   **FE Phase 2 — Student Dashboard:** Integrate package selection, checkout forms, and display pending payment warnings.
*   **FE Phase 3 — Booking System:** Connect schedule calendar events and limit booking slots based on subscription levels.
*   **FE Phase 4 — Payment Gates:** Integrate sandbox Iyzico frames and test payment success webhooks.
