# Handoff — YKS Coaching Platform (Backend)

## Automatic meet links switched off — coach sends a Google Meet link over chat (2026-09-17)

**Decision:** the platform no longer generates a meeting link. The coach creates a Google Meet
link themselves and sends it to the student **through in-platform chat**. No Google Calendar/Meet
API is used (the Phase 0.5 finding still stands — it needs a Workspace account). This supersedes
the "Phase 6 — Real video: Jitsi" section further down this file, which stays as the record of how
the Jitsi implementation worked.

**Nothing was deleted.** `MEET_LINK_ENABLED` (`app.meet-link.enabled`, default **false**) gates the
behaviour only; the whole seam is preserved for a future Google Meet integration:

- `MeetClient` / `JitsiMeetClient` / `StubMeetClient`, `sessions.meet_link` (V8), `Session.meetLink`
  and `SessionResponse.meetLink` all remain. Flipping the flag to `true` restores the old behaviour
  with **no schema, DTO or wiring change**.
- The flag deliberately does **not** condition the bean away (`@ConditionalOnProperty` was rejected):
  `SessionNotificationListener` keeps a single non-optional `MeetClient` dependency, and the seam is
  what a future Google Meet client plugs into. Two layers enforce it instead — `JitsiMeetClient`
  returns `null` without minting anything, and the listener skips the create/persist step entirely
  (so no client call and no DB write, not merely a discarded link).

**The trap this created, and the fix — read this before touching the reminder job.**
`SessionRepository.findReminderCandidates` required `meetLink is not null`, written when links
appeared automatically seconds after booking ("skip until it lands"). With generation off *nothing
ever gets a link*, so that condition would have silently suppressed **every** session reminder — no
error, no log, just no mail. The query now takes a `requireMeetLink` parameter fed from the flag
(`SessionService.findReminderCandidates`): off → the condition is dropped, on → the original
behaviour is byte-identical. Covered by `SessionRepositoryTest` (real PostgreSQL) plus
`SessionReminderJobTest#runReminders_candidateWithoutMeetLink_stillSendsReminder`.

**Mails.** `sendSessionBooked`, `sendSessionBookedToCoach` and `sendSessionReminder` no longer
render `<a href="">` when there is no link. The student's copy says the coach will share the link
over messages; the coach's copy tells them to create the Google Meet link and send it to the
student. See `ResendMailClient.studentJoinBlock` / `coachJoinBlock`.

**Frontend.** Student/coach/admin views keep their `meetLink` handling and only changed wording for
the null case ("Bağlantı mesajlarda paylaşılacak"). `ChatPage` now linkifies message text via
`messaging/linkifyMessage.tsx`: **only** `http://`/`https://` become anchors (`target="_blank"`,
`rel="noopener noreferrer"`), everything else — `javascript:`, `data:`, bare `www.` — stays inert
text, and it returns React nodes rather than using `dangerouslySetInnerHTML`.

**Known gap, NOT fixed (flagged 2026-09-17).** A coach can be unable to reach their own student:
`Conversation` rows are created *only* by `MessageService.openConversation`, which is reachable
only through `POST /api/v1/conversations` (`@PreAuthorize("hasRole('STUDENT')")`). Booking a
session does not create one. So a student who subscribes and books but never opens the chat leaves
the coach with no conversation to send into (`requireParticipant` → `CONVERSATION_NOT_FOUND`) —
and the meet link now travels *only* over chat. Secondary case: `sendMessage` calls
`accountReadinessService.requireReady(sender)` for the coach too, so an unverified-email or
incomplete-legal-onboarding coach is blocked from sending. Needs a product decision (coach-side
conversation initiation, or auto-creating the conversation at subscription/booking time).

## Port 8080 is intercepted by a local proxy — WebSocket breaks, HTTP does not (2026-08-16)

**If chat "doesn't connect" on your machine, check this before touching any code.** On the
owner's Mac, everything on `localhost:8080` is transparently routed through a proxy listening on
`127.0.0.1:10011`. That proxy passes plain HTTP through untouched but corrupts the WebSocket byte
stream *after* the upgrade. The application code is fine — the exact same build works on any other
port.

**Symptom.** REST works, `GET /ws/info` returns 200, the handshake completes with `101 Switching
Protocols` — and then the first client→server frame dies:

```
CloseStatus[code=1002, reason=A WebSocket frame was sent with an unrecognised opCode of [7]]
CloseStatus[code=1002, reason=The client frame set the reserved bits to [4] for a message with opCode [7] ...]
```

No STOMP `CONNECTED`, no STOMP `ERROR` frame, and `WebSocketMessageBrokerStats` reports
`stompSubProtocol[processed CONNECT(0)-CONNECTED(0)]` — the frame never reaches the STOMP layer.
In Chrome DevTools (Network → WS → Messages) it looks like the CONNECT frame goes out and the
server simply never answers; Chrome does not surface the close frame there, which is what makes
this so confusing. The frontend just sits on "Bağlanıyor…" and retries with backoff forever.

**Quickest check — one command, no app needed.** If the source port the server sees differs from
the port the client bound, something is proxying:

```bash
node -e 'const n=require("net"),p=+process.argv[1];let c;const s=n.createServer(k=>{console.log("client port:",c.localPort,"server sees:",k.remotePort,c.localPort===k.remotePort?"-> direct":"-> PROXY IN THE MIDDLE");process.exit(0)});s.listen(p,()=>{c=n.connect(p,"127.0.0.1",()=>c.write("x"))})' 8080
```

**How it was tracked down** (each step ruled out a whole class of cause):

1. All three clients — Chrome, Node `ws`, and the Tomcat Java client the tests use — failed
   identically on 8080. Not a client bug.
2. Raw WebSocket and SockJS both failed; `permessage-deflate` off, Origin removed, frame sizes
   36/241/313 bytes, delayed sends — no change. Not framing, compression, or timing.
3. `forward-headers-strategy=none`, plain `java -cp` instead of `spring-boot:run` (so no
   `-XX:TieredStopAtLevel=1`), browser traffic stopped — no change. Not app or JVM config.
4. Two instances of the same build running **simultaneously** on 8080 and 8099: 8099 completed a
   full STOMP round trip, 8080 failed. Only the port differed.
5. A bare Node `ws` echo server — no Tomcat, no Spring, no Java — also broke on 8080 and worked on
   8082. Proved the corruption is external to the application entirely.
6. Plain TCP bytes on 8080 arrive intact and the 101 response headers are byte-identical to what
   the server emitted, so the interceptor is HTTP-aware and only mangles the upgraded stream.
7. The source-port check above showed a mismatch on 8080 and a match on 8082, and `lsof` showed
   the client socket connected to `127.0.0.1:10011` rather than to 8080.

**What the proxy is.** **macOS Screen Time's web content filter**, identified by the project owner
after the investigation narrowed it to a listener on port 10011. It is a system-level content
filter, which is why it intercepts every process on the machine (Node, Java and Chrome alike),
survives killing every browser, and cannot be attributed with a plain `lsof` — the listener shows
up in `netstat -an | grep 10011` but needs elevated privileges to attribute:

```bash
sudo lsof -nP -i :10011 | grep LISTEN
```

Ruled out along the way: Antigravity's Chrome (`--remote-debugging-port=9222`) was killed outright
and made no difference, and there are no `pf` redirects, system extensions, `scutil` proxy settings
or Docker port bindings involved.

**To make port 8080 usable again**, turn off the Screen Time web content filter (System Settings →
Screen Time → Content & Privacy → Content Restrictions), or keep the backend on another port for
local dev. Note this is not specific to this project: any local WebSocket server on port 8080 on
this machine will break the same way, which is worth remembering on unrelated work.

**How to avoid it.** Nothing in this repo needs to change. Either stop/reconfigure whatever owns
port 10011, or run the backend on another port for local dev (`--server.port=8081`, and update
`frontend/.env`'s `VITE_API_BASE_URL` plus the `/ws` proxy target in `frontend/vite.config.ts`
together, or the frontend will talk to the wrong place). Ports 8081, 8082 and 8099 were all
verified clean. Do not "fix" this by changing WebSocket, Security or SockJS configuration — that
is how the false leads below got written.

**Two corrections to earlier notes in this file:**

- `scripts/ws_test.mjs` used to claim it used the SockJS URL format "to avoid a known Tomcat 11 +
  Node ws opCode compatibility quirk". That was wrong — the SockJS format bypasses nothing, and
  the failure is not Node-specific or Tomcat-specific. The comment has been removed and the script
  now takes the port as a parameter instead of hard-coding 8080.
