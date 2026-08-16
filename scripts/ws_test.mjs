/**
 * ws_test.mjs — Manual WebSocket/STOMP verification script
 *
 * Tests the /ws endpoint as a Node.js client to prove that:
 *  1. The Spring Security filter chain ignoring /ws/** does NOT break STOMP auth
 *  2. StompAuthChannelInterceptor still validates the JWT on CONNECT
 *  3. A valid participant can subscribe and send messages
 *
 * Connects over the SockJS WebSocket transport URL (/ws/{server}/{session}/websocket),
 * which is the same path the browser takes — useConversationSocket.ts always wraps the
 * connection in SockJS, so this exercises what the frontend actually does.
 *
 * The port is a parameter on purpose. A local HTTP-aware proxy can transparently
 * intercept a port and corrupt the post-upgrade WebSocket byte stream while leaving
 * plain HTTP working — a 1002 close ("unrecognised opCode" / "reserved bits") with no
 * STOMP ERROR frame is the signature. Retry on another port before suspecting the app.
 * See handoff.md, "Port 8080 intercepted by a local proxy" (2026-08-16).
 *
 * Usage (from scripts/ directory):
 *   node ws_test.mjs <jwt_token> <conversation_id> [port] [allowed_origin]
 *
 *   jwt_token        A valid JWT access token for a student or coach
 *   conversation_id  ID of a conversation the user is a participant of
 *   port             Optional. Default: 8080
 *   allowed_origin   Optional. Default: http://localhost:5173
 *
 * Quick start — get token + conv ID automatically (override PORT if the backend moved):
 *   cd scripts/
 *   PORT=8080
 *   TOKEN=$(curl -s -X POST http://localhost:$PORT/api/v1/auth/login \
 *     -H "Content-Type: application/json" \
 *     -d '{"email":"student.active.demo@example.com","password":"Password123!"}' \
 *     | node -e "process.stdin.resume();let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).accessToken))")
 *   CONV=$(curl -s http://localhost:$PORT/api/v1/conversations \
 *     -H "Authorization: Bearer $TOKEN" \
 *     | node -e "process.stdin.resume();let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d)[0].id))")
 *   node ws_test.mjs "$TOKEN" "$CONV" "$PORT"
 *
 * For an end-to-end round trip (login + subscribe + send + broadcast) see
 * ws_roundtrip_probe.mjs, which takes just a port.
 */

import WebSocket from 'ws';

const args = process.argv.slice(2);
if (args.length < 2) {
    console.error('Usage: node ws_test.mjs <jwt_token> <conversation_id> [port] [allowed_origin]');
    process.exit(1);
}

const token = args[0];
const conversationId = args[1];
const port = args[2] ?? '8080';
const allowedOrigin = args[3] ?? 'http://localhost:5173';

// SockJS WebSocket transport URL format: /ws/{server}/{session}/websocket — the same
// URL shape sockjs-client builds in the browser.
const sessionId = Math.random().toString(36).substring(2, 10);
const wsUrl = `ws://localhost:${port}/ws/000/${sessionId}/websocket`;

console.log(`\n=== YKS WebSocket / STOMP manual verification ===`);
console.log(`URL:          ${wsUrl}`);
console.log(`Origin:       ${allowedOrigin}`);
console.log(`Conversation: ${conversationId}`);
console.log(`Token:        ${token.substring(0, 40)}...\n`);

let stompConnected = false;
let receivedBroadcast = false;
let exitCode = 0;

// SockJS protocol helpers
// Server → client: "o" (open), "a[...]" (message array), "c[...]" (close)
// Client → server: JSON.stringify([frame])
function sockjsSend(ws, stompFrame) {
    ws.send(JSON.stringify([stompFrame]));
}

const ws = new WebSocket(wsUrl, {
    headers: { Origin: allowedOrigin },
    perMessageDeflate: false,
    extensions: {}
});

ws.on('open', () => {
    console.log('✅ WS transport opened (SockJS handshake OK)');
});

