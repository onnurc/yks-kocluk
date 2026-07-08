# Frontend Integration Checklist & Guide

This document outlines the integrations, API expectations, UI/UX behaviors, and edge cases required of the frontend application when consuming the MVP backend.

---

## 1. Authentication

*   **UI Location:** Login Page (`/login`), Registration Page (`/register`), Navigation Headers.
*   **Backend Endpoint:** 
    *   `POST /api/v1/auth/register` (Registration)
    *   `POST /api/v1/auth/login` (Login)
    *   `POST /api/v1/auth/refresh` (Session Refresh)
    *   `POST /api/v1/auth/logout` (Logout)
    *   `GET /api/v1/auth/me` (Profile retrieval)
*   **Required Role/Token:** Public (for register/login/refresh); Authenticated (`Bearer <accessToken>`) for profile/logout.
*   **Request Body:** See `docs/api-requests.md` Section 1.
*   **Expected Frontend Behavior:**
    1.  Upon successful login or registration, store `accessToken` and `refreshToken` securely (e.g., in Secure HttpOnly cookies or memory/localStorage).
    2.  Set `Authorization: Bearer <accessToken>` header on all subsequent API requests.
    3.  If an API request returns `401 Unauthorized`, trigger the refresh flow using `POST /api/v1/auth/refresh`.
    4.  If user is suspended, the API will return `403 Forbidden` with the error details `USER_SUSPENDED`. The frontend must redirect the user to a "Suspended Account" page showing the suspension reason (see Section 14).
    5.  Upon logout, clear all local state and tokens, and redirect to the landing page.
*   **Edge Cases:** Expired tokens must trigger silent refresh. If refresh fails, log the user out.

---

## 2. Subscription Checkout

*   **UI Location:** Packages/Pricing Page, Coach Details Subscription Button.
*   **Backend Endpoint:** `POST /api/v1/subscriptions/checkout`
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Request Body:** `{"coachId": Long, "packageId": Long}`
*   **Expected Frontend Behavior:**
    1.  Clicking the checkout button calls the checkout endpoint.
    2.  If the response contains `checkoutFormContent` (the Iyzico script and form snippet), mount this HTML content dynamically into a container iframe or modal.
    3.  Handle the Iyzico iframe load; the student will input credit card details within the secure Iyzico component.
    4.  Once checkout is created, track that the subscription state is `PENDING_PAYMENT`.
*   **Edge Cases:**
    *   Coach capacity might be full (returns `400 Bad Request` with capacity error). Disable button or show error popup.
    *   User has an ongoing subscription already (should return validation error).
*   **Notes:** Do NOT close the modal until the provider indicates redirect success or fail.

---

## 3. PENDING_PAYMENT UI Restrictions

*   **UI Location:** Student Dashboard, Chat Panel, Booking Calendar.
*   **Backend Endpoint:** Evaluated automatically at gateways (WebSocket STOMP & Session REST API).
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Expected Frontend Behavior:**
    1.  Check the user's active subscription status (retrieved from `GET /api/v1/subscriptions/me`).
    2.  If status is `PENDING_PAYMENT`:
        *   **Chat Panel:** Disable typing input in the conversation pane. Show a banner: *"Sohbet edebilmek için lütfen ödemenizi tamamlayın."*
        *   **Booking Panel:** Disable calendar slot clicking. Show a banner: *"Randevu alabilmek için lütfen ödeme işlemini gerçekleştirin."*
    3.  Provide a direct link to re-trigger checkout or view the payment gateway.
*   **Edge Cases:** If a user attempts to bypass UI restrictions, the backend gates will return a `403 Forbidden` response. The frontend must display this backend error message clearly.

---

## 4. Payment Completion

*   **UI Location:** Payment Success Page, Dashboard redirect.
*   **Backend Endpoint:** 
    *   `POST /api/v1/payments/{paymentId}/stub/succeed` (Local/Dev stub testing only)
    *   *Note:* Real payment completion is handled via the backend Iyzico Webhook (`POST /api/v1/payments/iyzico/webhook`).