- **RESOLVED (2026-08-16):** the `/ws/**` `WebSecurityCustomizer.ignoring()` in `SecurityConfig`
  had been introduced to fix this 1002 error. It never was a Spring Security problem, so that
  change rested on a false premise, and Spring Security warned about it at every boot ("You are
  asking Spring Security to ignore PathPattern [/ws/**]. This is not recommended -- please use
  permitAll via HttpSecurity#authorizeHttpRequests instead."). Reverted to
  `.requestMatchers("/ws/**").permitAll()` inside the filter chain and re-verified on a clean port
  (8081): full STOMP round trip OK, browser badge "Bağlı", `WebSocketAuthTest` 7/7, and the
  Spring warning is gone. `/ws/**` is back under the filter chain — no unnecessary security
  surface — while STOMP auth continues to happen in the CONNECT frame via
  `StompAuthChannelInterceptor`.

**Verified working** on 8081 with the same build, over the SockJS transport the browser uses —
CONNECT accepted, SUBSCRIBE authorized, SEND broadcast back over
`/topic/conversations/{id}` (`scripts/ws_roundtrip_probe.mjs <port>` reproduces this in one shot).

**Browser E2E, two live sessions (2026-08-16).** Student and coach in the same conversation, each
in its own origin so the two `localStorage` token sets don't collide (`localhost:5173` and a second
dev server on `localhost:5174`; the backend needs both in `CORS_ALLOWED_ORIGINS`). Confirmed:
connection badge "Bağlı" on both sides; student→coach and coach→student messages appear **without
a page reload**; the "Gönderiliyor…" pending indicator appears and clears when the broadcast
returns; timestamps render correctly; killing and restarting the backend flips the badge to
"Yeniden bağlanıyor…" and back to "Bağlı" on its own, and messages sent after the restart arrive
live. Two findings from that run:

- **The unread badge does not update live.** `AppLayout` fetches the count on mount and on the
  `messages-read` event only, so a message arriving while the user is on another page does not
  bump "Mesajlarım" until a full page reload (verified: badge stayed bare, then showed `1` after
  reload). The WS subscription is per-conversation, so a layout-level live count would need either
  a user-scoped topic or a refetch on any incoming message. Not a regression, just never wired.
- `POST /api/v1/conversations/{id}/read` shows as `204 [FAILED: net::ERR_ABORTED]` in dev. React
  StrictMode double-mounts the page and the first call is aborted; the second succeeds. Dev-only
  noise, no user-visible effect.

**`/api/v1/auth/me` 401s in the console are not a bug.** With an expired access token the sequence
is: two parallel `GET /auth/me` → both `401` (StrictMode double-invoke), one shared
`POST /auth/refresh` → `200`, then both retries → `200`. That is `httpClient.ts`'s single-flight
`ensureFreshToken` working as designed — the browser logs the 401s as console errors regardless.
The `"Unable to handle the Spring Security Exception because the response is already committed"`
error previously seen in the backend log was a symptom of the port-8080 corruption above (an async
SockJS transport request erroring after its response had started, while `/ws/**` was outside the
filter chain). It does not reproduce on a clean port with `permitAll`.

## OPEN — must resolve before prod: sockjs-client's withCredentials vs CORS allowCredentials=false (2026-08-15)

While wiring the frontend chat UI to the existing STOMP/WebSocket layer (`WebSocketConfig`,
`ChatStompController`), the SockJS transport (`sockjs-client`, used for the SockJS fallback
`WebSocketConfig` already registers) could not connect cross-origin at all:

- `sockjs-client`'s capability-check request (`GET /ws/info`) **unconditionally** sets
  `xhr.withCredentials = true` whenever it detects a cross-origin target
  (`abstract-xhr.js`: `if ((!opts || !opts.noCredentials) && AbstractXHRObject.supportsCORS)`).
  There is no public SockJS constructor option to turn this off — confirmed by reading the
  library source; `InfoReceiver` doesn't thread any such flag through to the XHR driver.
- The backend's CORS config (`SecurityConfig.corsConfigurationSource`) deliberately sets
  `configuration.setAllowCredentials(false)` (comment there: "OAuth2 uses top-level redirects,
  so cross-origin cookie credentials are unnecessary" — true for the REST/OAuth2 traffic that
  comment was written for, but SockJS's own precheck wasn't part of that reasoning).
- Result: the browser blocks the response — `Access-Control-Allow-Credentials` must be `true`
  when the request's credentials mode is `include`, and the server always sends `''`/omits it.
  SockJS never gets past `/info`, so the WebSocket layer never connects at all when frontend and
  backend are on different origins.

**Local dev workaround shipped (dev-only, does not fix prod):** `frontend/vite.config.ts` proxies
`/ws` through the Vite dev server so the browser's request is same-origin (no CORS involved at
all), and `useConversationSocket.ts` connects to `window.location.origin` instead of
`VITE_API_BASE_URL` when `import.meta.env.DEV`. This has no effect on a production build — there
is no dev proxy in the built static bundle, so `useConversationSocket.ts` falls back to
`VITE_API_BASE_URL` there, which is genuinely cross-origin in any real deployment (frontend and
backend on different domains, per the existing `CORS_ALLOWED_ORIGINS`/prod setup elsewhere in
this file).

**Must be resolved before shipping chat to prod**, by one of:
- Flip `allowCredentials` to `true` on the CORS config path(s) SockJS actually hits (broader
  surface than just `/ws` unless scoped carefully — needs a deliberate decision, not a reflexive
  flip, since the existing `false` was itself a deliberate choice for the REST/OAuth2 traffic).
- Serve frontend and backend from the same origin in prod (reverse proxy / same domain), making
  this moot the same way the dev proxy does.
- Any other approach that gets `/ws/info` responded to with matching `Allow-Credentials: true` +
  a specific (non-wildcard) `Allow-Origin`, which CORS requires together.

## Demo-seed accounts: manual DB recovery, the Flyway run-once trap, and closing the prod-exposure gap (2026-08-15)

**If you share the local/dev Neon DB with the owner: the six `*.demo@example.com` accounts
(`Password123!`) were manually re-inserted directly against that database today, 2026-08-15.**
If your app was already running against it, your `users`/`subscriptions`/`payments` rows for
these accounts may be stale relative to what's below — re-pull and restart, or just trust `V28`
(next point) to reconcile them correctly on your own next migration run once you also update
`application.yml`/`application-local.yml` per this section.

**What happened:** `DemoSeedCleanupComponent` deletes the demo-seed rows on every boot where
`app.demo-seed-enabled=false` (the default — see its own file for the full delete cascade). It
ran that way at some point before `DEMO_SEED_ENABLED: true` was added to
`application-local.yml`, deleting all six demo users and everything hanging off them
(conversations, messages, subscriptions, payments). Login for `student.active.demo@example.com`
then failed outright — the account simply didn't exist.

**Why flipping `DEMO_SEED_ENABLED` back to `true` didn't fix it:** Flyway migrations run exactly
once per database, tracked in `flyway_schema_history`. `V14`/`V15` were already recorded there as
`success` (2026-07-11) from the very first boot. No number of restarts — with any value of
`app.demo-seed-enabled` — makes Flyway re-run an already-applied migration. **This is the trap:**
a Flyway-seeded row that a runtime component can delete has no automatic path back. The fix has
to be either a fresh migration (which only helps future/other databases, not the one already
missing rows) or a manual write against the specific database that lost them — both were done
here.

**Fixes landed:**
- **`V28__reseed_demo_users.sql`** (`db/demo-seed/`) — idempotent version of the same seed
  (`ON CONFLICT DO NOTHING` where a unique constraint exists, `WHERE NOT EXISTS` where it
  doesn't), and it explicitly re-asserts `email_verified`/`legal_onboarding_completed` on
  existing rows rather than trusting the column default — the manual recovery today initially
  missed `legal_onboarding_completed` (defaults to `false`) because the accounts were re-inserted
  *after* `V19`'s one-time backfill `UPDATE` had already run; a plain re-insert doesn't get swept
  up in a backfill that already happened. `V28` closes that gap for good.
- **Closed an unrelated, pre-existing prod-exposure gap found while fixing this**:
  `application.yml`'s `spring.flyway.locations` included `classpath:db/demo-seed` in the
  **default** (profile-less) config. This project has no dedicated `prod` Spring profile —
  Railway runs the bare/default profile (see the Resend section below: `"prod" is detected as
  *not* local/test/stub`). So `db/demo-seed` (`V14`, `V15`, now `V28`) was never actually excluded
  from a real prod deploy — only `application-test.yml` opted out. A real deploy would have
  silently seeded a known-password `ADMIN` account into production. Fixed by flipping the
  approach to an **allowlist**: `application.yml`'s default is now `classpath:db/migration` only;
  `application-local.yml` (gitignored) and its committed `.example` template both explicitly add
  `classpath:db/migration,classpath:db/demo-seed` back for local dev. `stub` was left on the new,
  safer default (excluded) — it wasn't previously documented as needing demo-seed; flag if that's
  wrong.
- **Deploy-safety check, resolved 2026-08-15**: confirmed with the project owner that no Railway
  prod deploy and no separate prod database exist yet — the only database in use is this Neon
  dev DB. So there's no environment anywhere with `V14`/`V15` applied against a database this
  `flyway.locations` change would now hide them from; the flip is clean, nothing to migrate
  around. (The reasoning that made this worth checking first — Flyway's `validate` step failing
  with "detected applied migration not resolved locally" if a database already has rows for
  migrations that later drop out of the resolved location list — still applies in general and is
  worth remembering whenever `flyway.locations` changes on a database that's actually been
  deployed to.) See the next section for what's still needed before a *real* prod exists.

## Production readiness checklist: before the first real deploy

Written 2026-08-15, prompted by the demo-seed/prod-exposure fix above — this is not yet an
exhaustive go-live list, just what's been surfaced concretely by working on auth/messaging/OAuth
in this handoff so far.

- **Provision a separate Neon database for prod. Do not point a real deploy at this dev DB.**
  Beyond the general bad practice, it's now a concrete leak: this DB carries the
  `*.demo@example.com` accounts (`Password123!`, one of them `ADMIN`) plus whatever manual
  test data accumulates in it. `flyway.locations`'s allowlist (this section, above) stops
  `db/demo-seed` from *re-seeding* those rows on a fresh prod database — it does nothing about
  a prod deploy that's simply handed the dev database's connection string.
- **On that prod database, `spring.flyway.locations` resolves to `classpath:db/migration` only**
  — confirmed by the allowlist change above: Railway's bare/default profile (no `local`/`stub`
  override) gets `application.yml`'s default, and `db/demo-seed` is no longer part of it.
  Nothing further to do here as long as prod stays on the bare profile and doesn't set
  `spring.flyway.locations` itself.
- **Env vars to set before that first deploy** (each currently has a dev-only default or
  fallback that silently produces wrong-for-prod behavior rather than failing loudly — worth
  double-checking as a group right before cutover):
  - `RESEND_FROM` — `ResendConfig`'s fail-fast guard (`!local & !stub`) already throws at
    startup if this is unset, so this one *can't* be silently missed; listed here for
    completeness. See "Resend sender hardening" below for the guard's exact shape.
  - `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — `application.yml` defaults to
    `dummy-client-id`/`dummy-client-secret` (boots fine, Google login just silently doesn't
    work) rather than failing fast. No startup guard exists for these the way Resend has one.
  - `FRONTEND_BASE_URL`, `OAUTH2_FRONTEND_REDIRECT_URI`, `CORS_ALLOWED_ORIGINS` — all default to
    `localhost` origins/paths (see `application.yml`); already called out further down this file
    ("`/api/v1/health` remains public for Railway health checks…" section) but repeating here
    since they're easy to forget in the same pass as the items above.
  - `IYZICO_CALLBACK_URL` — `application.yml` defaults it to
    `http://localhost:8080/api/v1/payments/iyzico/webhook`, and `RealIyzicoClient.initializeCheckout`
    (`RealIyzicoClient.java:78`) falls back to that same hard-coded `localhost:8080` URL a
    *second* time if `properties.callbackUrl()` is ever blank — so even a misconfigured/blank env
    var doesn't fail loudly, it just quietly sends Iyzico a callback URL prod can never receive.
    Set it to the real public backend URL's `/api/v1/payments/iyzico/webhook` path, and consider
    whether that double localhost fallback is worth hardening to fail-fast instead (it currently
    isn't, unlike the Resend guard).

**General lesson for the next person touching demo/reference seed data:** Flyway's ledger
(run-once, checksummed) and a runtime component that conditionally deletes rows by a boolean flag
are a bad combination unless the seed migration is written to be safely re-runnable by hand (or
via a fresh migration) from the start. If you add another `DemoSeed*Component`-style cleanup or
another `db/demo-seed` migration, write it idempotent (`ON CONFLICT` / `WHERE NOT EXISTS`,
explicit field values, never a blind counter increment) the first time — see `V28` as the
reference shape.

## Resend domain verification landed: real from/reply-to (2026-08-15)

`uniformakademi.com` is now **Verified** in Resend (DNS/SPF/DKIM done). Closes the gap the two
Resend sections below both flagged as outstanding.

- **Real sender wired in `application-local.yml`** (gitignored, real values — not just the
  template): `app.resend.from: "Uniform Akademi <noreply@uniformakademi.com>"`,
  `app.resend.reply-to: "merhaba@uniformakademi.com"`. Set as nested `app.resend.*` keys directly
  rather than via the `RESEND_FROM`/`RESEND_REPLY_TO` env-var placeholders the rest of this file
  uses (`RESEND_API_KEY`, `JWT_SECRET`, …) — no indirection needed for a non-secret value.
  `noreply@` is a send-only sender label (no inbox behind it); `merhaba@` is the company's real
  Google Workspace inbox and is used only as reply-to, never as the from address.
  `application-local.yml.example` (the committed template) updated to match, replacing the old
  `onboarding@resend.dev` example.
- **`application.yml`'s default is unchanged on purpose** — `from: ${RESEND_FROM:onboarding@resend.dev}`
  still falls back to the sandbox sender for local/stub; only its comment was updated (domain is
  verified, prod sets `RESEND_FROM` to the real address). `ResendConfig`'s fail-fast guard
  (`requireExplicitResendFrom`, `!local & !stub`) is unaffected — Railway prod still needs
  `RESEND_FROM` set explicitly as an env var; this change doesn't add a new default in prod's favor
  by design, it just means the value prod should set is now a real deliverable address instead of a
  placeholder.
- **`ResendLiveSmokeTest` reviewed, not modified.** Confirmed it actually sends live mail through
  the real `ResendMailClient.sendSessionBooked` (real HTTP POST to Resend, not a stub) to whatever
  `RESEND_SMOKE_TO` env var names. `@Disabled` unconditionally, and CI (`.github/workflows/ci.yml`)
  runs plain `./mvnw clean test` with no `-Dtest=ResendLiveSmokeTest` override and no
  disabled-test-enabling flag — JUnit `@Disabled` is skipped regardless of `-Dtest` filters, so
  this can't fire in CI. To run manually:
  ```
  RESEND_API_KEY=re_xxx RESEND_SMOKE_TO=you@example.com \
    ./mvnw test -Dtest=ResendLiveSmokeTest -DfailIfNoTests=false
  ```
  and temporarily delete the `@Disabled` line first (it's the belt-and-suspenders CI guard).
  **Known gap, flagged but not changed:** the test hard-codes the sender as
  `"onboarding@resend.dev"` (`ResendLiveSmokeTest.java:45`), not the now-verified domain — under
  the sandbox sender, Resend restricts delivery to the account owner's own email regardless of
  `RESEND_SMOKE_TO`. Whether to switch it to the real domain (which would let it send to *any*
  `RESEND_SMOKE_TO` address, not just the account owner) is a real behavior change and was left for
  an explicit decision rather than applied silently.
- **Mail-content link audit (email verification, password reset, session notifications, etc.):**
  no hardcoded `localhost` found in any outbound mail template (`ResendMailClient.java`). The only
  two dynamic link sources in mail bodies are `meetLink` (Jitsi-generated per session, never
  localhost) and the password-reset `resetLink`, built in `PasswordSecurityService.forgotPassword`
  off `app.password-security.frontend-base-url` (`${FRONTEND_BASE_URL:http://localhost:5173}`) —
  already config-driven, and `FRONTEND_BASE_URL` was already documented as a required prod env var
  in the "Production security configuration" section below. Email verification is a 6-digit code,
  not a link, so it has no URL to leak. The one hardcoded `http://localhost:8080/...` in the
  codebase (`RealIyzicoClient.java:78`) is an iyzico webhook callback fallback, not mail content —
  out of scope here, noted for awareness only.
- **Remaining work:** one end-to-end manual scenario test against the real verified domain
  (register → verify email → checkout → session booking/cancellation/reminder → password reset —
  confirm each arrives from `noreply@uniformakademi.com`, headers show `reply-to:
  merhaba@uniformakademi.com`, and nothing lands in spam). Not yet run as part of this change.

## Integration test suite drift fix (2026-08-15)

`mvn verify` had 5 failing tests in `SubscriptionCheckoutIntegrationTest`, all pre-existing
regressions unrelated to any recent app-code change — the tests just hadn't been run against the
current dependency/security stack in a while. Root causes and fixes:

- **`checkout_nonStudent_forbidden` (403→400).** `tools.jackson.core:jackson-databind:3.1.2`
  (pulled in by Spring Boot 4.0.6's `spring-boot-starter-jackson`) now rejects a JSON body that
  omits properties of a record DTO (`HttpMessageNotReadableException` → 400) **before**
  `@PreAuthorize` ever runs — argument binding/validation happens outside the method-security proxy,
  so a malformed/partial body always wins over a role check. The test sent a 2-field body
  (`{"coachId":1,"packageId":1}"`) against `SubscriptionCheckoutRequest`'s 6 fields. Fixed by
  sending the full body via the existing `checkoutBody()` helper — the test's job is authorization,
  not payload completeness. **Same trap will resurface anywhere else a test posts a partial JSON
  body for a record request DTO** — this isn't specific to this one test/endpoint.
- **Two `succeedPayment_*` tests (403/200→404).** `StubPaymentController` is gated by
  `@ConditionalOnProperty(payments.stub.success-enabled=true)`, default `false`
  (`application.yml:196`). `application-test.yml` never set it, so under `@ActiveProfiles("test")`
  the controller bean never registered → 404 on every request. Fixed with
  `@SpringBootTest(properties = "payments.stub.success-enabled=true")` scoped to
  `SubscriptionCheckoutIntegrationTest` only (not global in `application-test.yml`) — it's a
  backdoor that must stay closed everywhere else, same reasoning `IyzicoWebhookSignatureTest`
  already applies to `payments.iyzico.enabled`.
- **Two `webhook_*` tests (200→401).** `SubscriptionService.verifyWebhookSignature` fails closed:
  requires `payments.iyzico.enabled=true` and a valid `X-IYZ-SIGNATURE-V3` HMAC header. Both tests
  posted unsigned, exactly the case the file's own bottom-of-file comment already documents as
  fixed for two *other*, earlier-removed tests — these two were missed in that pass. **Could not
  just add a signature + `payments.iyzico.enabled=true` to this test class**: that property is a
  bean-selection switch — `IyzicoClient` flips from `StubIyzicoClient` to `RealIyzicoClient`
  (`@ConditionalOnProperty` on each), and every checkout test in this same Spring context calls
  `POST /api/v1/subscriptions/checkout`, which synchronously calls
  `IyzicoClient.initializeCheckout()`. `RealIyzicoClient`'s constructor throws
  `IllegalStateException` when `baseUrl` isn't configured, which would fail context startup and
  take down every test in the class. **General lesson: before adding a `@SpringBootTest(properties
  = ...)` override to an existing test class, check what else that property gates in the same
  Spring context — a property flip can be a global bean swap, not a local toggle.** Fixed instead
  by removing both tests from `SubscriptionCheckoutIntegrationTest` (see the file's own
  bottom-of-file note, extended) — the SUCCESS+idempotency half was already redundantly covered by
  `IyzicoWebhookSignatureTest#processWebhook_validSignature_success` /
  `#processWebhook_duplicateWebhook_idempotent`; the FAILURE+idempotency half was genuinely missing
  and was added there as `processWebhook_validSignatureFailureStatus_processedAndIdempotent`,
  reusing the class's existing `calculateSignature` helper and `@BeforeEach` fixture. Fail-closed
  coverage for missing/invalid signatures already existed
  (`processWebhook_missingSignature_unauthorized` / `processWebhook_invalidSignature_unauthorized`)
  — nothing to add there.
- **Result:** `mvn verify` is green — 547 tests, 0 failures, 0 errors, 1 skip (pre-existing,
  unrelated). Full run including Testcontainers, not just the unit-test subset.
- **Pattern worth naming:** this whole class of failure exists because the Testcontainers-backed
  integration suite hadn't been run in CI/locally for a while — Docker wasn't available in the
  sandbox that made the last several code changes (see the Phase 7 entry below for a concrete
  instance), so app code kept moving (Jackson 3 upgrade, webhook signature hardening) while these
  tests silently fell behind. Now that a local Docker daemon is available and the full suite passes,
  **this integration suite should be wired into CI** so this kind of drift is caught at merge time
  instead of accumulating across several unrelated changes.

## Resend sender hardening: RESEND_FROM fail-fast + reply-to (2026-08-12)

Follow-up to the section below. Two gaps closed:

- **`RESEND_FROM` fail-fast.** `app.resend.from` defaults to Resend's `onboarding@resend.dev`
  sandbox sender in `application.yml` — fine for local dev, wrong for production (unverified
  domain, sandbox restrictions), and it was silently falling back with no signal if the env var
  was ever missing on a real deploy. This project has **no dedicated `prod` Spring profile**
  (Railway runs the bare/default profile — no `application-prod.yml`, no
  `SPRING_PROFILES_ACTIVE` set anywhere in-repo), so "prod" is detected as *not*
  local/test/stub. `ResendConfig` now registers an `InitializingBean`
  (`@Profile("!local & !stub")`, class already `@Profile("!test")`) that throws
  `IllegalStateException` at context refresh if `Environment.containsProperty("RESEND_FROM")` is
  false — checking the raw env var's presence, not `ResendProperties.from()`, since the latter is
  never blank (the yaml placeholder default always resolves to *something*). Local and stub stay
  zero-config. Covered by `ResendConfigTest` (`ApplicationContextRunner`, mirrors
  `StorageConfigTest`'s shape).
- **`app.resend.reply-to`** (new, optional key / `RESEND_REPLY_TO` env var). `ResendMailClient`
  includes it as `reply_to` on every Resend request when set; when blank, the field is omitted
  from the request body entirely (`@JsonInclude(NON_NULL)` on the internal request record) rather
  than sending an empty header. No behavior change for existing deployments that don't set it.
- **Domain verification:** was outstanding at the time this section was written; completed
  2026-08-15 (`uniformakademi.com`, Verified in Resend) — see the top handoff entry for the real
  `from`/`reply-to` values now in use.
- **Docs/env:** `application-local.yml.example` documents both keys. No backend `README.md`
  exists in this repo (only `frontend/README.md`/`HELP.md`), so this handoff doc is where backend
  config changes are recorded — same as the section below.

## Resend integration completion: coach-side mail, WELCOME, session cancel/reminder (V27, 2026-08-11)

Completed the Phase 7 `MailClient`/Resend work that was left half-wired. Architecture unchanged
(concrete `MailClient` interface + `ResendMailClient`/`StubMailClient`, `@TransactionalEventListener(AFTER_COMMIT)`,
best-effort swallow-and-log, no retry) — CLAUDE.md's `@Async` ban and "no abstractions beyond the 4
sanctioned interfaces" rule were kept, not revisited.

- **pom.xml cleanup:** removed `com.resend:resend-java` — declared with an unpinned `LATEST` version
  (a CLAUDE.md "pin everything" violation) but never actually imported; `ResendMailClient` talks to
  Resend directly over Spring `RestClient`, not the SDK. Dead dependency, not a real integration gap.
- **Coach-side notifications:** `PACKAGE_PURCHASED` and `SESSION_BOOKED` previously emailed the
  student only. Added `sendPurchaseConfirmedToCoach`/`sendSessionBookedToCoach` to `MailClient`,
  populated from a new `coachEmail` field on `PurchaseConfirmedEvent`/`SessionBookedEvent`. The
  coach-side purchase mail deliberately omits the payment amount — coach payout/commission math is
  still unresolved (`docs/adr_coach_payout_architecture.md`), so no commission-sensitive figure is
  surfaced via email.
- **WELCOME mail:** new `WelcomeMailEvent`/`WelcomeMailListener`. Fires once, the moment an account
  first becomes usable — after a successful `EmailVerificationService.verify()` for password
  sign-ups (not on code issue/resend, which can happen multiple times before the account is usable),
  or immediately on account creation/linking for Google sign-ups (already verified by Google, no
  code step; `AuthService.upsertGoogleUser`'s three verified-transition branches).
- **Session-level SESSION_CANCELLED:** distinct from the existing subscription-cancellation email
  (`sendCancellationConfirmed`). New `SessionCancelledEvent`/`SessionCancellationMailListener` fires
  from `SessionService.cancel()`, emailing both student (confirmation) and coach (notified), with
  copy that differs on early (`CANCELLED`, quota returned) vs. late (`LATE_CANCELLED`, quota burned).
  Only the existing student-initiated cancel flow — there is no coach-initiated session cancellation
  endpoint, so "who cancelled" only has the one branch today.
- **SESSION_REMINDER (new scheduled job):** `SessionReminderJob` mirrors `SubscriptionRenewalJob`'s
  shape (`@Scheduled` + a deterministic `now`-parameterized method tests call directly). Runs every
  15 min (`app.session-reminder.cron`), sends one reminder ~24h before start
  (`app.session-reminder.lead-time`, default `24h`) — single reminder, not 24h+1h, per the project's
  MVP-simplicity stance. Idempotency is a DB-level atomic claim, **V27** adds
  `sessions.reminder_sent_at`; `SessionRepository.claimReminder` is `UPDATE ... WHERE
  reminder_sent_at IS NULL` (same pattern as `CoachProfileRepository.incrementActiveStudentCountIfRoom`),
  so overlapping/concurrent job runs can never double-send. The frequent poll interval means a
  session is simply caught on whichever run first sees it inside the lead window — self-healing
  after job downtime, no precise per-session timer needed. Student-side only (coaches are paid via
  the subscription regardless of a no-show). Candidates additionally require a non-null `meetLink`,
  so a session is skipped (and picked up later) if the booking-time Meet-link creation hasn't landed
  yet, rather than reminding with a dead link.
- **Still deferred:** the pre-renewal notice ("your subscription auto-renews on X") from the
  "Scheduling / Reminders" slice below — only the session reminder was in scope for this pass.
- **Tests:** all new mail methods covered in `ResendMailClientTest` (`MockRestServiceServer`, no
  Spring context); new `PurchaseConfirmationMailListenerTest`, `SessionCancellationMailListenerTest`,
  `SessionReminderJobTest` (pure Mockito). `SessionLifecycleTest` (the Testcontainers integration
  test covering `SessionService.cancel()`) could not be run to completion in the session that made
  this change — Testcontainers hung with zero container spawned and near-zero CPU for 14+ minutes in
  that sandboxed environment, unrelated to the code change. All 352 non-Testcontainers unit tests
  pass; `SessionLifecycleTest` should be re-run locally to confirm before merging.
- **Config:** new env vars `SESSION_REMINDER_LEAD_TIME` (default `24h`) and `SESSION_REMINDER_CRON`
  (default `0 */15 * * * *`), both in `application.yml` with defaults — no `.env` changes required
  to run locally.

## Cloudflare R2 media storage foundation (V26)

- Neon/PostgreSQL remains the structured-data store. `media_assets` stores only owner, object key, declared metadata, type, visibility and lifecycle status; no binary content, credentials, or temporary signed URLs are stored in PostgreSQL.
- `StorageService` isolates business code from the S3-compatible SDK. `CloudflareR2StorageService` uses AWS SDK v2 only when `R2_ENABLED=true`; otherwise `StubStorageService` provides in-memory, network-free presign/HEAD/download/delete behavior.
- Authenticated clients call `POST /api/v1/media/uploads/presign`, upload directly with the required `Content-Type`, then call `POST /api/v1/media/uploads/{assetId}/complete`. Completion performs object HEAD/metadata validation before changing `PENDING_UPLOAD` to `ACTIVE`.
- The backend selects visibility and a random object key. Profile images remain PUBLIC because both authenticated shells and public coach discovery render the same stable URL; their anonymous locator is a separate 256-bit opaque token, never a database/user id. DOCUMENT remains intentionally PRIVATE for future KVKK/consent/user-document work.
- PUBLIC responses use the stable backend URL `GET /api/v1/public/media/{opaqueToken}`, which redirects to a fresh signed storage URL. PRIVATE downloads use authenticated `GET /api/v1/media/{assetId}/download-url` with owner/ADMIN authorization and expiration.
- Coach/student profiles reference active profile-image assets. Coach introductions are no longer uploaded to R2: an ADMIN stores a validated YouTube video id and responses derive only `https://www.youtube-nocookie.com/embed/{id}`. The historical R2 intro-video column/type is retained only for migration compatibility.
- Defaults: profile image (JPEG/PNG/WebP) 5 MiB; private document (PDF/JPEG/PNG) 10 MiB. Stale `PENDING_UPLOAD` rows expire after 24 hours in bounded scheduled batches.
- Replacement/deletion first commits the DB lifecycle/reference change, then performs irreversible object deletion after commit. Rollback therefore cannot leave an active DB reference pointing at a deleted object; post-commit failures are logged for operational follow-up.
- Railway does not proxy upload bodies or use its ephemeral filesystem. Required variables are `R2_ENABLED`, `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_ENDPOINT`, `MEDIA_PUBLIC_BASE_URL`, `R2_UPLOAD_URL_EXPIRATION_MINUTES`, and `R2_DOWNLOAD_URL_EXPIRATION_MINUTES`. A non-local R2 deployment fails startup unless `MEDIA_PUBLIC_BASE_URL` is a non-local HTTPS backend origin.

No real Cloudflare account, bucket, credentials, DNS, CORS, custom domain, or API call is configured in this branch. Production setup checklist:

1. Create the Cloudflare account.
2. Create the R2 bucket.
3. Create a bucket-scoped API token with minimum required object permissions.
4. Set Railway environment variables and enable R2.
5. Configure bucket CORS for exact frontend origins, PUT/GET methods and required headers.
6. Set `MEDIA_PUBLIC_BASE_URL` to the externally reachable HTTPS backend origin.
7. Confirm anonymous opaque-token redirects and authenticated private downloads through the backend.
8. Run real presign/upload/finalize/public-read/private-download/replacement/delete smoke tests.

Private-document malware scanning/quarantine and a durable retry/outbox for failed post-commit physical deletion remain production-hardening follow-ups.

## Account, conversation, purchase-mail and refund policy contract (2026-08-09)

- Local-password users may change their password at any time. There is no 15-day cooldown and no `PASSWORD_CHANGE_TOO_SOON`/`nextAllowedAt` contract. Current-password verification, password policy/reuse rejection, `passwordChangedAt`, `passwordVersion`, refresh-token revocation, old-access-token invalidation and re-login behavior remain unchanged. Reset-password and Google-only behavior are unchanged.
- Every coach/student conversation response exposes `observer: { type: "ADMIN", displayName: "Uniform Akademi Admin", readOnly: true }`. This stable platform observer is not a STUDENT/COACH participant and creates no subscription relationship. It is computed for every existing and new conversation, so no admin user ID or observer backfill row is required. Admin reads remain on the audited, method-secured read-only service; REST and STOMP sends still share `MessageService`, whose participant check excludes ADMIN.
- Admin conversation navigation is `GET /api/v1/admin/conversations/coaches` → `GET /api/v1/admin/conversations/coaches/{coachId}/students` → audited `GET /api/v1/admin/conversations/{conversationId}/messages?reason=...`. The older POST read endpoint remains temporarily for client compatibility. After opening a conversation, ADMIN subscribes to the same `/topic/conversations/{id}` destination as participants and receives broker fan-out in real time. SUBSCRIBE requires a current ADMIN role and an existing conversation; ADMIN SEND to `/app/conversations/{id}/send` is rejected. The legacy paginated all-conversations endpoint remains available. Coach profile photos are not currently persisted, so `profilePhotoUrl` is returned as null.
- The initial PENDING→SUCCESS plan purchase stamps `payments.succeeded_at` and publishes one after-commit transactional purchase-confirmation email through `MailClient` (`ResendMailClient`/`StubMailClient`). Pending/failed payments and recurring renewals do not send this template. Duplicate callbacks cannot re-enter the success transition. Recipient is derived from the subscription owner; marketing preferences are not consulted. Content contains student/plan/coach, amount/currency, purchase time and current period end, never provider/card secrets.
- The canonical refund clock is the initial successful CHARGE `Payment.succeededAt` (UTC `Instant`; V25 backfills existing successes from `updated_at`). Boundaries are precise elapsed instants: `[purchaseAt,purchaseAt+7d)` = `UNCONDITIONAL`; `[purchaseAt+7d,purchaseAt+14d]` = `STANDARD_ELIGIBLE` when no paid service evidence exists, otherwise `MANUAL_REVIEW`; values after `purchaseAt+14d` return `REFUND_WINDOW_EXPIRED`. This standard workflow makes no statement extinguishing other statutory rights.
- Paid-service evidence uses only `Session` rows for that subscription; `TrialConsultation` is excluded. A future planned session and a cancelled session do not indicate started service. A completed session, or a non-cancelled paid session whose start time has passed, routes days 8–14 to manual review. Snapshot evidence includes counts, earliest paid session, latest relevant status and `serviceStarted`; uncertain elapsed evidence is therefore reviewed, never automatically rejected.
- Student endpoints: `POST /api/v1/refund-requests` (body `{subscriptionId}`; principal owns it) and `GET /api/v1/refund-requests/me`. Admin endpoints: `GET /api/v1/admin/refund-requests` (status/eligibility/student/coach/date/page filters), `GET /{id}`, `POST /{id}/approve`, and `POST /{id}/reject` (meaningful reason required). `RefundRequest` is workflow state; actual money movement remains the existing `Payment(type=REFUND)` provider flow. Approval locks workflow state, calls the configured provider outside the DB transaction, and links the successful refund row. Provider failure returns the request to `PENDING`, never `REFUNDED`; active-request and payment-ledger constraints prevent duplicate active requests/refunds.

## Production security configuration

- API authentication is `Authorization: Bearer <JWT>` only. The JWT filter verifies signature/expiry, requires the subject user to still exist, blocks `SUSPENDED`/`DELETED`, enforces `passwordChangedAt` and `passwordVersion`, and derives the granted role from the current database user rather than a stale token role claim.
- `SessionCreationPolicy.IF_REQUIRED` is intentional. Spring Security temporarily stores Google OAuth2 authorization request/state in an HTTP session. After a successful callback, the backend creates the one-time frontend exchange code, invalidates that temporary session, clears the request security context, and redirects. The resulting API login is therefore JWT-based rather than cookie-session-based.
- CSRF remains disabled because application API authorization is not cookie based. CORS credential sharing is disabled. The OAuth2 handshake uses top-level browser redirects and does not require credentialed cross-origin API requests.
- Coarse route rules protect `/api/v1/admin/**` as `ADMIN`, `/api/v1/coach/**` as `COACH`, and student self/trial namespaces as `STUDENT`; controller `@PreAuthorize` and service ownership checks remain the finer-grained layer. Public coach discovery stays under the separate `/api/v1/coaches/**` namespace and is not blocked by the coach-self matcher. Admin is not implicitly allowed onto coach-self endpoints.
- REST and WebSocket origins share typed `app.cors.allowed-origins` configuration. Set `CORS_ALLOWED_ORIGINS` to a comma-separated list of exact deployed frontend origins, for example `https://app.example.com,https://www.example.com`. Development defaults to `http://localhost:5173`. Empty and wildcard origin lists fail startup. Allowed methods are `GET,POST,PUT,PATCH,DELETE,OPTIONS`; request headers are `Authorization,Content-Type`; no response authorization header is exposed.
- The `/ws/**` HTTP handshake remains public because the token travels in the STOMP `CONNECT` frame. `StompAuthChannelInterceptor` requires a valid current JWT/user, applies password/session invalidation rules, and authorizes conversation subscriptions by membership. REST and WebSocket share the same origin allowlist.
- The Iyzico webhook remains public at the HTTP-auth layer because providers cannot send an application JWT. The controller always calls the signature-verifying service overload; missing/invalid signatures fail closed, and disabled/missing provider configuration rejects the webhook.
- `/api/v1/health` remains public for Railway health checks. Swagger/API docs now fail closed (`SWAGGER_ENABLED=false` by default); only the explicit local example opts in. Forwarded headers also default to `none`. A production proxy deployment may set `SERVER_FORWARD_HEADERS_STRATEGY=framework` only when the ingress sanitizes/replaces client-supplied forwarding headers. Production must also set `CORS_ALLOWED_ORIGINS`, `OAUTH2_FRONTEND_REDIRECT_URI`, `FRONTEND_BASE_URL`, the external backend callback URL, and all secrets/real OAuth credentials.

## Dashboard Frontend API Contract (final hardening pass, 2026-08-09)

### Shared authentication, routing, and readiness

There is one login flow. After login (and on application reload), call `GET /api/v1/auth/me` and route only from `UserResponse.role`: `STUDENT` -> student dashboard, `COACH` -> coach dashboard, `ADMIN` -> admin dashboard. `UserResponse` contains `id`, `role`, `status`, `emailVerified`, `legalOnboardingCompleted`, `hasLocalPassword`, and `passwordChangedAt`; it intentionally contains no dashboard aggregates. A coach obtains approval/profile readiness from `GET /api/v1/coach/profile/me` (`CoachProfileResponse.status`). Student subscription state comes from dashboard/subscription data, never auth identity.

`emailVerified` proves email ownership; `legalOnboardingCompleted` proves current required onboarding acceptances. They are independent. Checkout, paid booking, trial creation, conversation creation, and message send apply both gates and return distinct `EMAIL_VERIFICATION_REQUIRED` or `LEGAL_ONBOARDING_REQUIRED` errors. Read-only dashboard/history endpoints remain available to an authenticated active account. The JWT filter blocks `SUSPENDED` and `DELETED` accounts with `USER_SUSPENDED` and `USER_DELETED` before controller execution.

All timestamps are ISO-8601 UTC `Instant` values. Calendar-month/week calculations use `Europe/Istanbul`; range filters are half-open `[from,to)`. Money is a JSON decimal backed by `BigDecimal`, in TRY. Paginated responses use `PageResponse<T>`: `{content,page,size,totalElements,totalPages,last}`; page size is capped at 100. Invalid ranges/sorts return `INVALID_DATE_RANGE`/`INVALID_SORT_FIELD`.

### Authorization matrix

| Surface | STUDENT | COACH | ADMIN |
| --- | --- | --- | --- |
| `/api/v1/students/**`, `/sessions/**`, `/subscriptions/**`, `/trial-consultations/**` | self only | denied | denied |
| `/api/v1/coaches/**` discovery | approved public coach data | denied | read-only discovery allowed |
| `/api/v1/coach/**` | denied | principal-derived self resources only | denied |
| `/api/v1/conversations/**` | membership + relationship gates | membership + relationship gates | denied; uses separately audited admin surface |
| `/api/v1/admin/**` | denied | denied | allowed; operational management, never impersonation |

No self endpoint accepts a role or owner ID from the client. A coach/student resource ID is always checked against the authenticated principal. Cross-owner lookups deliberately use not-found responses where appropriate to avoid leaking resource existence.

### Student endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/students/me/dashboard` | STUDENT | — | `StudentDashboardResponse` (`user`, latest `DashboardSubscription|null`, latest `DashboardPayment|null`) | `USER_NOT_FOUND` |
| `GET /api/v1/subscriptions/me` | STUDENT | — | `List<SubscriptionResponse>` | — |
| `POST /api/v1/subscriptions/checkout` | STUDENT | `SubscriptionCheckoutRequest` | `SubscriptionCheckoutResponse` | readiness errors, `COACH_NOT_APPROVED`, `ALREADY_SUBSCRIBED`, checkout legal errors |
| `POST /api/v1/subscriptions/{id}/cancel` | STUDENT/self | path ID | `SubscriptionResponse` | `SUBSCRIPTION_NOT_FOUND`, invalid state |
| `GET /api/v1/sessions/me` | STUDENT | — | `List<SessionResponse>`; newest start first | — |
| `POST /api/v1/sessions` | STUDENT | `availabilityId` | `SessionResponse` | readiness errors, `COACH_NOT_APPROVED`, `NO_ACTIVE_SUBSCRIPTION`, `QUOTA_EXCEEDED`, `SLOT_TAKEN`, `SLOT_IN_PAST` |
| `POST /api/v1/sessions/{id}/cancel` | STUDENT/self | path ID | `SessionResponse` | `SESSION_NOT_FOUND`, invalid state |
| `GET /api/v1/coaches` | STUDENT/ADMIN | `track`, `universityId`, `q`, pageable; default newest | `PageResponse<CoachSummaryResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/coaches/{id}` | STUDENT/ADMIN | coach profile ID | `CoachDetailResponse` | `COACH_NOT_FOUND` |
| `GET /api/v1/coaches/{id}/availability` | STUDENT/ADMIN | coach profile ID | future open `List<AvailabilityResponse>` | `COACH_NOT_FOUND` |
| `POST /api/v1/trial-consultations` | STUDENT | `availabilityId` | `TrialConsultationResponse` | readiness errors, `COACH_NOT_AVAILABLE`, `TRIAL_ALREADY_EXISTS`, `SLOT_TAKEN`, `SLOT_IN_PAST` |
| `GET /api/v1/trial-consultations/me` | STUDENT | — | `List<TrialConsultationResponse>`; newest start first | — |
| `POST /api/v1/trial-consultations/{id}/cancel` | STUDENT/self | path ID | `TrialConsultationResponse` | `TRIAL_NOT_FOUND`, `INVALID_TRIAL_STATE` |
| `POST /api/v1/conversations` | STUDENT | `coachId` | `ConversationResponse` | readiness errors, relationship/access errors |
| `GET /api/v1/conversations` | STUDENT/COACH | — | `List<ConversationResponse>`; latest activity first | membership/access errors |
| `GET /api/v1/conversations/{id}/messages` | STUDENT/COACH/member | pageable, default size 30 | `PageResponse<MessageResponse>` | membership/access errors |
| `POST /api/v1/conversations/{id}/messages` | STUDENT/COACH/member | `content` | `MessageResponse` | readiness errors, active-message relationship errors |

The nullable dashboard subscription/payment pair is the stable no-subscription state. Detailed sessions and conversations stay in their dedicated endpoints. `ACTIVE` and `PAST_DUE` are live booking/subscription relationships under the existing grace rule; `EXPIRED` and `CANCELLED` retain messaging history but cannot send or book.

### Coach endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/coach/dashboard/summary` | COACH/self | — | `CoachDashboardSummaryResponse` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/students` | COACH/self | `status=ACTIVE|HISTORICAL|ALL`, pageable; default newest + ID tie-break | `PageResponse<CoachStudentResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/coach/calendar/sessions` | COACH/self | `from`, `to`, `status`, `studentId`, pageable; default nearest + ID tie-break | `PageResponse<SessionResponse>` | `INVALID_DATE_RANGE`, `INVALID_SORT_FIELD` |
| `GET /api/v1/coach/conversations/summary` | COACH/self | — | `List<CoachConversationSummaryResponse>` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/dashboard/packages` | COACH/self | — | `List<CoachPackageSummaryResponse>` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/sessions` | COACH/self | — | `List<SessionResponse>` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/sessions/{id}/complete` / `no-show` | COACH/owner | path ID | `SessionResponse` | `SESSION_NOT_FOUND`, invalid state |
| `GET /api/v1/coach/trial-consultations` | COACH/self | — | `List<TrialConsultationResponse>` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/trial-consultations/{id}/{confirm|cancel|complete|no-show}` | COACH/owner | path ID | `TrialConsultationResponse` | `TRIAL_NOT_FOUND`, `INVALID_TRIAL_STATE`, `TRIAL_NOT_STARTED` |
| `GET /api/v1/coach/availability` | COACH/self | — | `List<AvailabilityResponse>`; earliest first | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/availability` | COACH/self | `AvailabilityCreateRequest` | `AvailabilityResponse` | `COACH_NOT_APPROVED`, `SLOT_OVERLAP`, `SLOT_DUPLICATE`, range errors |
| `DELETE /api/v1/coach/availability/{id}` | COACH/owner | path ID | 204 | `SLOT_NOT_FOUND`, `SLOT_BOOKED` |
| `GET /api/v1/coach/profile/me` | COACH/self | — | `CoachProfileResponse`, including approval `status` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/profile`, `PUT /api/v1/coach/profile/me` | COACH/self | profile request DTO | `CoachProfileResponse` | validation/ownership errors |

Summary definitions: active students are distinct students on `ACTIVE|PAST_DUE`; completed-this-month uses Istanbul month boundaries; upcoming sessions are future `PLANNED` paid sessions; unread count is messages addressed to the coach with null `readAt`; availability configured means at least one future unbooked slot; pending trials are `REQUESTED`. `grossSales` is lifetime successful `CHARGE` volume attributable to the coach/package, not payout, earnings, or profit. Coaches cannot mutate package prices.

### Admin endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/admin/dashboard/summary` | ADMIN | — | `AdminDashboardSummaryResponse` | `ACCESS_DENIED` for non-admin |
| `GET /api/v1/admin/users` | ADMIN | `role`, `status`, `search`, pageable | `PageResponse<AdminUserDirectoryResponse>` | `INVALID_SORT_FIELD` |
| `POST /api/v1/admin/users/{id}/suspend|unsuspend` | ADMIN | optional reason | `SuspendResponse` | deleted/admin protections, invalid state |
| `GET /api/v1/admin/coaches` | ADMIN | `status=PENDING|APPROVED|REJECTED|SUSPENDED|ALL`, `search`, pageable | `PageResponse<AdminCoachDirectoryResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/admin/coaches/{id}` | ADMIN | profile ID | `AdminCoachDirectoryResponse` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/admin/coaches/{id}/approve|reject` | ADMIN | reject reason where required | `CoachProfileResponse` | deleted/suspended protections, invalid state |
| `GET /api/v1/admin/subscriptions` | ADMIN | `status`, `studentId`, `coachId`, `packageId`, `from`, `to`, pageable | `PageResponse<AdminSubscriptionResponse>` | range/sort errors |
| `POST /api/v1/admin/subscriptions/{id}/terminate` | ADMIN | optional reason | `AdminSubscriptionTerminateResponse` | not found/invalid state |
| `GET /api/v1/admin/payments` | ADMIN | `type`, `status`, relationship/package/date filters, pageable | `PageResponse<AdminPaymentResponse>` | range/sort errors |
| `GET /api/v1/admin/finance/summary` | ADMIN | optional `from`, `to` | `AdminFinanceSummaryResponse` | `INVALID_DATE_RANGE` |
| `POST /api/v1/admin/payments/{paymentId}/refund` | ADMIN | amount, reason | `RefundResponse` | provider/stub and refundable-headroom errors |
| `GET /api/v1/admin/refunds` | ADMIN | status/relationship/package/date filters, pageable | refund `PageResponse<AdminPaymentResponse>` | range/sort errors |
| `GET /api/v1/admin/reports` | ADMIN | `status`, pageable | `PageResponse<ReportResponse>` | `INVALID_SORT_FIELD` |
| `PATCH /api/v1/admin/reports/{id}/status` | ADMIN | target status | `ReportResponse` | not found/invalid transition |
| `GET /api/v1/admin/sessions` | ADMIN | `type=ALL|PAID|TRIAL`, `status`, IDs, `from`, `to`, pageable | `PageResponse<AdminOperationalSessionResponse>` | `INVALID_SESSION_STATUS`, range/sort errors |

KPI definitions: students exclude `DELETED`; active coaches are `APPROVED` profiles with `ACTIVE` users; pending approvals are `PENDING` profiles; active subscriptions are `ACTIVE|PAST_DUE`; monthly sales/revenue are successful `CHARGE` ledger rows; monthly refunds are successful `REFUND` rows; open reports are `OPEN|REVIEWED`; scheduled sessions are future `PLANNED` paid sessions; completed-this-month counts paid `COMPLETED` sessions. The operational paid/trial union is read-only. Admin conversation content remains on the separate reason/audit-gated `/api/v1/admin/conversations` surface.

### Paid sessions, trials, and known limits

Paid sessions require a live subscription and consume weekly quota in `PLANNED|COMPLETED|LATE_CANCELLED|NO_SHOW`; early `CANCELLED` does not consume quota. Trials require no subscription/payment, consume no quota, and do not create a paid messaging relationship. Both reserve the same concrete availability under a pessimistic lock; availability ranges cannot overlap per coach, and cancellation reopens an eligible slot. No Flyway migration was added in this pass; schema remains V1-V24.

Remaining bounded-list risks: student/coach trial histories, legacy `/sessions/me` and `/coach/sessions`, conversation inboxes, and own availability are unpaginated compatibility contracts. Migrate them only with an explicit frontend versioning plan. The legacy immediate admin refund endpoint remains available beside the V25 request/review workflow documented above.

## Admin Dashboard backend foundation

- Admin uses the same shared login system and the authenticated `ADMIN` role; admin registration remains unavailable. Frontend role routing is not a separate authentication system.
- `GET /api/v1/admin/dashboard/summary` exposes repository-backed KPIs. Student count excludes `DELETED`; active coaches are `APPROVED` profiles whose user is `ACTIVE`; active subscriptions are `ACTIVE` or `PAST_DUE`; open reports are `OPEN` or `REVIEWED`.
- Monthly sales/revenue/refund/completed-session metrics use Europe/Istanbul calendar boundaries. Gross revenue is successful `CHARGE` ledger volume; refunds are successful `REFUND` rows; neither is profit. `netCollectedAmount` is gross successful charges minus successful refunds.
- Coach directory supports `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`, and `ALL`, repository-side search and pagination. Approval now rejects suspended/deleted identities. User directory supports role/status/search filters without password, token, verification-hash, reset evidence, or legal-document content.
- Admin suspend behavior and notification remain unchanged. `POST /api/v1/admin/users/{id}/unsuspend` restores only `SUSPENDED` non-admin users; deleted identities cannot be restored.
- Existing subscription/payment read models now support status, relationship, package, date, and pagination filters. Finance summaries use ledger rows only; no coach payout, commission settlement, or profit calculation is exposed.
- Existing immediate provider refund execution remains the financial source of truth. `GET /api/v1/admin/refunds` lists refund ledger attempts/completions; the V25 `RefundRequest` domain records student requests and admin decisions without duplicating provider refund rows.
- Existing report moderation and best-effort safety emails are reused. Admin message access was not broadened; the existing reason/audit-gated conversation oversight remains unchanged.
- `GET /api/v1/admin/sessions` provides a paginated read-only union of paid sessions and trial consultations. Admin receives no scheduling mutation endpoint.
- No migration was added; this slice is query/read-model functionality over migrations through V24.

## Coach Dashboard backend foundation (V24)

- Authentication remains one shared login system. The authenticated `role` drives frontend routing to the student, coach, or admin dashboard; there is no separate coach login.
- Coach-self endpoints resolve the coach from the JWT principal and are `COACH`-only: dashboard summary, paginated students, filtered calendar reads, conversation summaries, package/activity summaries, own availability, own profile, and trial-consultation management.
- An active student is a distinct student with an `ACTIVE` or `PAST_DUE` subscription to the coach. The student list separates `ACTIVE`, `HISTORICAL`, and `ALL`; weekly usage follows the existing Europe/Istanbul quota calculation and existing quota-consuming session statuses.
- Paid sessions still require a live subscription and weekly quota. Trial consultations are separate persisted records and never create a subscription/payment, consume quota, or unlock messaging.
- Public trial availability reuses the coach's explicit future, unbooked availability slots. A pessimistic lock on the availability row serializes paid and trial reservation; cancellation unlinks and reopens the slot.
- MVP trial rule: at most one non-cancelled trial per student/coach. The lifecycle is `REQUESTED -> CONFIRMED -> COMPLETED|NO_SHOW`, with `REQUESTED|CONFIRMED -> CANCELLED`.
- Coach package prices remain platform-controlled. Package summaries report current subscriptions and attributable successful-charge `grossSales`; no payout/earnings capability is implemented.
- `V24__trial_consultations.sql` adds the trial table, status check, ownership/time indexes, optimistic version, unique slot reference, and partial unique index enforcing the MVP student/coach rule.

Single-file resume point for a fresh session. **Factual, based on the code as it exists now**
(not the v4 plan). For binding rules see `CLAUDE.md`; for phase intent see `PHASES.md`.

_Last updated: 2026-06-27._

## Frontend legal/compliance integration

- Registration loads the current `TERMS_OF_USE`, `EXPLICIT_CONSENT`, and `KVKK_NOTICE`. It sends the two required accepted document IDs plus independent optional email/SMS marketing choices. The KVKK notice is a link, not a separate acceptance checkbox.
- Google OAuth returns to `/oauth/callback`, exchanges its one-time code, and sends users whose `legalOnboardingCompleted` is false to `/legal-onboarding`. Product routes remain unavailable until the current Terms and Explicit Consent are accepted.
- Subscription checkout loads and displays the current pre-information form, distance-sales agreement, and refund/cancellation policy. One required checkbox sends all three current document IDs with `legalDocumentsAccepted: true`.
- `/privacy` exposes authenticated marketing and cookie preferences, Explicit Consent withdrawal/re-onboarding, account-deletion status, and confirmed account deletion.
- The legacy frontend guardian/minor KVKK modal and guardian-required copy were retired. Date of birth remains in student registration because the backend still requires it.
- Legal content is rendered as plain text from the backend. Seeded backend legal text remains placeholder content and is not production-approved.
- Authenticated cookie preferences are synchronized with the backend. Anonymous-visitor cookie preference/banner behavior remains browser/frontend work and is not implemented by this integration.
- Frontend component testing is now configured with Vitest, jsdom, and Testing Library; `npm test` covers the core registration, OAuth/onboarding, checkout, withdrawal, preference, and deletion behaviors.
- The broader visual redesign remains separate from this focused integration.

---

## Status at a glance

- **Phases complete:** 0, 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 (Full Backend + Frontend workflows complete, rate limiting security layer integrated, except payout which is only prepared as an ADR).
- **In progress / next:** Closed beta QA, production infrastructure provisioning, and legal/tax model finalization.
- **Tests:** Full `mvn verify` (including Testcontainers) is **GREEN — 547 tests, 0 failures, 1 unrelated skip** (2026-08-15; see the top handoff entry for the drift fix that got it there). A local Docker daemon is required and was available for this run — Testcontainers should not be assumed "bypassed" going forward without checking.
- **Neon (prod DB):** Flyway at **v17** (all V1–V17 applied. V17 = date of birth and consent status).
- **Build:** Java 21 + Spring Boot 4 (backend); React + Vite + TypeScript (frontend).

### Stack (as wired)
Monorepo:
- **Backend:** Spring Boot 4.0.6, Spring Security 7 (stateless JWT + Google OAuth2), Spring Data JPA/Hibernate, Flyway, PostgreSQL, WebSockets/STOMP, Jackson, Testcontainers.
- **Frontend:** React + Vite + TypeScript, client routing, state provider, Tailwind/Vanilla CSS.

---

## How to run / test

```bash
# Tests (Colima/Docker MUST be running — Testcontainers spins real Postgres 18; no H2).
# Run from the backend directory:
cd backend
.\mvnw.cmd verify                  # on Windows
./mvnw verify                      # on macOS/Linux
.\mvnw.cmd test -Dtest=SomeTest    # run a single test class

# Run against Neon locally (needs the gitignored application-local.yml — see below):
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# App on http://localhost:8080 ; Swagger at /swagger-ui.html
```

- **`application-local.yml`** (gitignored, NOT committed) supplies `NEON_DATABASE_URL` (jdbc form,
  pooled host with `-pooler`, credentials as `user`/`password` query params, `channelBinding=require`),
  `JWT_SECRET`, and **`RESEND_API_KEY`** (Phase 7). Template is `application-local.yml.example`.
  Resend sender defaults to `onboarding@resend.dev` (sandbox; sends to the account owner without
  domain verification). **iyzico sandbox keys are NOT in here yet** — add when Phase 8 starts.
- **Admin seed (V3):** `admin@yks.local` / dev password **`admin1234`** (dev-only BCrypt hash baked
  into V3; prod overrides via `ADMIN_PASSWORD_HASH`). Use it to approve coaches in smoke tests.

---

## What each phase delivered

### Phase 0 — Setup
`BaseEntity` + `@EnableJpaAuditing`; scoped security; `GET /api/v1/health` → `{"status":"UP"}`;
Flyway V1 baseline; Testcontainers wired (real Postgres). `ddl-auto: validate` — Flyway owns schema.

### Phase 0.5 — Risk PoC (throwaway)
Findings: iyzico sandbox was blocked at PoC time (no creds → stub continued); Google Meet needs
Workspace → **Jitsi fallback** chosen for Phase 6. **Update (2026-06-27): iyzico sandbox is now
provisioned** (merchant **3429394**; sandbox API key + secret live in the iyzico sandbox panel,
not yet in repo/config) → **Phase 8 is no longer blocked on credentials.** Production iyzico
application (real company/tax info) still deferred to launch.

### Phase 1 — Auth  (V2)
- Entities: `User` (email unique, passwordHash nullable, role, status, googleSub, emailVerified),
  `RefreshToken` (hashed, rotating).
- Endpoints: `POST /api/v1/auth/{register,login,refresh,logout}`. JWT HS256 access (15m) +
  opaque rotating refresh (30d, stored hashed). Google OAuth2 account-linking by verified email.
- Invariants: stateless; `@AuthenticationPrincipal` resolves to `Long userId`; role claim →
  `ROLE_x` authority. ProblemDetail (RFC 9457) for ALL errors incl. 401/403 and framework
  exceptions (global `@RestControllerAdvice` extends `ResponseEntityExceptionHandler`).

### Phase 2 — Coach & Profile  (V3 seed admin, V4)
- Entities: `CoachProfile` (user 1:1, university, headline/bio/dept/gradYear, status
  PENDING/APPROVED/REJECTED, `active_student_count`, `max_student_capacity`=10 default, no price),
  `StudentProfile`, `University`, `CoachSubject` (track join).
- Endpoints: `POST/GET /api/v1/coach/profile/me`, `PUT /coach/profile/me`; student profile;
  `GET /api/v1/universities`; admin `GET /api/v1/admin/coaches?status=`, `POST .../{id}/approve`,
  `POST .../{id}/reject`.
- Invariants: only admin changes status; post-approval edits stay APPROVED; `AccessDeniedException`
  re-thrown in the handler so it routes to the 403 ProblemDetail handler (not 500).
  **REJECTED resubmission:** a REJECTED coach can call `createOwn` again — the existing profile is
  updated in-place (fields + tracks), status reset to PENDING, rejectionReason cleared. PENDING or
  APPROVED profiles still throw `PROFILE_ALREADY_EXISTS` (409). No duplicate rows created.

### Phase 3 — Search  (read-only)
- Endpoints: `GET /api/v1/coaches` (APPROVED-only; filters track/universityId/q; paginated,
  sort whitelist), `GET /api/v1/coaches/{id}`. Roles STUDENT+ADMIN (COACH 403).
- Seam: `CoachStatsService` returns derived rating/totalSessions (was placeholder; **totalSessions
  wired in 4d**, rating still null until Reviews). Search query untouched by the wiring.
- Note: `cast(:q as string)` in the JPQL to avoid Postgres `lower(bytea)` on null filter.

### Phase 4 — Availability & Booking  (V5–V8, built 4a→4d)
Cross-phase decision: minimal `Package`/`Subscription` introduced here (direct activation);
Phase 8 layers the payment lifecycle on top.

**4a — Packages & Subscriptions + capacity (V5).**
- `Package` (name unique, weeklySessions, durationDays, price, active; 2 seeded: "Aylık 1x" 1/30d/1500,
  "Aylık 2x" 2/30d/2500). `Subscription` (student, coachProfile, pkg, status ACTIVE/EXPIRED/CANCELLED,
  startAt/endAt). Enum field is `pkg` (`package` is reserved).
- Endpoints: `GET /api/v1/packages` (auth); `POST /api/v1/subscriptions` + `GET /me` (STUDENT).
- **Invariants:** capacity race → atomic conditional UPDATE
  `incrementActiveStudentCountIfRoom` (`SET active=active+1 WHERE active < max`; 0 rows → `COACH_FULL`,
  bypasses @Version on purpose). One ACTIVE sub per (student,coach) → **partial unique index**
  `where status='ACTIVE'`; same-student race caught via saveAndFlush → `ALREADY_SUBSCRIBED`.

**4b — Coach Availability slots (V6).**
- `CoachAvailability` (coachProfile, startTime/endTime UTC, `is_booked`; **UNIQUE(coach_profile_id,
  start_time)**).
- Endpoints: `POST/GET /api/v1/coach/availability`, `DELETE /coach/availability/{id}` (COACH, unbooked
  only, requires APPROVED coach status); `GET /api/v1/coaches/{id}/availability` (STUDENT/ADMIN, open future slots of APPROVED coach).
- Invariants: reject past slot (`SLOT_IN_PAST`), end≤start (`INVALID_SLOT_RANGE`), and non-approved coach (`COACH_NOT_APPROVED` 403) — validated in the
  service (unit-testable); duplicate start → `SLOT_DUPLICATE`; **overlapping time ranges** →
  `SLOT_OVERLAP` (409, service-level JPQL check: `existing.startTime < newEndTime AND
  existing.endTime > newStartTime`). Adjacent slots (end == start) are allowed. DB-level exclusion
  constraint (`btree_gist`) deferred — service guard sufficient for single-instance MVP.

**4c — Booking / Session (V7).**
- `Session` (student, coachProfile, subscription, **availability nullable+UNIQUE**, status
  PLANNED/COMPLETED/CANCELLED/LATE_CANCELLED/NO_SHOW, start/end snapshotted off the slot).
- Endpoints: `POST /api/v1/sessions` + `GET /me` (STUDENT); `GET /api/v1/coach/sessions` (COACH).
- **Invariants:**
  - **Double-booking guarantee = UNIQUE(`sessions.availability_id`) at INSERT** (saveAndFlush + catch →
    `SLOT_TAKEN`). Never read `is_booked` to decide; `is_booked` is a UI flag flipped after the guard.
  - **Approved Coach Gate:** The coach associated with the availability slot must be in the `APPROVED` status, otherwise booking is rejected with `COACH_NOT_APPROVED` (403).
  - **Weekly quota** = Mon–Sun in **Europe/Istanbul** → UTC window; counts quota-consuming sessions
    by subscription vs `pkg.weeklySessions`; `>=` → `QUOTA_EXCEEDED`.
  - Booking requires an **ACTIVE** subscription with the coach (`NO_ACTIVE_SUBSCRIPTION`) — the same
    lookup the message gate builds on.

**4d — Cancellation lifecycle + after-commit side effects.**
- Endpoints: `POST /api/v1/sessions/{id}/cancel` (STUDENT); `POST /api/v1/coach/sessions/{id}/complete`,
  `.../{id}/no-show` (COACH).
- **Invariants:**
  - Cancel: **≥24h before start → CANCELLED**, slot reopened (`availability=null`, `is_booked=false`),
    quota returned. **<24h → LATE_CANCELLED**, slot kept, quota burned. NO_SHOW → quota burned.
  - Quota-consuming set grew to **{PLANNED, COMPLETED, LATE_CANCELLED, NO_SHOW}** — the only change to
    quota logic (the 4c query is byte-for-byte unchanged; statuses are passed in).
  - **Meet link + email = the one sanctioned `@TransactionalEventListener(AFTER_COMMIT)`**
    (`SessionNotificationListener`): external calls run **outside** the tx; a failure is logged, never
    rolls back the booking. Persisting the link uses `setMeetLink` with **`REQUIRES_NEW`** (a default-
    propagation write inside an after-commit callback would NOT commit — known gotcha). `meet_link`
    column = V8. Clients: `integration.MeetClient`/`MailClient` (2 of the 4 sanctioned interfaces) with
    `Stub*` impls (real in Phase 6/7).
  - `CoachStats.totalSessions` wired = count of COMPLETED per coach — **edited only
    `CoachStatsService`** (+ a new repo count method); search path untouched (Phase 3 seam held).

### Phase 5 — Messaging  (V9; 5a + 5b + 5c done)

**5a — REST messaging + child-safety gate.**
- Entities: `Conversation` (student, coachProfile, **`last_message_at`** denormalized; UNIQUE(student,
  coach) = one thread/pair), `Message` (conversation, sender, content ≤4000, `read_at` nullable;
  `created_at` = sent time). Attachments **closed in beta** (text-only; no Attachment entity yet).
- Endpoints: `POST /api/v1/conversations` (STUDENT, open-or-get), `GET /conversations` (STUDENT/COACH
  inbox by last_message_at), `GET /conversations/{id}/messages` (paged), `POST /conversations/{id}/messages`
  (REST send fallback), `POST /conversations/{id}/read`.
- **Invariants:**
  - **Split Messaging Access Gates (Server-side)**:
    - **History Access Gate**: Allowed for students with status `ACTIVE`, `PAST_DUE`, `EXPIRED`, or `CANCELLED`. If a newer attempt is `PENDING_PAYMENT` or `FAILED`, but an older valid subscription exists, history access is granted. Open/read/history paths use this gate.
    - **Send Gate**: Allowed strictly only for students with `ACTIVE` subscription status. Blocked for all other statuses. Send path uses this gate.
  - **Membership re-checked on every send/read/history** (`403 NOT_CONVERSATION_PARTICIPANT`) — trust
    neither role nor path.
  - `last_message_at` bumped to the saved message's `createdAt` **in the same tx** as the insert.
  - mark-read flips `read_at` **only on the other party's** messages (`sender.id <> readerId` in the
    bulk update).
- Logic lives in `MessageService` (transport-agnostic on purpose — see 5b).

**5b — WebSocket/STOMP real-time.**
- `WebSocketConfig`: endpoint `/ws` (+ SockJS), broker `/topic`, app prefix `/app`, auth interceptor on
  client inbound channel. `ChatStompController.@MessageMapping("/conversations/{id}/send")` → **the same
  `MessageService.sendMessage`** → broadcast to `/topic/conversations/{id}` (payload serialized with the
  Jackson 3 `ObjectMapper`).
- `StompAuthChannelInterceptor`: **CONNECT** validates the JWT from the `Authorization` native header
  (missing/invalid/expired → connection rejected); **SUBSCRIBE** to a conversation topic checked against
  `MessageService.isParticipant` (else rejected server-side).
- SecurityConfig permits `/ws/**` (handshake carries no token — JWT is in the CONNECT frame). Safe: no
  subscribe/send is possible without a valid CONNECT.
- Token-expiry model: **validate at CONNECT; client reconnects with a fresh token on expiry.** Mid-session
  expiry is NOT actively enforced per-frame (see Known gaps).

**5c — Admin oversight (read-only, no schema change).**
- Endpoints: `GET /api/v1/admin/conversations` (list) + `GET /api/v1/admin/conversations/{id}/messages`,
  **ADMIN-only** via class-level `@PreAuthorize("hasRole('ADMIN')")` (the P2 pattern — there is no
  `/api/v1/admin/**` URL matcher; `anyRequest().authenticated()` + the annotation IS the rule). Non-admin → 403.
- **`AdminConversationService` is physically separate from `MessageService`** (child-safety): reads go straight
  to the repositories with **no membership gate**; it never calls `MessageService` and `MessageService` grew no
  `...ForAdmin` methods. All methods `@Transactional(readOnly = true)`.
- **Invisible read-only:** admin reads never touch `read_at` / `last_message_at` / membership — asserted by
  snapshot→read→re-fetch (fresh tx per repo call; no spanning context).
- **N+1-free list:** one entity-graph data query (+ Spring count) + **one** `count … group by` aggregate for the
  page's ids, merged in the service (0-message conversations default to count 0). Proven via Hibernate
  `Statistics` (statement count constant across page sizes).
- Pagination: default 20, **global max 100** (`spring.data.web.pageable.max-page-size`); sort whitelists —
  list `{lastMessageAt, createdAt}`, messages `{createdAt}` → `INVALID_SORT_FIELD` 400 otherwise.
- DTO: `ConversationSummaryResponse` (student{id,fullName} + coach{id,fullName,universityName} + lastMessageAt +
  messageCount); messages reuse `MessageResponse`. Tests: `AdminConversationControllerTest` (web slice, real
  `@PreAuthorize`) + `AdminConversationIntegrationTest` (Testcontainers).
- **No migration** — schema unchanged (still V9). **Deferred:** admin-access audit log → Phase 9.

### Phase 6 — Real video: Jitsi  (commit `150597f`, 116 tests)
- **What:** replaced `StubMeetClient` with **`JitsiMeetClient`** (real impl of the `MeetClient` seam).
  Generates `https://meet.jit.si/yks-{UUID}` locally from `UUID.randomUUID()` (v4 — **unguessable**,
  the mandated entropy source; minors-facing). **No HTTP call, no I/O** — a Jitsi room *is* its URL,
  created when the first participant opens it. Method is total (no checked exceptions). Base URL is a
  hardcoded constant (no config property — YAGNI until self-hosting).
- **Wiring:** `JitsiMeetClient` `@Profile("!test")`, `StubMeetClient` `@Profile("test")` — mutually
  exclusive ⇒ exactly one `MeetClient` bean per profile, no `@Primary`. (Caught that `StubMeetClient`
  was an unguarded `@Component`; a second impl without gating would have failed startup.)
- **Seam untouched:** `MeetClient` interface, `SessionNotificationListener` (AFTER_COMMIT),
  `SessionService.setMeetLink` (REQUIRES_NEW) — all unchanged. The Phase 4d failure-swallow test
  (`SessionNotificationListenerTest`) still stands as proof a client throw never rolls back the booking.
- **No migration** (`meet_link` already V8). Tests: `JitsiMeetClientTest` (UUID v4, two calls differ),
  `MeetClientWiringTest` (`ApplicationContextRunner` — exactly one bean per profile, no DB).

### Phase 7 — Real email: Resend  (commit `1a2072d`, 122 tests; live send confirmed)
- **Scope (locked):** real **Resend transport + booking-confirmation email ONLY**. The real
  deliverable is the reusable transport every later (A) email plugs into. **Nothing time-driven.**
- **What:** `StubMailClient` → `@Profile("test")`; new **`ResendMailClient`** `@Profile("!test")` posts
  to `https://api.resend.com/emails` via Spring `RestClient` (baseUrl constant, `Authorization: Bearer
  <key>`), inline Turkish HTML body, start time rendered **Europe/Istanbul**. `MailClient` keeps its
  single method `sendSessionBooked` — no new methods.
- **Config:** `ResendProperties` (`@ConfigurationProperties("app.resend")`: `apiKey`, `from`) +
  `ResendConfig` (`@Profile("!test") @EnableConfigurationProperties`, supplies the `RestClient.Builder`
  with **connect 3s / read 5s** timeouts via `SimpleClientHttpRequestFactory`). `RESEND_API_KEY` is
  env-only (no default); `from` defaults to `onboarding@resend.dev`.
- **Failure semantics (double backstop):** `sendSessionBooked` is total — wraps `retrieve()` in
  try/catch, logs the Resend message id on 2xx, **swallows-and-logs** any error/timeout. On top of the
  listener's own catch. A Resend outage/hang can **never** block the after-commit thread or roll back
  the committed booking.
- **Test isolation:** `ResendConfig` is `@Profile("!test")`, so under `test` the stub is active and
  **zero network** is wired — full-context tests hit no HTTP and need no Resend key.
- **No migration.** Tests: `ResendMailClientTest` (`MockRestServiceServer` — request shape on 2xx,
  500 → does-not-throw), `MailClientWiringTest` (exactly one `MailClient` per profile),
  `ResendLiveSmokeTest` (**`@Disabled` in the repo**; env-var-guarded; flipped locally only at smoke time).
- **Live smoke PASSED (2026-06-27):** real send `onboarding@resend.dev` → `onurcetinkaya149@gmail.com`,
  Resend message id `34f773d3-64c5-4a7e-b9a4-b68db3e38649`, HTTP 2xx. Key used for the smoke was
  rotated afterward (see Known gaps → resolved).

### Phase 8 Stage 1 — Auto-renew lifecycle, STUB-first  (V10; commits `c0277e6` 8a · `4db867b`/`a585ec2` 8b · `520a980` 8c · `ece8807` 8d)

**The whole auto-renew flow works end-to-end behind a stub `IyzicoClient` — no real money, no company.**
Real iyzico is Stage 2 (sandbox).

**8a — model & seam (V10).**
- `SubscriptionStatus` += **`PAST_DUE`** ({ACTIVE, PAST_DUE, EXPIRED, CANCELLED}). `Subscription` += `autoRenew`
  (default true), `savedCardToken`, `cancelledAt`, `failedChargeCount`, `lastChargeAttemptAt`. **`end_at` is the
  renewal trigger** (no separate next-renewal field).
- `Payment` entity: `type` (CHARGE/REFUND), `amount`, `status` (**PENDING**/SUCCESS/FAILED), **`idempotency_key`
  UNIQUE**, `provider_reference`, commission snapshot (`commission_rate/amount/coach_payout_amount`),
  `source_payment_id` (refund seam, unused). V10 also **widened the live-sub partial-unique index** to
  `where status in ('ACTIVE','PAST_DUE')`.
- **`IyzicoClient`** interface (4th sanctioned seam) + **`StubIyzicoClient`** (plain `@Component`, always succeeds,
  `stub-ref-{uuid}`). Subscription creation stamps `autoRenew=true` + a `stub-card-token-{uuid}`.
  `app.payment.commission-rate` (0.20) / `retry-days` (3).

**8b — billing lifecycle core (`SubscriptionBillingService`).** `processDue(subId, now)` with explicit tx
boundaries via `TransactionTemplate`: **tx1 reserve PENDING (UNIQUE key)** → **charge OUTSIDE any tx** →
**tx2 finalize + advance**. Crash between tx1 and charge → next run **resumes** the stale PENDING with its
own key (provider-idempotent → never a double charge); a concurrent same-day run finds today's PENDING and
**SKIPs**. Commission snapshotted at reserve (frozen). Capacity **decrement** on every →EXPIRED (atomic,
floored at 0). **Booking gate widened to {ACTIVE, PAST_DUE}** (grace keeps access open). Mandatory idempotency
concurrency test + every lifecycle branch tested.

**8c — scheduled job (`SubscriptionRenewalJob`).** `@EnableScheduling` (in `SchedulingConfig`, `@Profile("!test")`
so it never auto-fires in tests), daily cron `0 0 3 * * *` Europe/Istanbul. Selects due set (ACTIVE past end_at
∪ all PAST_DUE) and delegates to `processDue`; per-subscription try/catch. Overlapping-run idempotency tested.
**Single-instance beta; UNIQUE(idempotency_key) makes multi-instance safe (≤1 charge/sub/day).**

**8d — cancel endpoint + emails.** `POST /api/v1/subscriptions/{id}/cancel` (STUDENT, ownership in service) →
200 `SubscriptionResponse` (now exposes `autoRenew`; `savedCardToken` never exposed). **Idempotent**
(re-cancel = no-op, no re-stamp, no second email; terminal → 409). Four emails via `MailClient` (Stub + Resend,
best-effort), dispatched **after** the billing tx commits — renewals from the job, cancellation from the
controller, each in its own try/catch: `CHARGED_SUCCESS`→renewal, `CHARGED_FAILED_PAST_DUE`→payment-failed
(per retry, "attempt N/3"), `CHARGED_FAILED_EXPIRED`+`EXPIRED_NO_RENEW`→expired, cancel→confirmation.

**Locked auto-renew rules (as built):** renew on the `end_at` day (charge → push `end_at` +`durationDays`,
stay ACTIVE); on failure → **PAST_DUE** grace, **retry once/day for 3 attempts**, each failure emails; 3rd
failure → **EXPIRED** + capacity freed; cancel sets `auto_renew=false` + keeps access **until `end_at`** then
EXPIRES (no refund, no immediate cutoff).

**Fund-distribution / payout = STILL AN UNFILLED SEAM** — `Payment` snapshots commission, but nothing pays
coaches; model **A (iyzico Marketplace sub-merchant) vs B (single-merchant manual payout) pending the
accountant**. Not hardcoded into the flow.

**Stage 2 (next) swaps in:** a real `RealIyzicoClient @Profile("!test")` (saved-card **tokenization** + **recurring
charge**) replacing the stub, the stub flipping to `@Profile("test")` — same final shape as Meet/Mail. Sandbox
keys (merchant 3429394) go in `application-local.yml`. No core-flow change expected (the seam is the only swap).

### Safety & Moderation Notification Emails (Phase 10 / fix/safety-notification-emails)

- **What:** Added transactional email notifications for report, admin status update, and suspension events via the best-effort `MailClient` Resend integration.
- **Rules:**
  - **Report creation:** Confirms to the reporter that their report was received. Intentionally does **not** email the reported user (anti-harassment check).
  - **Report status moderation:** Emails the reporter on a successful transition of report status to `REVIEWED`, `RESOLVED`, or `DISMISSED`. No email sent on invalid transitions or same-status no-ops.
  - **User suspension:** Emails the suspended user to notify them that their account access has been restricted. Stays quiet if target user is not found or suspension fails (e.g. self-suspend check).
- **Execution:** Mail dispatch happens synchronously at the controller level immediately after service-level `@Transactional` write-tx commits. Caught-and-swallowed inside a double-backstop try-catch block so mail-server failures/timeouts **never** block the API response or roll back database records. SMS and unsuspend flows remain future-phase/deferred.
- **Tests:** Added 10 controller/service tests ensuring best-effort exception swallowing, mail-client method targeting, DTO mail metadata propagation, and state-transition gate checks.

---


## What's next

| Phase / Slice | Scope |
| --- | --- |
| **KVKK & Consent** | ✅ **DONE** (Phases 7-8). Implemented `dateOfBirth` validation, minor age checks (under 18), `ConsentStatus` tracking (`PENDING`, `ACCEPTED`, `REVOKED`), parental consent modal frontend, and backend enforcement gates. |
| **Admin Oversight & Safety** | ✅ **DONE**. Created safety reports, user suspension dashboard, and admin conversation inspection audit logger (`AdminConversationAccessLog`). Also implemented admin safety report status moderation flow (`PATCH /api/v1/admin/reports/{reportId}/status` to transition report status, tracking reviewedBy/reviewedAt) and the duplicate open report safety guard (`DUPLICATE_OPEN_REPORT`). |
| **Admin Finance & Payout Seam** | ✅ **DONE** (Phases 9-10). Created refund/termination modal popups, webhook signature validation, idempotency guards, and model-neutral payout ledger seam (`PayoutService` and `PayoutLedger`). |
| **Auth Rate Limiting** | ✅ **DONE** (Stabilization). Implemented Redis-compatible transient state rate limiting behind `RateLimitStore` with clean in-memory fallback. Protects `/auth/login`, `/auth/register`, and `/auth/refresh` against brute-force/abuse. |
| **Future / Launch Blockers** | Real money transactions production configuration, legal review of auto-renew/refund policies, final production DB seeding, and domain registration. |

### Deferred slices (carved out of the doc's original phasing — deliberate, see below)

| Slice | Scope |
| --- | --- |
| **Scheduling / Reminders** (own phase) | **Session reminder ✅ DONE (2026-08-11, V27):** `SessionReminderJob`, every 15 min, ~24h lead time, `reminder_sent_at` atomic-claim idempotency — see the top section. Still deferred: the **pre-renewal notice** (*"your subscription auto-renews on X — you can cancel"*, sent ahead of the charge) — no job/marker for this one yet; could hang off a sibling of `SessionReminderJob` using the same claim pattern against `Subscription`. |
| **In-app `Notification` entity** (own slice) | Persisted in-app notifications (`Notification`: `user_id`, `type`, `title`, `content`, `read_at`, `related_type`, `related_id`) + list/mark-read endpoints. **Not built.** Structurally independent of the email transport (no shared code) → its own vertical slice (entity→repo→service→controller→DTO→mapper→**V10 migration**→tests). May pair naturally with the Scheduling phase (shared triggers). |

> **PHASES.md divergence (recorded, deliberate):** `PHASES.md` Phase 7 bundles "persist in-app
> notifications (`Notification` entity)" **into** the notification phase. We **split it out**: Phase 7 =
> outbound Resend email only; the in-app `Notification` entity is its own future slice. Same
> split-a-big-phase move already used for Phase 4 (4a–4d) and Phase 5 (5a–5c) — consistent with how this
> project is run. The two (B) time-driven emails were likewise carved into the Scheduling phase.

### Monetization model (CHANGED 2026-06-27 — reverses the earlier one-time-purchase model)

- **Auto-renew is now IN SCOPE** (reverses "one-time monthly purchase / no auto-renew / single charge").
  Subscriptions **auto-renew monthly until the user cancels**. Requires **saved-card tokenization** +
  a **scheduled monthly charge**. (The old "iyzico single-charge, no recurring" DO-NOT / rejected-proposal
  in CLAUDE.md has been removed — recurring is the model now.)
- **Fund-distribution model is NOT finalized** (pending the user's accountant). Two candidates:
  **(A)** iyzico **Marketplace / sub-merchant** — iyzico auto-splits funds to coach + platform; **(B)**
  **single platform merchant** — all funds to the platform, which tracks each coach's earnings and pays
  out manually/batch. **Design rule:** build subscription / renewal / cancellation logic now; keep
  **fund distribution / payout as an isolated SEAM** — do not hardcode either model. (The earlier
  "no escrow / Marketplace" rejected-proposal is now **OPEN / under review**, not settled.)
### Known gaps (intentional, scheduled)

- **Fund distribution / payout to coaches is NOT implemented:** No database schema or production code exists. Only architectural recommendations are prepared in `docs/adr_coach_payout_architecture.md`.
- **Mid-session WebSocket token expiry is not enforced** — a JWT is validated only at CONNECT; an already-open session is not force-closed when its access token later expires.
- **Refresh Token Reuse/Theft Detection is deferred for MVP:** Refresh tokens are stored hashed and rotated on use, but full theft/reuse detection (i.e. revoking the entire token family/session if a revoked token is reused) is not implemented.
- **Google OAuth2 not live-tested** (no Workspace/creds); login wiring exists but is inert without creds.
- **Reviews don't exist** → `CoachStats.rating` is always null (totalSessions is real).
- **Tax/Billing details:** The exact choice of Split Payout vs Single Platform Payout is pending the project accountant's feedback.
- **Admin oversight reads currently leave no audit trail** — Phase 5c ships ADMIN conversation/message reads
  with no access log. A deliberate, documented interim gap; the **admin-access audit log (KVKK)** closes it in
  **Phase 9**.
- **5c integration tests are ID-scoped but not isolated** — `AdminConversationIntegrationTest` asserts on
  specific seeded conversation ids (not page-wide totals), but the shared, non-rollback `@SpringBootTest`
  **Tech-debt:** add per-class cleanup (or a transactional/isolated fixture) so the large-page workaround can go.
- _(Resolved)_ **iCloud sync + repo not versioned** — repo moved to `~/dev/demo` (non-synced) and
  `git init` done (initial commit `cda5c73`); see "Security rotations — RESOLVED" below.
- **iyzico webhook callback URL has a hardcoded localhost fallback:** `RealIyzicoClient.java:78`
  falls back to `http://localhost:8080/api/v1/payments/iyzico/webhook` when
  `properties.callbackUrl()` is blank. If this ever fires in prod (callback URL not configured),
  iyzico gets handed an unreachable localhost address and payment success/failure webhooks silently
  never arrive — checkouts would stay stuck `PENDING_PAYMENT` with no error surfaced anywhere. Not
  fixed yet; needs a real config property (mirroring how `FRONTEND_BASE_URL` /
  `OAUTH2_FRONTEND_REDIRECT_URI` are handled) before going live, ideally paired with a startup
  fail-fast check outside local/stub — same shape as `ResendConfig.requireExplicitResendFrom`.

---

## Security rotations — RESOLVED (2026-06-27)

Both standing credential-rotation action items are **DONE** (no longer open):

- **Neon password — ROTATED.** Old `npg_KlC7…` (shared in plaintext during setup) replaced with a fresh
  password reset from the Neon console. `NEON_DATABASE_URL` in the gitignored `application-local.yml`
  updated (jdbc form, pooled `-pooler` host, `sslmode=require&channelBinding=require` preserved).
  Verified by booting `-Plocal`: connects to Neon, Flyway validates 9 migrations, schema at V9.
- **Resend API key — ROTATED.** The key used for the Phase 7 live smoke (exposed in chat) was replaced
  with a fresh key; `RESEND_API_KEY` in `application-local.yml` updated. Old key retired.
- Both secrets live **only** in the gitignored `application-local.yml` — verified absent from every
  tracked/committed file. Repo now lives at `~/dev/demo` (non-iCloud-synced); `git init` done
  (initial commit `cda5c73`).

---

## Workflow rules (follow these every session)

1. **Feature Branch Workflow:** Always work on a feature branch (e.g., `feature/post-merge-completion`). Never commit directly to `main`.
2. **Compile and Test BEFORE Pull Request:** Compile both backend and frontend, run tests locally, fix all compiler and test errors, and then submit a PR to `main`.
3. **Never edit applied migrations:** Applied Flyway migrations (like V14, V15, V16) are permanently locked on Neon DB. Any schema change must be a fresh migration (e.g. V17, V18).
4. **Plan first, wait for approval:** Present a short plan and wait before starting execution.
5. **Honor CLAUDE.md guidelines:** Concrete services, standard ProblemDetail formatting, atomic capacity limits, and Testcontainers.

---

## Migrations (V1–V27)

V1 baseline · V2 auth · V3 seed_admin · V4 coach_profile · V5 packages_subscriptions · V6 coach_availability · V7 sessions · V8 session_meet_link · V9 messaging · V10 payments_autorenew · V11 webhook_verifications · V12-V13 safety/admin · V14-V16 demo seed · V17 legacy minor-consent status · V18 OAuth login codes · V19 versioned legal documents and acceptances · V20 checkout legal documents and transaction-linked evidence · V21 privacy preferences and account deletion · V22 password recovery/security · V23 email verification · V24 trial consultations · V25 purchase confirmation and refund requests · V26 media assets and profile media · V27 session reminder (`sessions.reminder_sent_at`).

## Versioned registration legal acceptance

- `KVKK_NOTICE` (Aydınlatma Metni) and `EXPLICIT_CONSENT` (Açık Rıza) are distinct. KVKK Notice is publicly readable and has no mandatory acceptance checkbox.
- Registration requires current published `TERMS_OF_USE` and `EXPLICIT_CONSENT` document IDs. Marketing email and SMS preferences are separate, optional opt-ins.
- No guardian account or guardian verification exists. `date_of_birth` and legacy `consent_records` remain for compatibility/audit, but age or a revoked legacy record no longer creates a separate checkout, booking, conversation, or message gate.
- Existing users are backfilled as legally onboarded to avoid lockout. Newly created Google OAuth users are not auto-accepted and use `POST /api/v1/auth/legal-onboarding` before protected product actions.
- V19 seeds clearly labelled placeholder version `1.0` documents. Approved legal wording must replace them before production reliance.

## Checkout legal acceptance

- The UI presents one mandatory checkbox covering the current Ön Bilgilendirme Formu, Mesafeli Satış Sözleşmesi, and İade / İptal Politikası.
- The backend validates all three submitted current document IDs and creates three separate immutable acceptance records. Every record snapshots document type, version, content hash, and acceptance time.
- Checkout acceptance evidence is linked to both the reserved subscription and its initial payment attempt. The database permits one active evidence row per checkout/document and keeps registration/onboarding uniqueness separate.
- Package, coach, duplicate-subscription, user, and legal-onboarding checks run before checkout-document validation. Subscription/payment/evidence commit together; Iyzico initialization remains outside the transaction and is never called after legal validation failure.
- V20 content is explicit placeholder text pending approved legal wording.
- Refund eligibility and the technical event defining service commencement remain unresolved and unchanged. This work adds no automatic refund decision or coach-transfer behavior.

## Privacy preferences, consent withdrawal, and account deletion

- Marketing email and SMS permissions are optional, explicit, channel-specific states. Registration/onboarding opt-ins now persist through `marketing_preferences`; existing users and omitted flags default to not granted. Transactional service, payment, booking, subscription, security, and moderation emails do not consult marketing preferences.
- Authenticated cookie preferences are stored in `privacy_preferences`. Necessary storage is always enabled, while analytics and marketing default to false and require the current effective published `COOKIE_POLICY`. Anonymous visitor choices remain a frontend/browser-local concern; no anonymous tracking identifier was introduced.
- `POST /api/v1/privacy/explicit-consent/withdraw` timestamps active Explicit Consent evidence and sets `legalOnboardingCompleted=false`. Terms and KVKK Notice are untouched. Checkout, booking, conversation creation, and message sending retain their existing legal-onboarding gate; the existing onboarding API can record the current consent again.
- Account deletion is a synchronous controlled workflow: it records a deletion request, sets `UserStatus.DELETED`, revokes refresh tokens and pending OAuth login codes, unlinks Google identity, clears profile PII and optional preferences, and replaces account identity with a unique non-original placeholder. Deleted identity hashes prevent password/Google recreation with the same identity.
- The main user, messages, reports, subscriptions, payments/refunds, legal acceptances, checkout evidence, and audit/security records are retained for integrity and dispute/accounting needs. Exact statutory retention periods—including message retention—and operational deletion of external storage objects remain production legal-policy TODOs.
- Suspended users cannot currently self-request deletion because the JWT filter blocks all suspended-account API access; an admin/support remediation path is still needed. Refund/service-commencement and coach-transfer rules are unchanged.

## Critical-path tests (the ones to never break)
`AuthServiceTest` · `SubscriptionCapacityConcurrencyTest` · `SessionDoubleBookingConcurrencyTest` · `SessionQuotaBoundaryTest` · `SessionLifecycleTest` · `MessageServiceTest` / `MessageGateIntegrationTest` (child-safety gate) · `WebSocketAuthTest` · `SubscriptionRenewalConcurrencyTest` · `SubscriptionBillingServiceTest` · `MinorConsentServiceTest` · `InMemoryRateLimitStoreTest` · `AuthRateLimitServiceTest` · `AuthControllerRateLimitTest`.

---

## Phase 5c — Admin oversight (DONE)

**Shipped as designed** (see the as-built summary under "What each phase delivered → 5c"). The locked
decisions below were all honored; kept here as the rationale record.

- **Endpoints:** `GET /api/v1/admin/conversations` and `GET /api/v1/admin/conversations/{id}/messages`,
  **ADMIN-only** — reuse the Phase 2 admin security pattern (class-level `@PreAuthorize("hasRole('ADMIN')")`,
  same shape as `AdminCoachController`). Non-admin → **403**.
- **Separate service, no gate:** create a dedicated **`AdminConversationService`** that reads **directly
  from the repositories** — admin reads must **never** go through `MessageService`. Participants only ever
  touch `MessageService`; admins only ever touch `AdminConversationService`. This physical separation is a
  **child-safety requirement** — do **NOT** add `...ForAdmin` methods to `MessageService`.
- **Read-only and invisible:** admin reads must **not** touch `read_at`, `last_message_at`, or any
  membership state (an admin viewing a thread changes nothing). **This must be asserted in tests** — it's
  the most easily-missed Phase 5 invariant (e.g. snapshot `read_at`/`last_message_at` before the admin
  read, assert unchanged after).
- **List scope (narrow for beta):** list + order by `lastMessageAt desc`; each summary = participants
  (student name + coach name/university) + `lastMessageAt` + `messageCount`. **No filters yet.**
  - Pagination **mandatory**: `Pageable` (default 20, **max 100**), sort whitelist, wrap in
    `PageResponse<T>`. Avoid N+1 — use a projection / DTO query for the summary (incl. messageCount),
    don't loop per-conversation.
- **Deferred:** admin-access **audit log** is **Phase 9** (KVKK-relevant). Phase 5c ships without it — a
  deliberate, documented interim gap (admins can read threads with no access trail until Phase 9).

---

## Rate Limiting & Transient State

- **Endpoints protected:**
  - `POST /api/v1/auth/login` (IP limit: 20/min, Email limit: 5/min)
  - `POST /api/v1/auth/register` (IP limit: 5/min)
  - `POST /api/v1/auth/refresh` (IP limit: 30/min, Token fingerprint limit: 30/min)
- **Response behavior:** Breaches yield `HTTP 429 Too Many Requests` with a ProblemDetail JSON carrying `errorCode: RATE_LIMIT_EXCEEDED` and a standard `Retry-After: <seconds>` header.
- **Client IP Resolution / shared rate limits:** The default/production rate-limit store is Redis and requires `REDIS_URL`; local/test/stub retain the in-memory store. All counters use the same atomic Redis increment/expiry script across replicas. The explicit outage policy defaults to `IN_MEMORY_FALLBACK` and may be changed to `FAIL_CLOSED`. Client IP defaults to the socket remote address. `X-Forwarded-For` is authoritative only when `RATE_LIMIT_TRUST_PROXY_HEADERS=true` and the socket peer matches `RATE_LIMIT_TRUSTED_PROXY_CIDRS`; direct spoofed headers are ignored. The reverse proxy must sanitize forwarding headers before production enables either that mode or Spring's forwarded-header framework.
- **Sensitive data privacy:** Key identifiers are formatted to prevent sensitive data leaks. IP addresses are hashed using SHA-256; login emails are normalized (trim, lowercase) and hashed; refresh tokens are hashed to create unique fingerprints.
- **Transient state architecture:**
  - Transient state (such as rate limits) is decoupled behind the `RateLimitStore` interface.
  - The current production-ready MVP uses `InMemoryRateLimitStore` (ConcurrentHashMap in JVM memory), which automatically runs background cleanups on expired buckets.
  - **In-memory/per-instance constraint:** Since the state is in JVM memory, it is scoped per application instance. If the backend scales to 2+ instances, key counters won't be shared across nodes unless a shared store is introduced.
- **Redis Migration Path:**
  - Future Redis migration requires zero code changes to controllers or business services.
  - A developer simply needs to add the Spring Data Redis dependency, define `RedisRateLimitStore implements RateLimitStore`, and declare it as a `@Bean` to override the memory implementation.
  - Hashing rules, env-prefixes (`yks:{env}:rate-limit:...`), and window algorithms are already fully Redis-compatible (mapped directly to `INCR` + `EXPIRE` commands or custom Lua scripts).
# Password recovery and security (V22)

- Password-backed accounts can change their password at `/security` at any time; every successful change still invalidates current sessions.
- Google-only accounts have no local password and neither see nor receive a local-password reset flow.
- Forgot-password responses are intentionally generic. Eligible accounts receive a 30-minute, single-use link; only its SHA-256 hash is persisted and passwords are never emailed.
- Successful change/reset revokes every refresh token and increments the JWT `passwordVersion`, so older access tokens are rejected. Change requires re-login; reset never auto-logs in.
- Production requires `FRONTEND_BASE_URL`, `RESEND_API_KEY`, and `RESEND_FROM`. Reset mail is transactional and independent of marketing preferences.

## Email ownership verification (V23)

- Normal password registration creates an authenticated but limited account with `emailVerified=false`, issues a cryptographically random 6-digit code, stores only its BCrypt hash, and sends it after transaction commit. Codes are single-use and expire after 10 minutes.
- `POST /api/v1/auth/verify-email` and `POST /api/v1/auth/resend-verification` resolve the authenticated user rather than accepting a user ID. Resend has a persisted 60-second cooldown plus per-user/IP hourly rate limits; a new code invalidates older codes.
- Google sign-in trusts the actual Google `email_verified` claim. A verified Google email sets `emailVerified=true`, skips verification mail, and remains independently subject to legal onboarding.
- Email verification and `legalOnboardingCompleted` are separate readiness flags. Checkout, booking/session creation, conversation creation, and message sending require both; failures remain distinct as `EMAIL_VERIFICATION_REQUIRED` and `LEGAL_ONBOARDING_REQUIRED`.
- Frontend route priority is `/verify-email`, then `/legal-onboarding`, then the role home. `/privacy`, `/security`, verification, onboarding, and session-management auth calls remain reachable for limited accounts.
- Verification mail uses the existing best-effort Resend/stub transport and ignores marketing opt-in state. Production `RESEND_API_KEY`, verified sender/domain, and deployment configuration remain external work.

## WebSocket /ws Endpoint Security & Node.js Test Script (2026-08-16)

To permanently resolve the CORS issue with `sockjs-client` pre-flight requests in production, the `/ws/**` endpoint has been explicitly ignored from the Spring Security filter chain (`WebSecurityCustomizer.ignoring()`). 
Security regression tests in `WebSocketAuthTest` confirm that `StompAuthChannelInterceptor` correctly validates JWTs, rejects missing/invalid tokens, enforces suspended/deleted user bans, and authorizes `SUBSCRIBE` actions based on conversation membership at the STOMP message level.

Because `WebSocketStompClient` in Java tests bypasses browser-level network constraints, a Node.js verification script is provided to test the STOMP connection under realistic conditions.

**Running the manual verification script:**
A Node.js script located at `scripts/ws_test.mjs` acts as an external client using `ws` and `@stomp/stompjs`. It verifies that the `/ws` endpoint accepts raw WebSocket STOMP connections without HTTP-level filter chain interference.

```bash
# From the project root
npm install ws @stomp/stompjs
node scripts/ws_test.mjs <jwt_token> <conversation_id>
```
The script will connect to `ws://localhost:8080/ws`, subscribe to the given conversation topic, and send a test message.
