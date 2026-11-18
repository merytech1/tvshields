// Records every network request of the TV's WebView page for a while, through the WebView's
// DevTools (debug builds only). Used for the per-site inventories in docs/testing.md (phase 8).
//
//   adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
//   node tools/capture/requests.mjs <out.json> [seconds] [url]
//
// With a URL the page navigates there; without one, it records whatever loads (for example the
// reload after turning Shields off for the site). The browser cache is disabled while recording.

import fs from 'node:fs';

const [out, seconds = '30', url] = process.argv.slice(2);
if (!out) {
  console.error('usage: node tools/capture/requests.mjs <out.json> [seconds] [url]');
  process.exit(2);
}
const endpoint = process.env.CDP_ENDPOINT ?? 'http://127.0.0.1:9222';

const pages = await (await fetch(`${endpoint}/json/list`)).json();
const page = pages.find((p) => p.type === 'page');
if (!page) throw new Error('No page target: is the app open on a debug build?');

const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });

let nextId = 1;
const pending = new Map();
const requests = new Map();
ws.onmessage = ({ data }) => {
  const msg = JSON.parse(data);
  if (msg.id) {
    pending.get(msg.id)?.(msg);
    pending.delete(msg.id);
    return;
  }
  const p = msg.params;
  switch (msg.method) {
    case 'Network.requestWillBeSent':
      // A redirect reuses the request id: keep the first URL and note where it went.
      if (requests.has(p.requestId)) {
        requests.get(p.requestId).redirects.push(p.request.url);
      } else {
        requests.set(p.requestId, {
          url: p.request.url,
          method: p.request.method,
          type: p.type ?? null,
          documentUrl: p.documentURL,
          initiator: p.initiator?.type ?? null,
          redirects: [],
          status: null,
          mimeType: null,
          failed: null,
        });
      }
      break;
    case 'Network.responseReceived': {
      const r = requests.get(p.requestId);
      if (r) Object.assign(r, { status: p.response.status, mimeType: p.response.mimeType, type: p.type ?? r.type });
      break;
    }
    case 'Network.loadingFailed': {
      const r = requests.get(p.requestId);
      if (r) r.failed = p.blockedReason ?? p.errorText;
      break;
    }
  }
};
const send = (method, params = {}) => new Promise((resolve) => {
  const id = nextId++;
  pending.set(id, resolve);
  ws.send(JSON.stringify({ id, method, params }));
});

await send('Network.enable');
await send('Network.setCacheDisabled', { cacheDisabled: true });
if (url) await send('Page.navigate', { url });
console.log('recording');
await new Promise((resolve) => setTimeout(resolve, Number(seconds) * 1000));
await send('Network.setCacheDisabled', { cacheDisabled: false });
ws.close();

const list = [...requests.values()];
fs.writeFileSync(out, JSON.stringify({ recordedAt: new Date().toISOString(), seconds: Number(seconds), url: url ?? null, requests: list }, null, 1));
console.log(`${list.length} requests -> ${out}`);