*   **Required Role/Token:** Student (Stub) / Public (Webhook).
*   **Expected Frontend Behavior:**
    1.  **For Dev/Local Testing:** The frontend can trigger a test action or developer-mode button to call the stub success endpoint `POST /api/v1/payments/{paymentId}/stub/succeed`.
    2.  **Redirect callback page:** The frontend must poll the subscription status (via `GET /api/v1/subscriptions/me`) or refresh current user details upon redirect return.
    3.  Transition from `PENDING_PAYMENT` to `ACTIVE` changes the UI:
        *   Remove warning banners.
        *   Enable the booking calendar and chat box.
        *   Show a confetti success modal: *"Ödemeniz başarıyla alındı! Artık görüşmeye hazırsınız."*
*   **Notes:** Webhooks are processed asynchronously. The redirect URL page should show a loading indicator and query `/api/v1/subscriptions/me` every 2-3 seconds up to 5 times to confirm active state.

---

## 5. Cancel-Renewal

*   **UI Location:** User Settings -> Subscription/Billing Tab.
*   **Backend Endpoint:** `POST /api/v1/subscriptions/{id}/cancel-renewal`
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Expected Frontend Behavior:**
    1.  Display a button: *"Yenilemeyi İptal Et"* (Cancel Renewal) for any subscription where `autoRenew` is `true`.
    2.  Show a confirmation modal: *"Aboneliğinizin otomatik yenilenmesini iptal etmek istediğinizden emin misiniz? Bir sonraki ödeme dönemine kadar ({endDate}) koçunuza erişiminiz devam edecektir."*
    3.  On confirmation, call the endpoint, set `autoRenew` representation to `false`, and display: *"Yenileme iptal edildi. Erişiminiz {endDate} tarihine kadar aktiftir."*
*   **Edge Cases:** Ensure the cancel button is hidden if subscription status is already terminated, expired, or autoRenew is already false.

---

## 6. Admin Refund

*   **UI Location:** Admin Panel -> Payments Management.
*   **Backend Endpoint:** `POST /api/v1/admin/payments/{paymentId}/refund`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Request Body:** `{"amount": BigDecimal, "reason": String}`
*   **Expected Frontend Behavior:**
    1.  Provide a "Refund" action next to successful payments in the administrative list.
    2.  Present a modal form with fields:
        *   *Refund Amount:* Numeric input (validating it cannot exceed the payment price).
        *   *Reason:* Text field (explaining the return).
    3.  Call the endpoint and show the response (confirming refund status and remaining refundable balance).
    4.  Update the payment row state to reflects refund details.

---

## 7. Consent / KVKK

*   **UI Location:** Registration Flow, First Login Overlay.
*   **Backend Endpoint:** `POST /api/v1/consents`
*   **Required Role/Token:** Authenticated User (`Bearer <userToken>`)
*   **Request Body:** `{"consentType": "KVKK" | "TERMS" | "PRIVACY" | "MARKETING", "documentVersion": "v1.0"}`
*   **Expected Frontend Behavior:**
    1.  Prior to allowing users to access the dashboard on registration or first login, check if consent is recorded (or require it on the registration form).
    2.  Render the legal document block with a checkbox: *"KVKK Aydınlatma Metnini okudum ve kabul ediyorum."*
    3.  Upon ticking and clicking next, call the endpoint. If successful, save consent state locally or redirect to dashboard.
*   **Edge Cases:** If consent Type is rejected by the backend or database connection fails, show validation error and block access to dashboard.

---

## 8. Safety Report

*   **UI Location:** Chat Conversation Options (Report Coach/Student), Messaging Thread context menu.
*   **Backend Endpoint:** `POST /api/v1/reports`
*   **Required Role/Token:** Authenticated User (`Bearer <userToken>`)
*   **Request Body:** `{"targetType": "USER" | "CONVERSATION" | "MESSAGE", "targetId": Long, "reason": String, "details": String}`
*   **Expected Frontend Behavior:**
    1.  Present a "Report" button next to messaging bubbles or in the coach profile menu.
    2.  Show a modal form with violation options: *Spam, Harassment, Abuse, Sharing Personal Contact Information, Other*.
    3.  Collect input and send the report. Show validation success message: *"Şikayetiniz alındı. Moderatörlerimiz inceleyecektir."*
*   **Edge Cases:** Prevent users from reporting themselves in the UI.

---

## 9. Admin Report List

