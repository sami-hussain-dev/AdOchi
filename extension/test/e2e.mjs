// End-to-end check in a real browser: loads the built extension into a throwaway headless profile
// and checks blocking, page cleaning, the scam warning, pausing a site and the popup.
// "Loads" checks need those sites to be reachable from your network; Edge's own tracking
// prevention also blocks ad sites, so the pause/off checks are only meaningful in Chrome.
//
//   node extension/test/e2e.mjs [edge|chrome|brave] [screenshot-folder]
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
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
const which = process.argv[2] || 'edge';
const shots = process.argv[3];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- Test page -----------------------------------------------------------------------------------

const PAGE = `<!doctype html><title>AdOchi test</title>
<ins class="adsbygoogle" id="slot" style="display:block;width:300px;height:250px"></ins>
<div id="ok" class="header-download" style="width:10px;height:10px"></div>
<script>
async function probe(url) {
  try { await fetch(url, { mode: 'no-cors', cache: 'no-store' }); return 'loaded'; } catch (e) { return 'blocked'; }
}
window.run = async () => ({
  adsense: await probe('https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js'),
  doubleclick: await probe('https://securepubads.g.doubleclick.net/tag/js/gpt.js'),
  taboola: await probe('https://cdn.taboola.com/libtrc/loader.js'),
  fbPixel: await probe('https://connect.facebook.net/en_US/fbevents.js'),
  google: await probe('https://www.google.com/favicon.ico'),
  jsdelivr: await probe('https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js'),
  wikipedia: await probe('https://www.wikipedia.org/favicon.ico'),
  slotHidden: getComputedStyle(document.getElementById('slot')).display === 'none',
  normalVisible: getComputedStyle(document.getElementById('ok')).display !== 'none',
  cleanerLoaded: !!window.__adochiCleaner,
});
</script>`;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/html' });
  res.end(PAGE);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const pageUrl = `http://127.0.0.1:${server.address().port}/test.html`;

// ---- Browser over the DevTools pipe ------------------------------------------------------------

const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'adochi-e2e-'));
const browser = spawn(BROWSERS[which], [
  '--headless=new', '--remote-debugging-pipe', '--enable-unsafe-extension-debugging',
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

async function openTab(url) {
  const { targetId } = await cdp('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp('Target.attachToTarget', { targetId, flatten: true });
  await cdp('Page.enable', {}, sessionId);
  if (url) await go(sessionId, url);
  return { targetId, sessionId };
}

async function go(sessionId, url) {
  await cdp('Page.navigate', { url }, sessionId);
  await sleep(2500);
}

async function evaluate(sessionId, expression) {
  const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }, sessionId);
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + (r.exceptionDetails.exception?.description ?? ''));
  return r.result.value;
}

async function screenshot(sessionId, name, width, height) {
  if (!shots) return;
  await cdp('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 2, mobile: false }, sessionId);
  await sleep(400);
  const { data } = await cdp('Page.captureScreenshot', { format: 'png' }, sessionId);
  fs.mkdirSync(shots, { recursive: true });
  fs.writeFileSync(path.join(shots, `${which}-${name}.png`), Buffer.from(data, 'base64'));
}

const failures = [];
function expect(label, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  ' + detail : ''}`);
  if (!ok) failures.push(label);
}

try {
  const version = await cdp('Browser.getVersion');
  console.log(`${which}: ${version.product}`);
  const { id } = await cdp('Extensions.loadUnpacked', { path: EXT });
  await sleep(4000); // Let the background apply settings and register page scripts.

  const page = await openTab(pageUrl);
  const r = await evaluate(page.sessionId, 'run()');
  console.log(JSON.stringify(r));
  for (const key of ['adsense', 'doubleclick', 'taboola', 'fbPixel']) expect(`blocks ${key}`, r[key] === 'blocked');
  for (const key of ['google', 'jsdelivr', 'wikipedia']) expect(`loads ${key}`, r[key] === 'loaded');
  expect('hides empty ad slot', r.slotHidden);
  expect('leaves normal page parts alone', r.normalVisible);
  expect('page cleaner runs in the page', r.cleanerLoaded);

  // Scam site: first domain of the threat list, opened directly.
  const threats = JSON.parse(fs.readFileSync(path.join(EXT, 'rules', 'threats.json'), 'utf8'));
  const scam = threats[0].condition.requestDomains[0];
  const scamTab = await openTab(`http://${scam}/login`);
  await sleep(1500);
  const landed = await evaluate(scamTab.sessionId, 'location.href');
  expect('scam site shows the warning page', landed.startsWith(`chrome-extension://${id}/warning.html`), landed);
  expect('warning names the site', (await evaluate(scamTab.sessionId, "document.getElementById('host').textContent")) === scam);
  await screenshot(scamTab.sessionId, 'warning', 800, 760);

  // Popup for the test page, opened as a tab.
  const { targetInfos } = await cdp('Target.getTargets');
  const testTab = targetInfos.find((t) => t.targetId === page.targetId);
  const popup = await openTab(`chrome-extension://${id}/popup.html`);
  const realTabId = await evaluate(popup.sessionId, `chrome.tabs.query({}).then(ts => ts.find(t => t.url === ${JSON.stringify(testTab.url)}).id)`);
  await go(popup.sessionId, `chrome-extension://${id}/popup.html?tabId=${realTabId}`);
  const shown = await evaluate(popup.sessionId, "({ page: document.getElementById('page').textContent, site: document.getElementById('site').textContent, ads: document.getElementById('adsSub').textContent })");
  console.log(JSON.stringify(shown));
  expect('popup counts blocked requests', Number(shown.page) >= 4, shown.page);
  expect('popup shows the site', shown.site === '127.0.0.1');
  expect('popup shows list size', /K ad & tracker sites/.test(shown.ads), shown.ads);
  await screenshot(popup.sessionId, 'popup', 340, 620);

  // Pause on this site: ads load again; resume: blocked again.
  await evaluate(popup.sessionId, "document.getElementById('siteOn').click()");
  await sleep(2500);
  await go(page.sessionId, pageUrl);
  const paused = await evaluate(page.sessionId, 'run()');
  expect('pausing a site lets its ads through', paused.doubleclick === 'loaded' && paused.taboola === 'loaded', JSON.stringify(paused));
  await screenshot(popup.sessionId, 'popup-paused', 340, 560);
  await evaluate(popup.sessionId, "document.getElementById('siteOn').click()");
  await sleep(2500);
  await go(page.sessionId, pageUrl);
  expect('resuming blocks them again', (await evaluate(page.sessionId, 'run()')).doubleclick === 'blocked');

  // Master switch off: nothing blocked.
  await evaluate(popup.sessionId, "document.getElementById('enabled').click()");
  await sleep(2500);
  await go(page.sessionId, pageUrl);
  expect('master switch turns blocking off', (await evaluate(page.sessionId, 'run()')).doubleclick === 'loaded');
  await screenshot(popup.sessionId, 'popup-off', 340, 560);
  await evaluate(popup.sessionId, "document.getElementById('enabled').click()");
} catch (e) {
  failures.push(String(e));
  console.error(e);
} finally {
  browser.kill();
  server.close();
  await sleep(1000);
  fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5 });
}

console.log(failures.length ? `\n${failures.length} FAILED` : '\nAll checks passed');
process.exit(failures.length ? 1 : 0);
