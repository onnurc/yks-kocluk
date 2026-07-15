# Phase 0.5 — Risk PoC Findings (throwaway spike)

**Date:** 2026-06-17
**Status:** Phase 0.5 complete. No live tests run (both spikes gated on unavailable
credentials/accounts). No production code, no permanent dependencies, `src/main` untouched.
Real integrations remain deferred to their own phases (iyzico → Phase 8, Meet → Phase 6),
behind the `IyzicoClient` / `MeetClient` interfaces.

---

## Spike 1 — iyzico single charge

**Outcome: BLOCKED (not failed).** iyzico sandbox application is still pending approval,
so no API key/secret are available. The live sandbox charge could not be executed.

- **What was to be proven:** a single sandbox charge (test card `5528790000000008`,
  future expiry, CVV `123`) against `https://sandbox-api.iyzipay.com` returns
  `status="success"` with a real `paymentId`. Webhook handling intentionally out of scope.
- **Unblock requires:** approved iyzico **sandbox merchant account → API key + secret**.
  Once available, run the spike (temporary test-scope `com.iyzipay:iyzipay-java`) to confirm
  a real `paymentId` before building anything on top.
- **Impact on main project: none.** Development continues with the **`IyzicoClient` stub**.
  The real implementation is built in **Phase 8 (Payment)** behind that interface, so the
  swap is cheap and nothing downstream is blocked today.
- **Risk note:** iyzico onboarding/approval can be slow — keep the sandbox application moving
  in parallel so Phase 8 isn't gated on it.

---

## Spike 2 — Google Meet link generation

**Outcome: Google Meet API NOT viable for us → fallback decided (Jitsi).**

- **Key question — does a personal Google account's service account work, or is Workspace
  required?** **Answer: Google Workspace is required.** Generating a Meet link via the
  Calendar API (`conferenceData.createRequest`, solution `hangoutsMeet`,
  `conferenceDataVersion=1`) must run in a **Workspace user context** — i.e. a service
  account with **domain-wide delegation impersonating a Workspace user**. A service account
  on a **personal/free Gmail** account cannot mint `meet.google.com` links. We only have a
  personal Gmail account → the Google Meet path is unavailable.

- **Fallback decision: (b) Jitsi.** Auto-generate `https://meet.jit.si/{UUID}`.
  - **Zero API dependency / zero credentials** — no Workspace cost, no service account,
    no OAuth setup.
  - **Preserves auto-generation** (better UX, fewer no-shows than asking coaches to paste
    their own links — option (a)).
  - **Cheap `MeetClient` swap:** the stub already returns a fake link; the Jitsi version just
    returns `meet.jit.si/{UUID}`. No call-site changes.
  - **Child-safety caveat (minors-facing platform):** room ids must stay **unguessable
    (UUID)**; consider adding a Jitsi **lobby/password** and never expose the link outside the
    authenticated student↔coach relationship. Revisit hardening in Phase 6 / Phase 9.
  - **Fallback-to-the-fallback:** manual link field (coach pastes a Meet/Zoom link) stays
    available if a later child-safety review insists on coach-owned, access-controlled rooms.

- **Production decision is still deferred to Phase 6 (Session/Meet)** behind `MeetClient`:
  if a Workspace account becomes available, real Google Meet can replace Jitsi without
  touching call sites. Until then, **Phase 6 implements the Jitsi `MeetClient`.**

---

## Summary

| Spike | Result | Next step |
|---|---|---|
| iyzico | Blocked — sandbox approval pending | Get sandbox API key+secret; verify a real `paymentId`; build real client in Phase 8. Stub until then. |
| Google Meet | Workspace required, unavailable | Use **Jitsi** (`meet.jit.si/{UUID}`) behind `MeetClient`, implemented in Phase 6. |

**Decisions recorded:** continue on `IyzicoClient` stub; Meet fallback = Jitsi auto-generation.
