# Backend Manual QA Test Plan

This document outlines the manual test plan for verifying the MVP flows of the coaching platform backend. 

---

## 1. Auth/Login Basic Flow

### Test 1.1: User Registration
*   **Test Name:** `Auth - Register New User`
*   **Purpose:** Verify that a new student or coach can register successfully.
*   **Preconditions:** Email is not already registered in the database.
*   **Endpoint:** `/api/v1/auth/register`
*   **Method:** `POST`
*   **Required Role/Token:** Public (No token required)
*   **Request Body:**
    ```json
    {
      "email": "student1@example.com",
      "password": "SecurePassword123",
      "fullName": "John Doe",
      "role": "STUDENT"
    }
    ```
*   **Expected HTTP Response:** `201 Created`
*   **Expected Database/Status Result:** A new record is created in the `users` table with status `ACTIVE`.
*   **Notes / Edge Cases:** 
    *   Verify password must be at least 8 characters.
    *   Verify role must be either `STUDENT` or `COACH`.

### Test 1.2: User Login
*   **Test Name:** `Auth - User Login`
*   **Purpose:** Verify that an existing user can log in and receive JWT tokens.
*   **Preconditions:** User is registered and status is `ACTIVE`.
*   **Endpoint:** `/api/v1/auth/login`
*   **Method:** `POST`
*   **Required Role/Token:** Public (No token required)
*   **Request Body:**
    ```json
    {
      "email": "student1@example.com",
      "password": "SecurePassword123"
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** No database changes. Response contains `accessToken` and `refreshToken`.
*   **Notes / Edge Cases:** Verify correct credentials return tokens, wrong password returns `401 Unauthorized`.

### Test 1.3: Token Refresh
*   **Test Name:** `Auth - Token Refresh`
*   **Purpose:** Verify that a client can obtain a new access token using a valid refresh token.
*   **Preconditions:** User has a valid `refreshToken` obtained from login.
*   **Endpoint:** `/api/v1/auth/refresh`
*   **Method:** `POST`
*   **Required Role/Token:** Public (No token required)
*   **Request Body:**
    ```json
    {
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** A new access token and refresh token are returned in the response.
*   **Notes / Edge Cases:** Passing an expired/invalid refresh token should return `401 Unauthorized`.

### Test 1.4: Current User Retrieval
*   **Test Name:** `Auth - Get Current User Details`
*   **Purpose:** Verify an authenticated user can retrieve their profile info.
*   **Preconditions:** Valid `accessToken` in authorization header.
*   **Endpoint:** `/api/v1/auth/me`
*   **Method:** `GET`
*   **Required Role/Token:** Authenticated user (`Bearer <accessToken>`)
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** Returns JSON matching user's profile info (ID, email, name, role).
*   **Notes / Edge Cases:** Request without a token or with an invalid token returns `401 Unauthorized` / `403 Forbidden`.

---

## 2. Subscription Checkout Flow

### Test 2.1: Initiate Checkout
*   **Test Name:** `Subscription - Initiate Checkout`
*   **Purpose:** Initiate the purchase flow for a coaching package, creating a pending subscription.
*   **Preconditions:** 
    *   Coach exists and has capacity.
    *   Package exists.
    *   Student has logged in.
*   **Endpoint:** `/api/v1/subscriptions/checkout`
*   **Method:** `POST`
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Request Body:**
    ```json
    {
      "coachId": 1,
      "packageId": 2
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** 
    *   A subscription record is created in the `subscriptions` table with status `PENDING_PAYMENT`.
    *   A payment record is created in the `payments` table with status `PENDING`.
*   **Notes / Edge Cases:** 
    *   Response should contain the `checkoutFormContent` (HTML form code from Iyzico) or a redirect/payment token.
    *   Coach capacity is NOT incremented/decremented at this point.

---

## 3. PENDING_PAYMENT Restrictions

### Test 3.1: Block Chat for Pending Payment Student
*   **Test Name:** `Access Control - Block Chat Access`
*   **Purpose:** Ensure students cannot connect to websocket or send STOMP messages while their subscription is `PENDING_PAYMENT`.
*   **Preconditions:** Student has a subscription with `PENDING_PAYMENT` status but no active subscriptions.
*   **Endpoint:** `/topic/conversations/{conversationId}` (Stomp SUBSCRIBE) or sending messages
*   **Method:** STOMP protocol frame
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Expected HTTP Response:** STOMP Error Frame returned, connection closed or subscription rejected.
*   **Expected Database/Status Result:** No database changes. Message is blocked.

### Test 3.2: Block Session Booking for Pending Payment Student
*   **Test Name:** `Access Control - Block Booking Access`
*   **Purpose:** Ensure students cannot book coaching sessions while their subscription is `PENDING_PAYMENT`.
*   **Preconditions:** Student has `PENDING_PAYMENT` subscription.
*   **Endpoint:** `/api/v1/sessions`
*   **Method:** `POST`
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Request Body:**
    ```json
    {
      "availabilitySlotId": 10
    }
    ```
*   **Expected HTTP Response:** `403 Forbidden` or `400 Bad Request` (depending on exception type, typically a domain validation exception).
*   **Expected Database/Status Result:** No session booking is created.

---

## 4. Stub Payment Success Flow

### Test 4.1: Transition Pending Payment to Success via Stub
*   **Test Name:** `Payment - Succeed Pending Stub Payment`
*   **Purpose:** Manually complete a pending stub payment to simulate a successful client redirect/payment.
*   **Preconditions:** A payment record with ID `{paymentId}` exists with status `PENDING` and a corresponding subscription with `PENDING_PAYMENT`.
*   **Endpoint:** `/api/v1/payments/{paymentId}/stub/succeed`
*   **Method:** `POST`
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:**
    *   Payment status transitions from `PENDING` to `SUCCESS`.
    *   Subscription status transitions from `PENDING_PAYMENT` to `ACTIVE`.
    *   Coach profile active student capacity is incremented.
*   **Notes / Edge Cases:** Calling this endpoint for a payment that is already `SUCCESS` must be handled gracefully or reject.

---

## 5. Iyzico Webhook Success/Failure Flow

### Test 5.1: Process Webhook Success
*   **Test Name:** `Webhook - Iyzico Webhook Payment Success`
*   **Purpose:** Verify that a public webhook notification from Iyzico successfully marks a payment as successful.
*   **Preconditions:** 
    *   Payment record `{paymentId}` has status `PENDING`.
    *   Subscription has status `PENDING_PAYMENT`.
*   **Endpoint:** `/api/v1/payments/iyzico/webhook`
*   **Method:** `POST`
*   **Required Role/Token:** Public (No token required, but signed/secure parsing is verified)
*   **Request Body:**
    ```json
    {
      "paymentId": 12345,
      "status": "SUCCESS",
      "providerReference": "iyz_ref_111222"
    }
    ```
*   **Expected HTTP Response:** `200 OK` (Or response containing `status: OK`)
*   **Expected Database/Status Result:**
    *   Payment status transitions to `SUCCESS`.
    *   Subscription status transitions to `ACTIVE`.
    *   Coach profile active student count is incremented.

### Test 5.2: Process Webhook Failure
*   **Test Name:** `Webhook - Iyzico Webhook Payment Failure`
*   **Purpose:** Verify that a public webhook notification from Iyzico for a failed payment updates statuses correctly.
*   **Preconditions:** Payment record exists with status `PENDING`.
*   **Endpoint:** `/api/v1/payments/iyzico/webhook`
*   **Method:** `POST`
*   **Required Role/Token:** Public
*   **Request Body:**
    ```json
    {
      "paymentId": 12345,
      "status": "FAILED",
      "providerReference": "iyz_ref_failed"
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:**
    *   Payment status transitions to `FAILED`.
    *   Subscription status transitions to `FAILED` or remains `PENDING_PAYMENT` depending on retry rules.
    *   Coach capacity is NOT incremented.

---

## 6. Cancel-Renewal Flow

### Test 6.1: Cancel Auto-Renewal
*   **Test Name:** `Subscription - Cancel Auto-Renewal`
*   **Purpose:** Allow students to turn off auto-renewal for their active subscription.
*   **Preconditions:** Subscription `{id}` is `ACTIVE` and `autoRenew` is `true`.
*   **Endpoint:** `/api/v1/subscriptions/{id}/cancel-renewal`
*   **Method:** `POST`
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** 
    *   `auto_renew` column for the subscription is updated to `false`.
    *   Subscription status remains `ACTIVE` (remains active until expiration date).
*   **Notes / Edge Cases:** Students cannot cancel auto-renewal for subscriptions belonging to other students (returns `403 Forbidden`).

---

## 7. Admin Refund Flow

### Test 7.1: Process Successful Refund
*   **Test Name:** `Admin - Trigger Payment Refund`
*   **Purpose:** Allow an administrator to refund a successful payment and cancel the active subscription.
*   **Preconditions:** Payment `{paymentId}` has status `SUCCESS` and is linked to an `ACTIVE` subscription.
*   **Endpoint:** `/api/v1/admin/payments/{paymentId}/refund`
*   **Method:** `POST`
*   **Required Role/Token:** Admin Role (`Bearer <adminToken>`)
*   **Request Body:**
    ```json
    {
      "amount": 150.00,
      "reason": "Student requested cancellation"
    }
    ```
*   **Expected HTTP Response:** `200 OK` (with refund response payload)
*   **Expected Database/Status Result:**
    *   The payment is marked as `REFUNDED` (or refund state fields updated).
    *   The corresponding subscription is updated to `REFUNDED` (or terminated status).
    *   Coach profile active student capacity is decremented.

---

## 8. Consent Creation

### Test 8.1: Record User Legal Consent
*   **Test Name:** `Consent - Record KVKK Consent`
*   **Purpose:** Record a user's consent to the platform's KVKK policies.
*   **Preconditions:** User is logged in.
*   **Endpoint:** `/api/v1/consents`
*   **Method:** `POST`
*   **Required Role/Token:** Authenticated user (`Bearer <userToken>`)
*   **Request Body:**
    ```json
    {
      "consentType": "KVKK",
      "documentVersion": "v1.0"
    }
    ```
*   **Expected HTTP Response:** `201 Created`
*   **Expected Database/Status Result:** A new row is inserted into `consent_records` with the user ID, consent type, version, ip address, and timestamp.

---

## 9. Report Creation

### Test 9.1: Submit User Safety Report
*   **Test Name:** `Safety - Create Safety Report`
*   **Purpose:** Allow any user to submit a safety or violation report.
*   **Preconditions:** User is logged in.
*   **Endpoint:** `/api/v1/reports`
*   **Method:** `POST`
*   **Required Role/Token:** Authenticated user (`Bearer <userToken>`)
*   **Request Body:**
    ```json
    {
      "targetType": "USER",
      "targetId": 2,
      "reason": "Inappropriate chat messages",
      "details": "User sent abusive messages during coaching."
    }
    ```
*   **Expected HTTP Response:** `201 Created`
*   **Expected Database/Status Result:** A new row is added to the `reports` table with status `OPEN`.

---

## 10. Admin Report List

### Test 10.1: View and Filter Reports
*   **Test Name:** `Admin - List and Filter Safety Reports`
*   **Purpose:** Allow admins to fetch all reports, with optional filtering by status.
*   **Preconditions:** At least one report exists in the database.
*   **Endpoint:** `/api/v1/admin/reports?status=OPEN`
*   **Method:** `GET`
*   **Required Role/Token:** Admin Role (`Bearer <adminToken>`)
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** Paginated list of safety reports matches the request criteria.

---

## 11. User Suspend Flow

### Test 11.1: Suspend User Account
*   **Test Name:** `Admin - Suspend User`
*   **Purpose:** Allow an administrator to suspend a student or coach.
*   **Preconditions:** User exists and status is `ACTIVE`.
*   **Endpoint:** `/api/v1/admin/users/{id}/suspend`
*   **Method:** `POST`
*   **Required Role/Token:** Admin Role (`Bearer <adminToken>`)
*   **Request Body:**
    ```json
    {
      "reason": "Violated platform safety rules"
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:**
    *   User status in `users` table updates to `SUSPENDED`.
    *   `suspension_reason` field is populated with the provided reason.

---

## 12. Suspended User Access Blocking

### Test 12.1: Block API Requests
*   **Test Name:** `Security - Block Suspended User REST Access`
*   **Purpose:** Ensure suspended users cannot make REST API requests.
*   **Preconditions:** User is `SUSPENDED`.
*   **Endpoint:** Any protected API (e.g. `GET /api/v1/auth/me` or `GET /api/v1/sessions/me`)
*   **Method:** `GET`
*   **Required Role/Token:** Suspended user token (`Bearer <suspendedUserToken>`)
*   **Expected HTTP Response:** `403 Forbidden`
*   **Expected Database/Status Result:** Blocked response containing error code `USER_SUSPENDED`.

### Test 12.2: Block WebSocket Connection
*   **Test Name:** `Security - Block Suspended User Stomp Connection`
*   **Purpose:** Ensure suspended users cannot establish a WebSocket connection.
*   **Preconditions:** User is `SUSPENDED`.
*   **Endpoint:** `/ws` (Stomp connection endpoint)
*   **Method:** WebSocket connection attempt carrying JWT token of suspended user.
*   **Required Role/Token:** Suspended user token
*   **Expected HTTP Response:** Connection handshake is rejected, returning STOMP error frame `Hesabınız askıya alınmıştır`.

---

## 13. Admin Subscription Terminate Flow

### Test 13.1: Admin Terminate Active Subscription
*   **Test Name:** `Admin - Terminate Subscription`
*   **Purpose:** Verify that an admin can terminate an active subscription.
*   **Preconditions:** Subscription with ID `{id}` is `ACTIVE`.
*   **Endpoint:** `/api/v1/admin/subscriptions/{id}/terminate`
*   **Method:** `POST`
*   **Required Role/Token:** Admin Role (`Bearer <adminToken>`)
*   **Request Body:**
    ```json
    {
      "reason": "Administrative intervention"
    }
    ```
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:**
    *   Subscription status transitions to `TERMINATED`.
    *   `termination_reason` field is set.
    *   Coach profile active student count is decremented.

---

## 14. Admin Conversation Access Audit Log

### Test 14.1: Log Admin Chat Oversight Access
*   **Test Name:** `Admin - Audit Log Conversation Access`
*   **Purpose:** Verify that when an admin views a thread, they must log their action.
*   **Preconditions:** Conversation exists.
*   **Endpoint:** `/api/v1/admin/conversations/{conversationId}/access-log`
*   **Method:** `POST`
*   **Required Role/Token:** Admin Role (`Bearer <adminToken>`)
*   **Request Body:**
    ```json
    {
      "reason": "Investigating abuse report"
    }
    ```
*   **Expected HTTP Response:** `200 OK` (or `201 Created` returning the log details)
*   **Expected Database/Status Result:** A new row is inserted into `admin_conversation_access_logs`.

---

## 15. Booking/Message Access Gates

### Test 15.1: Block Chat for Terminated Subscription
*   **Test Name:** `Access Control - Gate Terminated Chat Access`
*   **Purpose:** Verify messaging access is cut off when a subscription is `TERMINATED`.
*   **Preconditions:** Student's subscription was terminated; student has no other active subscriptions.
*   **Endpoint:** Sending a message or subscribing to `/topic/conversations/{conversationId}`
*   **Method:** WebSocket / STOMP
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Expected HTTP Response:** Connection rejected or STOMP Error Frame returned.

---

## 16. Coach Capacity Correctness

### Test 16.1: Capacity Increment/Decrement Flow
*   **Test Name:** `Business Logic - Coach Capacity Lifecycle`
*   **Purpose:** Verify coach capacity tracking across checkout, payment, cancellation, and termination.
*   **Preconditions:** Coach has initial active student count `N`.
*   **Execution Steps:**
    1.  Student performs subscription checkout -> Verify capacity remains `N`.
    2.  Student completes stub payment success -> Verify capacity becomes `N + 1`.
    3.  Admin terminates subscription -> Verify capacity returns to `N`.
    4.  Verify capacity never drops below `0` (even on multiple refund/termination inputs).

---

## 17. Iyzico Sandbox Checkout Test

### Test 17.1: Real Sandbox Payment Simulation
*   **Test Name:** `Integration - Sandbox Checkout Card Payment`
*   **Purpose:** Guide on testing real sandbox card checkout.
*   **Preconditions:** Iyzico sandbox configurations are set in `application.yml`.
*   **Steps:**
    1.  Call `POST /api/v1/subscriptions/checkout` to receive the sandbox form token and `checkoutFormContent`.
    2.  Render the returned Iyzico HTML form on a client page or open the sandbox checkout URL.
    3.  Submit standard Iyzico sandbox test card credentials:
        *   **Card Number:** `5430 0000 0000 0000` (or any other official sandbox card)
        *   **Expiry Date:** `12/30`
        *   **CVC:** `123`
    4.  Complete payment inside the Iyzico iframe -> redirects to the success page.
    5.  Check subscription status in the database -> status should have transitioned to `ACTIVE`.

---

## 18. Error Cases and Duplicate/Idempotency Cases

### Test 18.1: Slot Double Booking Prevention
*   **Test Name:** `Error - Double Booking Prevention`
*   **Purpose:** Ensure a coach's availability slot cannot be booked by two users simultaneously.
*   **Preconditions:** Coach availability slot is already booked.
*   **Endpoint:** `/api/v1/sessions`
*   **Method:** `POST`
*   **Required Role/Token:** Student Role (`Bearer <studentToken>`)
*   **Request Body:**
    ```json
    {
      "availabilitySlotId": 10
    }
    ```
*   **Expected HTTP Response:** `409 Conflict` (or `400 Bad Request` specifying slot already booked)
*   **Expected Database/Status Result:** Duplicate session is rejected.

### Test 18.2: Webhook Duplicate/Replay Safety
*   **Test Name:** `Idempotency - Webhook Replay Protection`
*   **Purpose:** Ensure duplicate webhook calls do not trigger duplicate capacity or status updates.
*   **Preconditions:** A webhook request for a payment was already successfully processed.
*   **Endpoint:** `/api/v1/payments/iyzico/webhook`
*   **Method:** `POST`
*   **Request Body:** Send the identical JSON body from the previous successful webhook call.
*   **Expected HTTP Response:** `200 OK`
*   **Expected Database/Status Result:** Returns success, no status updates are repeated, and coach capacity does NOT double-increment.

### Test 18.3: Refund Bounds Validation
*   **Test Name:** `Error - Refund Amount Bounds Check`
*   **Purpose:** Verify that admins cannot refund an amount greater than the original payment amount.
*   **Preconditions:** A successful payment of `150.00` exists.
*   **Endpoint:** `/api/v1/admin/payments/{paymentId}/refund`
*   **Method:** `POST`
*   **Required Role/Token:** Admin Role
*   **Request Body:**
    ```json
    {
      "amount": 200.00,
      "reason": "Exceeded amount"
    }
    ```
*   **Expected HTTP Response:** `400 Bad Request`
*   **Expected Database/Status Result:** Refund is rejected, payment status remains `SUCCESS`.
