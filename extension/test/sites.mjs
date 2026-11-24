// Checks the page cleaner on copies of real sites, in a real browser with the built extension:
// ChatGPT (ads under answers), Copilot, Google and Bing search ads. The pages are served locally
// under the real site names (the browser is pointed at this machine for them), so nothing is
// fetched from those sites and no account is needed. Needs openssl (comes with Git for Windows).
//
//   node extension/test/sites.mjs [chrome|edge|brave]
import { execFileSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import https from 'node:https';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const EXT = path.join(ROOT, 'dist', 'chrome');
const BROWSERS = {
  edge: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  chrome: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  brave: 'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe',
};
const which = process.argv[2] || 'chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const HOSTS = ['chatgpt.com', 'copilot.microsoft.com', 'www.google.com.pk', 'www.bing.com'];

// ---- Pages ---------------------------------------------------------------------------------------

const IMG = 'data:image/gif;base64,R0lGODlhAQABAAAAACw=';

// An answer, then an ad unit like the one ChatGPT shows under it (no known ad markup, so only the
// "Ad" label and the "Ads do not influence" note can find it), then an ad in ChatGPT's own markup.
const CHATGPT = `<!doctype html><meta charset="utf-8"><title>ChatGPT</title>
<nav><a href="/c/1" id="navAd"><span>Ad</span></a><a href="/c/2">Sponsored</a></nav>
<main>
  <article data-testid="conversation-turn-2" id="turn">
    <div data-message-author-role="assistant">
      <div class="markdown prose" id="answer"><p>Use a hosted RPC endpoint.</p><p id="answerWord">Ad</p></div>
      <div class="actions"><button data-testid="copy-turn-action-button" id="copy">Copy</button></div>
    </div>
    <div id="adUnit">
      <p>For RPC in blockchain apps, here's one infrastructure API option.</p>
      <div class="card"><img src="${IMG}" alt=""><span>GetBlock</span><span id="adPill">Ad</span>
        <b>RPC for Production Apps</b><a href="https://getblock.example/?utm_source=x">Learn more</a></div>
      <p id="note">Ads do not influence the answers you get from ChatGPT. Your chats stay private. <a href="/ads">Learn about ads and personalization</a></p>
    </div>
  </article>
  <aside data-assistant-ads-unit id="markupAd"><a href="https://ad.example/">Ad by markup</a></aside>
  <form><textarea id="prompt">Ask anything</textarea></form>
</main>
<script>
  // Ad data as ChatGPT sends it, read the way the page reads it.
  const data = JSON.parse('{"suppress_ads":false,"blocks":[{"type":"ads","visibility":{"status":"allowed"}}],"messages":[{"id":1,"ads":[{"x":1}],"text":"hi"}]}');
  const streamed = new TextDecoder().decode(new TextEncoder().encode('data: {"type":"ads","visibility":{"status":"allowed"}}'));
  window.adData = { suppress: data.suppress_ads, status: data.blocks[0].visibility.status, messageAds: 'ads' in data.messages[0], text: data.messages[0].text, streamed };
  // An ad that arrives after the answer, like a real streamed reply.
  setTimeout(() => {
    const late = document.createElement('div');
    late.id = 'lateAd';
    late.innerHTML = '<div class="card"><img src="${IMG}" alt=""><span>Sponsored</span><a href="https://late.example/">Visit</a></div>';
    document.getElementById('turn').appendChild(late);
  }, 600);
</script>`;

const COPILOT = `<!doctype html><meta charset="utf-8"><title>Copilot</title>
<div class="markdown" id="answer">Here is your answer.</div>
<div data-testid="ads-card" id="adsCard"><a href="https://ad.example/">Ad card</a></div>`;

const GOOGLE = `<!doctype html><meta charset="utf-8"><title>rpc - Google Search</title>
<div id="tads"><div data-text-ad="1"><span>Sponsored</span><a href="https://ad.example/">Ad result</a></div></div>
<div id="search"><div class="g" id="organic"><a href="https://example.com/">Real result</a></div></div>
<div id="bottomads"><a href="https://ad2.example/">Bottom ad</a></div>`;

const BING = `<!doctype html><meta charset="utf-8"><title>rpc - Search</title>
<ol id="b_results"><li class="b_ad" id="bingAd"><a href="https://ad.example/">Ad result</a></li>
<li class="b_algo" id="organic"><a href="https://example.com/">Real result</a></li></ol>`;

const PAGES = { 'chatgpt.com': CHATGPT, 'copilot.microsoft.com': COPILOT, 'www.google.com.pk': GOOGLE, 'www.bing.com': BING };

// ---- HTTPS server under the real names ---------------------------------------------------------

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'adochi-sites-'));
const key = path.join(work, 'key.pem');
const cert = path.join(work, 'cert.pem');
try {
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', key, '-out', cert, '-days', '1',
    '-subj', '/CN=adochi-test', '-addext', 'subjectAltName=' + HOSTS.map((h) => 'DNS:' + h).join(',')], { stdio: 'ignore' });
} catch (e) {
  console.error('openssl is needed for this test (it comes with Git for Windows).');
  process.exit(2);
}
const server = https.createServer({ key: fs.readFileSync(key), cert: fs.readFileSync(cert) }, (req, res) => {
  const page = PAGES[(req.headers.host || '').split(':')[0]];
  res.writeHead(page ? 200 : 404, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(page || 'not found');
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

// ---- Browser over the DevTools pipe ------------------------------------------------------------

const profile = path.join(work, 'profile');
const browser = spawn(BROWSERS[which], [
  '--headless=new', '--remote-debugging-pipe', '--enable-unsafe-extension-debugging', '--ignore-certificate-errors',
  `--host-resolver-rules=${HOSTS.map((h) => `MAP ${h} 127.0.0.1:${port}`).join(',')}`,
  `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check', 'about:blank',
], { stdio: ['ignore', 'ignore', 'ignore', 'pipe', 'pipe'] });

let nextId = 1;
const waiting = new Map();
let buffer = '';
browser.stdio[4].on('data', (chunk) => {
  buffer += chunk.toString('utf8');
  let end;
  while ((end = buffer.indexOf('\0')) >= 0) {
    const msg = JSON.parse(buffer.slice(0, end));
    buffer = buffer.slice(end + 1);
    const w = waiting.get(msg.id);
    if (w) {
      waiting.delete(msg.id);
      msg.error ? w.reject(new Error(JSON.stringify(msg.error))) : w.resolve(msg.result);
    }
  }
});

function cdp(method, params = {}, sessionId) {
  const id = nextId++;
  browser.stdio[3].write(JSON.stringify({ id, method, params, sessionId }) + '\0');
  return new Promise((resolve, reject) => waiting.set(id, { resolve, reject }));
}

async function open(url) {
  const { targetId } = await cdp('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp('Target.attachToTarget', { targetId, flatten: true });
  await cdp('Page.enable', {}, sessionId);
  await cdp('Page.navigate', { url }, sessionId);
  await sleep(2500);
  return sessionId;
}

async function evaluate(sessionId, expression) {
  const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }, sessionId);
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + (r.exceptionDetails.exception?.description ?? ''));
  return r.result.value;
}

/** Which of the given element ids are hidden. */
const HIDDEN = (ids) => `(${JSON.stringify(ids)}).reduce((out, id) => {
  const el = document.getElementById(id);
  out[id] = !el ? 'missing' : (el.offsetParent === null || getComputedStyle(el).display === 'none');
  return out;
}, {})`;

const failures = [];
function expect(label, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  ' + detail : ''}`);
  if (!ok) failures.push(label);
}

try {
  const version = await cdp('Browser.getVersion');
  console.log(`${which}: ${version.product}`);
  await cdp('Extensions.loadUnpacked', { path: EXT });
  await sleep(4000);

  const gpt = await open('https://chatgpt.com/c/test');
  const g = await evaluate(gpt, HIDDEN(['adUnit', 'markupAd', 'lateAd', 'answer', 'answerWord', 'copy', 'turn', 'navAd', 'prompt']));
  console.log(JSON.stringify(g));
  expect('ChatGPT: ad unit under the answer hidden', g.adUnit === true);
  expect('ChatGPT: ad in ChatGPT markup hidden', g.markupAd === true);
  expect('ChatGPT: ad that arrives later hidden', g.lateAd === true);
  expect('ChatGPT: the answer stays', g.answer === false && g.answerWord === false && g.copy === false && g.turn === false);
  expect('ChatGPT: sidebar and prompt stay', g.navAd === false && g.prompt === false);
  const d = await evaluate(gpt, 'window.adData');
  console.log(JSON.stringify(d));
  expect('ChatGPT: ads switched off in the chat data', d.suppress === true && d.status === 'hidden' && d.messageAds === false && d.text === 'hi');
  expect('ChatGPT: ads hidden in the streamed answer', d.streamed.includes('"status":"hidden"'));

  const copilot = await open('https://copilot.microsoft.com/');
  const c = await evaluate(copilot, HIDDEN(['adsCard', 'answer']));
  expect('Copilot: ad card hidden, answer stays', c.adsCard === true && c.answer === false, JSON.stringify(c));

  const google = await open('https://www.google.com.pk/search?q=rpc');
  const go = await evaluate(google, HIDDEN(['tads', 'bottomads', 'organic']));
  expect('Google: sponsored results hidden, real results stay', go.tads === true && go.bottomads === true && go.organic === false, JSON.stringify(go));

  const bing = await open('https://www.bing.com/search?q=rpc');
  const b = await evaluate(bing, HIDDEN(['bingAd', 'organic']));
  expect('Bing: sponsored results hidden, real results stay', b.bingAd === true && b.organic === false, JSON.stringify(b));
} catch (e) {
  failures.push(String(e));
  console.error(e);
} finally {
  browser.kill();
  server.close();
  await sleep(1000);
  fs.rmSync(work, { recursive: true, force: true, maxRetries: 5 });
}

console.log(failures.length ? `\n${failures.length} FAILED` : '\nAll checks passed');
process.exit(failures.length ? 1 : 0);
