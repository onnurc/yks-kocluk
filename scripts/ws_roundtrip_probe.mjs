// Full chat round-trip over the SAME SockJS transport path the browser uses.
// Usage: node ws_roundtrip_probe.mjs <port>
import WebSocket from 'ws';

const port = process.argv[2] ?? '8080';
const base = `http://localhost:${port}`;

const login = async (email) => {
  const r = await fetch(`${base}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Password123!' }),
  });
  if (!r.ok) throw new Error(`login ${email} failed: ${r.status}`);
  return (await r.json()).accessToken;
};

const studentToken = await login('student.active.demo@example.com');
const convs = await (await fetch(`${base}/api/v1/conversations`, {
  headers: { Authorization: `Bearer ${studentToken}` },
})).json();
const conversationId = convs[0].id;
console.log(`port ${port}: logged in, conversation=${conversationId}`);

const sessionId = Math.random().toString(36).substring(2, 10);
const ws = new WebSocket(`ws://localhost:${port}/ws/000/${sessionId}/websocket`, {
  headers: { Origin: 'http://localhost:5173' },
});
const send = (frame) => ws.send(JSON.stringify([frame]));
const probeText = `roundtrip probe ${new Date().toISOString()}`;
let connected = false;

ws.on('message', (raw) => {
  const str = raw.toString();
  if (str === 'o') {
    send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ${studentToken}\n\n\0`);
    return;
  }
  if (!str.startsWith('a[')) return;
  for (const f of JSON.parse(str.substring(1))) {
    const cmd = f.split('\n')[0];
    if (cmd === 'CONNECTED') {
      connected = true;
      console.log('1. CONNECTED  — JWT accepted on the CONNECT frame');
      send(`SUBSCRIBE\nid:sub-0\ndestination:/topic/conversations/${conversationId}\n\n\0`);
      console.log('2. SUBSCRIBED — /topic/conversations/' + conversationId);
      setTimeout(() => {
        send(`SEND\ndestination:/app/conversations/${conversationId}/send\ncontent-type:text/plain\n\n${probeText}\0`);
        console.log('3. SENT       — via /app/conversations/' + conversationId + '/send');
      }, 300);
    } else if (cmd === 'MESSAGE') {
      const body = JSON.parse(f.substring(f.indexOf('\n\n') + 2).replace('\0', ''));
      console.log('4. BROADCAST  — received back over the topic:');
      console.log(`     content=${JSON.stringify(body.content)}`);
      console.log(`     sentAt=${body.sentAt}  senderId=${body.senderId}`);
      console.log(body.content === probeText ? '\n*** FULL ROUND TRIP OK ***' : '\n(content mismatch)');
      ws.close();
      process.exit(0);
    } else if (cmd === 'ERROR') {
      console.log('ERROR frame:', f.slice(0, 200));
      process.exit(2);
    }
  }
});
ws.on('close', (c, r) => { if (!connected) { console.log(`CLOSE ${c} ${r}`); process.exit(1); } });
ws.on('error', (e) => { console.log('ERR', e.message); process.exit(1); });
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 12000);
