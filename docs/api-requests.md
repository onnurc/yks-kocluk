# API Request Collection & Testing Guide

This document contains copy-paste friendly API examples and testing tools for the MVP backend.

---

## Suggested Test Order

We recommend following this sequential path when testing the backend features:
1. **Login / Register**: Obtain student, coach, and admin JWT tokens.
2. **Checkout**: Create a `PENDING_PAYMENT` subscription.
3. **Verify `PENDING_PAYMENT` restrictions**: Try to book a session or send messages; verify they are blocked.
4. **Stub Success or Webhook Success**: Approve/complete the payment.
5. **Verify `ACTIVE` access**: Ensure booking and STOMP messaging now function.
6. **Cancel-Renewal**: Disable subscription auto-renewal.
7. **Refund**: Perform an admin refund.
8. **Reports & Consents**: Submit consent data and violations reports.
9. **Suspend**: Suspend a student/coach account and verify their access is immediately rejected.
10. **Terminate**: Hard-terminate a subscription.

---

## 1. Authentication

### 1.1. Register User
*   **Purpose:** Register a new Student or Coach account.
*   **Method and URL:** `POST http://localhost:8080/api/v1/auth/register`
*   **Required Role/Token:** Public
*   **Headers:**
    ```http
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "email": "student1@example.com",
      "password": "SecurePassword123",
      "fullName": "John Doe",
      "role": "STUDENT"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
    }
    ```
*   **Important State Changes:** New user row in `users` table with status `ACTIVE`.
*   **Common Error Cases:** `400 Bad Request` if email is invalid/duplicate or password length < 8.

### 1.2. User Login
*   **Purpose:** Authenticate and retrieve active JWT tokens.
*   **Method and URL:** `POST http://localhost:8080/api/v1/auth/login`
*   **Required Role/Token:** Public
*   **Headers:**
    ```http
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "email": "student1@example.com",
      "password": "SecurePassword123"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
    }
    ```
*   **Important State Changes:** None.
*   **Common Error Cases:** `401 Unauthorized` for incorrect password/email combination.

### 1.3. Get Profile (Me)
*   **Purpose:** Retrieve detailed account settings for current logged-in user.
*   **Method and URL:** `GET http://localhost:8080/api/v1/auth/me`
*   **Required Role/Token:** Authenticated user (`Bearer <accessToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <accessToken>
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 1,
      "email": "student1@example.com",
      "fullName": "John Doe",
      "role": "STUDENT",
      "status": "ACTIVE"
    }
    ```
*   **Important State Changes:** None.
*   **Common Error Cases:** `401 Unauthorized` / `403 Forbidden` if token is missing, expired, or invalid.

---

## 2. Subscription

### 2.1. Subscription Checkout
*   **Purpose:** Initiate purchase of a coaching package, transitioning subscription status to `PENDING_PAYMENT`.
*   **Method and URL:** `POST http://localhost:8080/api/v1/subscriptions/checkout`
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <studentToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "coachId": 1,
      "packageId": 2
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "subscriptionId": 5,
      "paymentId": 10,
      "status": "PENDING_PAYMENT",
      "amount": 150.00,
      "checkoutFormContent": "<script type=\"text/javascript\">...</script><div id=\"iyzipay-checkout-form\"></div>"
    }
    ```
*   **Important State Changes:** Creates a `subscriptions` row (status `PENDING_PAYMENT`) and a `payments` row (status `PENDING`).
*   **Common Error Cases:** `400 Bad Request` if coach has no capacity.

### 2.2. Cancel Subscription Renewal
*   **Purpose:** Opt out of auto-renewal for the next billing cycle.
*   **Method and URL:** `POST http://localhost:8080/api/v1/subscriptions/{id}/cancel-renewal`
*   **Required Role/Token:** Student who owns the subscription (`Bearer <studentToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <studentToken>
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 5,
      "studentId": 1,
      "coachProfileId": 1,
      "status": "ACTIVE",
      "autoRenew": false,
      "endDate": "2026-08-08T08:00:00Z"
    }
    ```
*   **Important State Changes:** `auto_renew` column set to `false`.
*   **Common Error Cases:** `403 Forbidden` if subscription belongs to a different student.

### 2.3. Terminate Subscription (Admin)
*   **Purpose:** Forcefully terminate a subscription immediately, ending student access and freeing coach capacity.
*   **Method and URL:** `POST http://localhost:8080/api/v1/admin/subscriptions/{id}/terminate`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <adminToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "reason": "Severe misconduct or contract termination"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "subscriptionId": 5,
      "status": "TERMINATED",
      "terminationReason": "Severe misconduct or contract termination",
      "terminatedAt": "2026-07-08T08:58:00Z"
    }
    ```
*   **Important State Changes:** Subscription status is updated to `TERMINATED`, `termination_reason` is set, and the coach's active student count is decremented.
*   **Common Error Cases:** `400 Bad Request` if subscription is already terminated or expired.

---

## 3. Payment

### 3.1. Stub Payment Succeed
*   **Purpose:** Manually mark a pending stub/sandbox payment as successful.
*   **Method and URL:** `POST http://localhost:8080/api/v1/payments/{paymentId}/stub/succeed`
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <studentToken>
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 5,
      "status": "ACTIVE",
      "coachProfileId": 1,
      "autoRenew": true
    }
    ```
*   **Important State Changes:** `payments` table status transitions to `SUCCESS`, `subscriptions` status transitions to `ACTIVE`, and coach capacity increments.

### 3.2. Iyzico Webhook Callback
*   **Purpose:** Handle notification from Iyzico billing engine.
*   **Method and URL:** `POST http://localhost:8080/api/v1/payments/iyzico/webhook`
*   **Required Role/Token:** Public
*   **Headers:**
    ```http
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "paymentId": 12345,
      "status": "SUCCESS",
      "providerReference": "iyz_ref_998877"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "status": "OK",
      "message": "Webhook processed successfully"
    }
    ```