ws.on('message', (raw) => {
    const str = raw.toString();

    if (str === 'o') {
        // SockJS open — now send STOMP CONNECT with JWT
        console.log('✅ SockJS "open" received — sending STOMP CONNECT with JWT...');
        const connectFrame =
            `CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n` +
            `Authorization:Bearer ${token}\n\n\0`;
        sockjsSend(ws, connectFrame);
        return;
    }

    if (str.startsWith('a[')) {
        // SockJS message array wrapping one or more STOMP frames
        let frames;
        try { frames = JSON.parse(str.substring(1)); }
        catch { console.error('Could not parse SockJS frame:', str); return; }

        for (const f of frames) {
            if (f.startsWith('CONNECTED')) {
                stompConnected = true;
                console.log('✅ STOMP CONNECTED frame received!');
                console.log('   → StompAuthChannelInterceptor accepted the JWT on CONNECT');
                console.log('   → Spring Security filter chain ignoring /ws/** is working correctly\n');

                // Subscribe to the conversation topic
                const topic = `/topic/conversations/${conversationId}`;
                console.log(`📡 Subscribing to ${topic}...`);
                const subFrame = `SUBSCRIBE\nid:sub-0\ndestination:${topic}\n\n\0`;
                sockjsSend(ws, subFrame);

                // After a short delay, send a test message
                setTimeout(() => {
                    const dest = `/app/conversations/${conversationId}/send`;
                    const payload = `[ws_test.mjs probe @ ${new Date().toISOString()}]`;
                    console.log(`📤 Sending to ${dest}: ${payload}`);
                    const sendFrame =
                        `SEND\ndestination:${dest}\ncontent-type:text/plain\n\n${payload}\0`;
                    sockjsSend(ws, sendFrame);
                }, 400);

                // Disconnect after 3 seconds
                setTimeout(() => {
                    if (!receivedBroadcast) {
                        console.warn('⚠️  No broadcast received within 3s (message may have stored but not echoed)');
                    }
                    console.log('\n✅ Disconnecting cleanly...');
                    sockjsSend(ws, 'DISCONNECT\n\n\0');
                    setTimeout(() => { ws.close(); process.exit(exitCode); }, 500);
                }, 3000);

            } else if (f.startsWith('ERROR')) {
                console.error('❌ STOMP ERROR frame received:');
                const lines = f.split('\n');
                const msg = lines.find(l => l.startsWith('message:'));
                console.error('   ', msg || f.substring(0, 200));
                if (!stompConnected) {
                    console.error('   → CONNECT was rejected — check token validity');
                }
                exitCode = 1;
                ws.close();

            } else if (f.startsWith('MESSAGE')) {
                receivedBroadcast = true;
                console.log('📩 Received MESSAGE broadcast (round-trip confirmed!):');
                const bodyStart = f.indexOf('\n\n');
                if (bodyStart !== -1) {
                    try {
                        const body = JSON.parse(f.substring(bodyStart + 2).replace('\0', ''));
                        console.log(`   content: ${body.content}`);
                        console.log(`   sentAt:  ${body.sentAt ?? body.createdAt ?? '(not in payload)'}`);
                        console.log(`   senderId: ${body.senderId}`);
                    } catch {
                        console.log('   raw body:', f.substring(bodyStart + 2, bodyStart + 102));
                    }
                }
            } else {
                console.log('Other STOMP frame:', f.substring(0, 80));
            }
        }
    }

    if (str.startsWith('c[')) {
        // SockJS close
        const info = JSON.parse(str.substring(1));
        console.log('SockJS close:', info);
    }
});

ws.on('error', (e) => {
    console.error('❌ WebSocket error:', e.message);
    exitCode = 1;
    setTimeout(() => process.exit(exitCode), 200);
});

ws.on('close', (code, reason) => {
    if (!stompConnected) {
        console.error(`❌ Connection closed before STOMP CONNECTED: code=${code} reason="${reason}"`);
        process.exit(1);
    }
});

// Timeout guard
setTimeout(() => {
    if (!stompConnected) {
        console.error('❌ Timeout: no STOMP CONNECTED received within 10s');
        process.exit(1);
    }
}, 10000);
