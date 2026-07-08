# Iyzico Sandbox Integration Testing Guide

This guide provides step-by-step instructions on setting up and testing the real Iyzico sandbox checkout flows in a local environment.

---

## 1. Goal

This guide details how to verify the initialization and execution of the **Iyzico Sandbox Checkout** flow. 
*   **Testing Mode:** Sandbox only. This is not a live production payment, and no real currency is moved.
*   **Stub vs. Sandbox vs. Production:**
    *   **Stub (Development):** Fakes all payments instantly via a developer stub endpoint (`POST /api/v1/payments/{paymentId}/stub/succeed`). No connection is made to Iyzico.
    *   **Sandbox (Testing):** Connects to the real Iyzico Sandbox API environment. It renders the official checkout form and validates test card numbers.
    *   **Production (Live):** Moves real money using live credentials, keys, and cards.

---

## 2. Required Credentials

To use the Iyzico sandbox, you need credentials from your developer dashboard at [iyzico.com](https://www.iyzico.com). 

*   `IYZICO_API_KEY`: Your sandbox API key.
*   `IYZICO_SECRET_KEY`: Your sandbox secret key.
*   `IYZICO_BASE_URL`: Must point to the sandbox URL: `https://sandbox-api.iyzipay.com`.
*   `IYZICO_CALLBACK_URL`: Webhook callback URL where Iyzico sends transaction notifications.
*   `PAYMENTS_IYZICO_ENABLED`: Setting to toggle using Iyzico client (`true`) or Stub mode (`false`).

> [!WARNING]
> Never commit keys or secrets to Git or share them in public channels. Always load them via environment variables or external properties files.

---

## 3. Local Environment Setup with PowerShell

Before running the application, set up your shell environment variables in PowerShell:

```powershell
# Set sandbox properties
$env:IYZICO_API_KEY = "sandbox-api-key-from-dashboard"
$env:IYZICO_SECRET_KEY = "sandbox-secret-key-from-dashboard"
$env:IYZICO_BASE_URL = "https://sandbox-api.iyzipay.com"
$env:IYZICO_CALLBACK_URL = "http://localhost:8080/api/v1/payments/iyzico/webhook"
$env:PAYMENTS_IYZICO_ENABLED = "true"
```

---

## 4. Starting the Backend

Start the Spring Boot application from the root directory of the workspace where the environment variables were set:

```powershell
./mvnw.cmd spring-boot:run
```

**Expected Successful Startup Output:**
You should see logging indicating initialization, database migrations executing, and the server listening:
```text
INFO 12345 --- [main] com.ykskocluk.demo.DemoApplication  : Started DemoApplication in 5.432 seconds
```

---

## 5. Getting a Student JWT Token

To make authenticated API calls, login as an active student to obtain their token:

```powershell
$LoginBody = @{
    email = "student1@example.com"
    password = "SecurePassword123"
} | ConvertTo-Json

$AuthResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $LoginBody -ContentType "application/json"
$StudentToken = $AuthResponse.accessToken
Write-Host "Student Token: $StudentToken"
```

---

## 6. Creating a Checkout

Create a pending subscription and obtain the Iyzico sandbox checkout details:

```powershell
$CheckoutBody = @{
    coachId = 1
    packageId = 2
} | ConvertTo-Json

$Headers = @{ Authorization = "Bearer $StudentToken" }

$CheckoutResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/subscriptions/checkout" -Method Post -Body $CheckoutBody -Headers $Headers -ContentType "application/json"

# Print checkout details
$CheckoutResponse | Format-List
```

**Expected Response Schema:**
```json
{
  "subscriptionId": 5,
  "paymentId": 10,
  "subscriptionStatus": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "amount": 150.00,
  "checkoutToken": "token-hash-goes-here",
  "checkoutUrl": "https://sandbox-api.iyzipay.com/checkout..."
}
```

---

## 7. Opening checkoutUrl

Open the retrieved sandbox checkout form directly in your default browser:

```powershell
Start-Process $CheckoutResponse.checkoutUrl
```

This will load the official Iyzico payment screen showing the package amount and secure inputs.

---

## 8. Sandbox Payment Card Test

1.  When the checkout page loads, enter standard mock billing info.
2.  Use the official Iyzico sandbox test cards available on the Iyzico developer dashboard or documentation, e.g.:
    *   **Card Number:** `5430 0000 0000 0000` (valid sandbox Mastercard)
    *   **Expiry Date:** Enter any valid future date (e.g. `12 / 30`)
    *   **Security Code (CVC):** `123`
3.  **Submit Payment:**
    *   **Success Case:** The browser should redirect to the success callback page configured for your subscription.
    *   **Failure Case:** Using incorrect sandbox credentials (e.g., card numbers ending in different test profiles) should display validation failure inside the Iyzico form.

---

## 9. Webhook/Callback Limitation with Localhost

Iyzico sandbox operates on the cloud/internet. Therefore, **Iyzico servers cannot reach a localhost callback URL** (e.g., `http://localhost:8080`) to deliver webhook notifications.

To test the complete end-to-end webhook integration locally:
*   You must expose your local server to the public internet using a secure tunneling client (e.g., **ngrok** or **Localtunnel**).
*   Alternatively, deploy the branch to a public test environment where the backend is accessible via a public domain name.

---

## 10. Public Callback / Tunnel Test Plan

1.  Start your public tunnel to point to port `8080` (e.g. `ngrok http 8080`).
2.  Retrieve the public URL generated (e.g., `https://your-public-tunnel.ngrok-free.app`).
3.  Update the callback environment variable:
    ```powershell
    $env:IYZICO_CALLBACK_URL = "https://your-public-tunnel.ngrok-free.app/api/v1/payments/iyzico/webhook"
    ```
4.  Restart the backend server (`./mvnw.cmd spring-boot:run`).
5.  Perform the checkout and payment steps. Upon success, Iyzico will reach the tunnel domain, forwarding the webhook request directly to your local instance.

---

## 11. Expected Database/Status Results

Verify database tables state during execution:

| Stage | Subscription Status | Payment Status | Coach Student Count |
| :--- | :--- | :--- | :--- |
| **1. Checkout created** | `PENDING_PAYMENT` | `PENDING` | Unchanged |
| **2. Successful Webhook Callback** | `ACTIVE` | `SUCCESS` | Incremented by 1 |
| **3. Failed Webhook Callback** | `PENDING_PAYMENT` | `FAILED` | Unchanged |
| **4. Refund executed** | `REFUNDED` / `TERMINATED` | `REFUNDED` | Decremented by 1 |

---

## 12. Troubleshooting

*   **Missing Credentials:** If `IyzicoProperties` reads empty/null fields, checkout initialization throws a `400 Bad Request` or null pointer exception. Verify environment variables are set correctly in the context of the running command prompt.
*   **Wrong Base URL:** If set to production URL, payment forms will reject sandbox test credit cards.
*   **401/403 Error on Checkout:** Ensure you are sending the `Authorization: Bearer <accessToken>` header with a valid student account token.
*   **Webhook Not Received:** Double check your tunnel URL is open and active, and matches the callback URL stored during checkout.
*   **DB Status Does Not Transition:** If payment succeeds in browser but DB states do not change, check logs for signature verification failures or invalid transaction ID lookups.

---

## 13. Safety Notes

*   **Never commit sandbox or production credentials** to the codebase.
*   **Never input a real, active credit card** in the sandbox page.
*   **Ensure `PAYMENTS_IYZICO_ENABLED` is set to `false` in default config profiles** to prevent unit tests or staging builds from attempting cloud client connections.
