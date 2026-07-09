# YKS Coaching Platform - Frontend (Phase 1)

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

## 2. Phase 1 — Authentication Flows

Phase 1 establishes complete end-to-end user session lifecycle flows:
*   **Sign-in & Sign-up Forms (`LoginPage`, `RegisterPage`):** Fully controlled components verifying credentials, mapping custom attributes (`firstName`/`lastName` to `fullName`), and routing profiles dynamically based on roles.
*   **Form Errors Panel (`FormError.tsx`):** Renders custom server details or invalid payload lists dynamically.
*   **Redirect Guards:** Prevents already signed-in users from accessing credentials forms, redirecting them straight to active dashboards.
*   **Logout Mechanics:** Triggers service calls to discard remote sessions and completely flushes client token storages locally.

---

## 3. Manual Testing Guide

Ensure the backend server is running at `VITE_API_BASE_URL` before testing:

### Test 3.1: Register New Student or Coach
1.  Navigate to `/register`.
2.  Input first name, last name, unique email address, password (>= 8 characters), and select either "Öğrenci" or "Koç".
3.  Click "Kayıt Ol" -> Confirms registration and automatically logs in to `/dashboard`.

### Test 3.2: Login Existing User
1.  Navigate to `/login`.
2.  Submit registered user credentials.
3.  Upon success, verify you are redirected:
    *   To `/admin` if the role is `ADMIN`.
    *   To `/dashboard` if the role is `STUDENT` or `COACH`.

### Test 3.3: Access Guards Validation
1.  Ensure you are logged out. Try accessing `/dashboard` or `/admin` directly -> verify you are redirected to `/login`.
2.  Log in as a `STUDENT` or `COACH`. Try accessing `/admin` directly -> verify you are redirected back to `/dashboard`.
3.  Log in as any user. Try accessing `/login` or `/register` -> verify you are redirected straight back to your profile dashboard.