*   **Important State Changes:** Activates pending subscription on `SUCCESS`, increments capacity, or fails it on `FAILED`.
*   **Common Error Cases:** Replaying already processed transaction must not double-increment capacity (Idempotent response).

### 3.3. Admin Refund
*   **Purpose:** Issue a refund for a successful payment.
*   **Method and URL:** `POST http://localhost:8080/api/v1/admin/payments/{paymentId}/refund`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <adminToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "amount": 150.00,
      "reason": "Dissatisfied student cancellation request"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "refundId": 101,
      "paymentId": 12345,
      "status": "SUCCESS",
      "refundedAmount": 150.00,
      "remainingAmount": 0.00,
      "message": "Refund processed successfully"
    }
    ```
*   **Important State Changes:** Marks payment status/fields as refunded, marks subscription status, decrements coach active students capacity.
*   **Common Error Cases:** `400 Bad Request` if refund amount > original payment amount.

---

## 4. KVKK / Consent

### 4.1. Record Consent
*   **Purpose:** Store user acceptance of policies (KVKK, TERMS, PRIVACY).
*   **Method and URL:** `POST http://localhost:8080/api/v1/consents`
*   **Required Role/Token:** Authenticated user (`Bearer <userToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <userToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "consentType": "KVKK",
      "documentVersion": "v1.0"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 23,
      "userId": 1,
      "consentType": "KVKK",
      "documentVersion": "v1.0",
      "acceptedAt": "2026-07-08T08:58:30Z",
      "ipAddress": "127.0.0.1"
    }
    ```
*   **Important State Changes:** Creates rows in the `consent_records` audit table.

---

## 5. Safety Reports

### 5.1. Create Report
*   **Purpose:** File safety or rule violations report against a user, message, or channel.
*   **Method and URL:** `POST http://localhost:8080/api/v1/reports`
*   **Required Role/Token:** Authenticated user (`Bearer <userToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <userToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "targetType": "USER",
      "targetId": 2,
      "reason": "Abusive communication",
      "details": "Aggressive behavior shown during slot meetings."
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 9,
      "reporterId": 1,
      "targetType": "USER",
      "targetId": 2,
      "reason": "Abusive communication",
      "status": "OPEN",
      "createdAt": "2026-07-08T08:58:40Z"
    }
    ```
*   **Important State Changes:** A new `reports` row is initialized in the DB.

### 5.2. List Reports (Admin)
*   **Purpose:** Retrieve list of safety reports (with optional status filtering).
*   **Method and URL:** `GET http://localhost:8080/api/v1/admin/reports?status=OPEN`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <adminToken>
    ```
*   **Successful Response Shape:**
    ```json
    {
      "content": [
        {
          "id": 9,
          "reporterId": 1,
          "targetType": "USER",
          "targetId": 2,
          "reason": "Abusive communication",
          "status": "OPEN",
          "createdAt": "2026-07-08T08:58:40Z"
        }
      ],
      "totalElements": 1,
      "totalPages": 1,
      "size": 20,
      "number": 0
    }
    ```
*   **Important State Changes:** None.

---

## 6. Admin User Control

### 6.1. Suspend User
*   **Purpose:** Suspend a user to block all further platform access.
*   **Method and URL:** `POST http://localhost:8080/api/v1/admin/users/{id}/suspend`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <adminToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "reason": "Repeated safety violations"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "userId": 2,
      "status": "SUSPENDED",
      "reason": "Repeated safety violations",
      "suspendedAt": "2026-07-08T08:59:00Z"
    }
    ```
*   **Important State Changes:** User status transitions to `SUSPENDED`, and active login/WebSocket tokens are invalidated.

---

## 7. Admin Conversation Audit

### 7.1. Log Admin Thread View
*   **Purpose:** Log access when an admin reads student-coach chats.
*   **Method and URL:** `POST http://localhost:8080/api/v1/admin/conversations/{conversationId}/access-log`
*   **Required Role/Token:** Admin (`Bearer <adminToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <adminToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "reason": "Reviewing report ID 9"
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 42,
      "adminUserId": 100,
      "conversationId": 12,
      "reason": "Reviewing report ID 9",
      "timestamp": "2026-07-08T08:59:10Z"
    }
    ```
*   **Important State Changes:** Creates audit record in `admin_conversation_access_logs`.

---

## 8. Booking & Messaging Gate Checks

### 8.1. Create Session Booking (Gate Check)
*   **Purpose:** Attempt to book a session. Requires student to have an active subscription.
*   **Method and URL:** `POST http://localhost:8080/api/v1/sessions`
*   **Required Role/Token:** Student (`Bearer <studentToken>`)
*   **Headers:**
    ```http
    Authorization: Bearer <studentToken>
    Content-Type: application/json
    ```
*   **Request Body:**
    ```json
    {
      "availabilityId": 15
    }
    ```
*   **Successful Response Shape:**
    ```json
    {
      "id": 3,
      "studentId": 1,
      "coachId": 1,
      "availabilityId": 15,
      "status": "BOOKED",
      "createdAt": "2026-07-08T08:59:30Z"
    }
    ```
*   **Important State Changes:** Session created.
*   **Notes:** If subscription is `PENDING_PAYMENT` or `TERMINATED`, it returns `400 Bad Request` or `403 Forbidden` with a message explaining they must purchase an active subscription.

### 8.2. WebSocket Messaging Access Gate
*   **Manual Verification:**
    *   Initiate STOMP client connection. Include token in authorization header native key: `Authorization: Bearer <studentToken>`.
    *   Attempt connection.
    *   If student lacks an `ACTIVE` subscription (e.g. status is `PENDING_PAYMENT`, `TERMINATED`, or `REFUNDED`), the connection will disconnect immediately or send back a STOMP Error Frame blocking access.

---

## PowerShell Test Script

Save the script below to `test-flow.ps1` and run it in PowerShell to verify basic subscription transitions:

```powershell
# Define target host
$BaseUrl = "http://localhost:8080/api/v1"

# --- 1. Login to obtain tokens ---
Write-Host "Logging in as Student..." -ForegroundColor Cyan
$LoginBody = @{
    email = "student1@example.com"
    password = "SecurePassword123"
} | ConvertTo-Json

$AuthResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $LoginBody -ContentType "application/json"
$StudentToken = $AuthResponse.accessToken
Write-Host "Access Token retrieved: $($StudentToken.Substring(0,15))..." -ForegroundColor Green

# --- 2. Subscribe (Initiate Checkout) ---
Write-Host "Creating Checkout..." -ForegroundColor Cyan
$CheckoutBody = @{
    coachId = 1
    packageId = 2
} | ConvertTo-Json

$Headers = @{ Authorization = "Bearer $StudentToken" }
$CheckoutResponse = Invoke-RestMethod -Uri "$BaseUrl/subscriptions/checkout" -Method Post -Body $CheckoutBody -Headers $Headers -ContentType "application/json"
$SubId = $CheckoutResponse.subscriptionId
$PayId = $CheckoutResponse.paymentId
Write-Host "Created Subscription ID: $SubId, Payment ID: $PayId" -ForegroundColor Green

# --- 3. Verify PENDING_PAYMENT restriction (Try Booking) ---
Write-Host "Attempting session booking under pending payment (Should fail)..." -ForegroundColor Cyan
$BookingBody = @{ availabilityId = 15 } | ConvertTo-Json
try {
    Invoke-RestMethod -Uri "$BaseUrl/sessions" -Method Post -Body $BookingBody -Headers $Headers -ContentType "application/json"
    Write-Host "FAIL: Booking should not have succeeded." -ForegroundColor Red
} catch {
    Write-Host "PASS: Booking correctly rejected. Error: $_" -ForegroundColor Green
}

# --- 4. Stub success ---
Write-Host "Completing Stub Payment..." -ForegroundColor Cyan
$PaymentResponse = Invoke-RestMethod -Uri "$BaseUrl/payments/$PayId/stub/succeed" -Method Post -Headers $Headers
Write-Host "Subscription Status updated to: $($PaymentResponse.status)" -ForegroundColor Green

# --- 5. Verify ACTIVE access (Try Booking now) ---
Write-Host "Attempting session booking under active subscription..." -ForegroundColor Cyan
try {
    $BookingResponse = Invoke-RestMethod -Uri "$BaseUrl/sessions" -Method Post -Body $BookingBody -Headers $Headers -ContentType "application/json"
    Write-Host "PASS: Session Booked successfully. Session ID: $($BookingResponse.id)" -ForegroundColor Green
} catch {
    Write-Host "FAIL: Booking failed to execute: $_" -ForegroundColor Red
}

# --- 6. Cancel renewal ---
Write-Host "Cancelling renewal..." -ForegroundColor Cyan
$CancelResponse = Invoke-RestMethod -Uri "$BaseUrl/subscriptions/$SubId/cancel-renewal" -Method Post -Headers $Headers
Write-Host "Auto-renewal status changed to: $($CancelResponse.autoRenew)" -ForegroundColor Green
```