*   **UI Location:** Admin Dashboard -> Reports Management.
*   **Backend Endpoint:** `GET /api/v1/admin/reports`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Expected Frontend Behavior:**
    1.  Render a table showing all reports (reporter, target type, target ID, reason, status, timestamp).
    2.  Provide filters: Status (`OPEN`, `REVIEWED`, `RESOLVED`, `DISMISSED`).
    3.  Support standard paging (query params `page`, `size`, `sort`).

---

## 10. Admin User Suspend

*   **UI Location:** Admin Dashboard -> User Management -> User Detail View.
*   **Backend Endpoint:** `POST /api/v1/admin/users/{id}/suspend`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Request Body:** `{"reason": String}`
*   **Expected Frontend Behavior:**
    1.  Display a prominent button: *"Kullanıcıyı Askıya Al"* (Suspend User).
    2.  Show modal asking for suspension reason.
    3.  Upon submission, update user status display to `SUSPENDED` and display a success toast message.

---

## 11. Admin Subscription Terminate

*   **UI Location:** Admin Dashboard -> Subscriptions Management.
*   **Backend Endpoint:** `POST /api/v1/admin/subscriptions/{id}/terminate`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Request Body:** `{"reason": String}`
*   **Expected Frontend Behavior:**
    1.  Difference from Cancel-Renewal: Cancel-renewal allows active access until the end of the term. **Termination cuts off access immediately.**
    2.  Display warning in modal: *"DİKKAT: Abonelik hemen iptal edilecektir. Öğrencinin koçla olan tüm rezervasyonları ve mesajlaşma erişimi derhal durdurulacaktır."*
    3.  Call endpoint, update subscription status row to `TERMINATED`.

---

## 12. Admin Conversation Access Audit

*   **UI Location:** Admin Dashboard -> Conversations Oversight -> Chat Thread.
*   **Backend Endpoint:** `POST /api/v1/admin/conversations/{conversationId}/access-log`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Expected Frontend Behavior:**
    1.  Before displaying the messages in a conversation thread to an administrator, prompt them with an audit overlay: *"Bu görüşmeyi görüntülemek için bir gerekçe girmeniz gerekmektedir."*
    2.  The administrator inputs the reason and clicks confirm.
    3.  The frontend calls the log endpoint. Upon `200 OK`, fetch and render the messages.
    4.  **Crucial Constraint:** Admin views must NOT trigger read-receipts. The admin must fetch via the admin endpoints, not student/coach message markers.

---

## 13. Booking & Messaging Gates

*   **UI Location:** Chat Box, Booking Dashboard.
*   **Expected Frontend Behavior:**
    1.  Never rely purely on UI disabling. Access controls are enforced centrally.
    2.  If the backend rejects a STOMP connection or rejects a session booking with a validation failure (e.g. lack of subscription, slot booked), show the warning text returned by the API details response directly on the screen.

---

## 14. Error Handling

The backend returns standard problem details (`application/problem+json`). The frontend must parse these responses and handle:

*   **401 Unauthorized:** JWT token expired/missing. Trigger token refresh. If refresh fails, navigate to `/login`.
*   **403 Forbidden (`USER_SUSPENDED`):** Account suspended. The response body contains:
    ```json
    {
      "status": 403,
      "errorCode": "USER_SUSPENDED",
      "detail": "Hesabınız askıya alınmıştır"
    }
    ```
    Redirect user to `/suspended` displaying the detail message and contact support options.
*   **409 Conflict:** E.g. Slot double booking. Show error toast: *"Seçilen saat dilimi doludur. Lütfen başka bir slot seçin."*
*   **400 Bad Request:** Form validation errors (e.g., password length, missing fields). Map error array to form field warning labels.

---

## 15. Environment Handling

*   **Local / Dev Environment:**
    *   Stub/mocks are active.
    *   Instruct QA/testers to use the manual transition endpoint `POST /api/v1/payments/{paymentId}/stub/succeed` to progress payments.
*   **Sandbox Mode:**
    *   Use sandbox credentials.
    *   Mount the returned Iyzico HTML container for sandbox card test submissions.
*   **Production Environment:**
    *   Real payments processing. Ensure CORS, SSL, and webhook URL domain settings are correctly aligned in environmental configuration profiles.
