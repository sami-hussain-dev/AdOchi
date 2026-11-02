// Builds the AdOchi browser extension: downloads the same free blocklists the Android app uses,
// turns them into browser blocking rules, and packs Chrome (also Edge, Brave, Opera) and Firefox builds.
//
//   node extension/tools/build.mjs            download fresh lists and build
//   node extension/tools/build.mjs --cached   reuse lists downloaded earlier (offline rebuild)
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const APP_ASSETS = path.resolve(ROOT, '../app/src/main/assets');
const SRC = path.join(ROOT, 'src');
const DIST = path.join(ROOT, 'dist');
const CACHE = path.join(ROOT, '.cache');
const useCache = process.argv.includes('--cached');

const HAGEZI = 'https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/';

// The app's default lists (core/FilterCatalog.kt).
const ADS = [
  { id: 'essentials', file: path.join(APP_ASSETS, 'filters/essentials.txt') },
  { id: 'social', file: path.join(APP_ASSETS, 'filters/social.txt') },
  { id: 'hagezi_pro', url: HAGEZI + 'pro-onlydomains.txt' },
  { id: 'hagezi_popup', url: HAGEZI + 'popupads-onlydomains.txt' },
];
const THREATS = [
  { id: 'hagezi_tif', url: HAGEZI + 'tif.medium-onlydomains.txt' },
  { id: 'hagezi_fake', url: HAGEZI + 'fake-onlydomains.txt' },
  { id: 'phishing_army', url: 'https://phishing.army/download/phishing_army_blocklist.txt' },
  { id: 'malware_phishing', url: 'https://malware-filter.gitlab.io/malware-filter/phishing-filter-domains.txt' },
  { id: 'malware_urlhaus', url: 'https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-domains.txt' },
];

// Sites that must keep working whatever a list says (same set as the app's LiveListsTest).
const MUST_WORK = [
  'facebook.com', 'www.facebook.com', 'm.facebook.com', 'graph.facebook.com', 'static.xx.fbcdn.net',
  'scontent.xx.fbcdn.net', 'www.messenger.com', 'whatsapp.com', 'web.whatsapp.com', 'wa.me',
  'www.instagram.com', 'i.instagram.com', 'www.tiktok.com', 'x.com', 'api.x.com', 't.me', 'web.telegram.org',
  'www.youtube.com', 'm.youtube.com', 'i.ytimg.com', 'rr3---sn-4g5e6nzz.googlevideo.com', 'youtubei.googleapis.com',
  'www.google.com', 'accounts.google.com', 'play.google.com', 'www.gstatic.com', 'fonts.googleapis.com',
  'fonts.gstatic.com', 'ajax.googleapis.com', 'mail.google.com', 'drive.google.com', 'maps.googleapis.com',
  'outlook.live.com', 'login.microsoftonline.com', 'www.hbl.com', 'hblibank.com.pk', 'www.meezanbank.com',
  'www.ubldigital.com', 'www.mcb.com.pk', 'www.bankalfalah.com', 'jazzcash.com.pk', 'easypaisa.com.pk',
  'sadapay.pk', 'nayapay.com', 'www.daraz.pk', 'www.bisp.gov.pk', 'www.nadra.gov.pk', 'www.fbr.gov.pk',
  'raw.githubusercontent.com', 'github.com', 'cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'unpkg.com',
  'code.jquery.com', 'chatgpt.com', 'claude.ai', 'www.wikipedia.org', 'zoom.us', 'www.netflix.com',
  'www.amazon.com', 'www.paypal.com', 'stackoverflow.com', 'www.linkedin.com',
];
const MUST_BLOCK = [
  'doubleclick.net', 'googleads.g.doubleclick.net', 'pagead2.googlesyndication.com', 'an.facebook.com',
  'adservice.google.com', 'popads.net', 'propellerads.com', 'adnxs.com', 'taboola.com', 'outbrain.com',
  'analytics.tiktok.com', 'ads.linkedin.com',
];

// Ads and trackers sharing a host with real content, so they're matched by address (BrowserRules in the app).
const PATH_RULES = [
  '||facebook.com/tr^',
  '||connect.facebook.net/*/fbevents.js',
  '||youtube.com/api/stats/ads',
  '||youtube.com/pagead/',
  '||youtube.com/ptracking',
  '||youtube.com/get_midroll_',
  '||google.com/pagead/',
];

const NOT_MAIN_FRAME = [
  'sub_frame', 'stylesheet', 'script', 'image', 'font', 'object', 'xmlhttprequest', 'ping', 'csp_report',
  'media', 'websocket', 'other',
];
const ALL_TYPES = ['main_frame', ...NOT_MAIN_FRAME];
const DOMAINS_PER_RULE = 1000;

// ---- Lists -------------------------------------------------------------------------------------

const IGNORED = new Set(['localhost', 'localhost.localdomain', 'local', 'broadcasthost', '0.0.0.0']);
// What browsers accept in a blocking rule: plain lower-case ASCII names.
const VALID = /^(?=.{3,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$/;

function normalize(input) {
  let d = input.trim().toLowerCase();
  if (d.startsWith('*.')) d = d.slice(2);
  d = d.replace(/^\.+|\.+$/g, '');
  if (IGNORED.has(d) || !VALID.test(d) || !/[a-z]/.test(d)) return null;
  return d;
}

/** Same formats as the app's ListParser: plain domains, hosts files, `||domain^` and `@@||domain^`. */
function parse(text, onBlock, onAllow) {
  for (let line of text.split('\n')) {
    line = line.trim();
    if (!line || '#!['.includes(line[0])) continue;
    const allow = line.startsWith('@@||');
    if (allow || line.startsWith('||')) {
      const start = allow ? 4 : 2;
      const caret = line.indexOf('^', start);
      const rest = caret >= 0 ? line.slice(caret + 1) : '';
      if (rest && rest !== '|' && rest !== '$important') continue;
      const name = line.slice(start, caret >= 0 ? caret : undefined);
      if (/[/*$]/.test(name)) continue;
      const d = normalize(name);
      if (d) (allow ? onAllow : onBlock)(d);
      continue;
    }
    const hash = line.indexOf('#');
    if (hash >= 0) line = line.slice(0, hash).trim();
    const tokens = line.split(/\s+/).filter(Boolean);
    if (tokens.length === 1) {
      const d = normalize(tokens[0]);
      if (d) onBlock(d);
    } else if (tokens.length > 1 && /^[\d.:]+$/.test(tokens[0])) {
      for (const t of tokens.slice(1)) {
        const d = normalize(t);
        if (d) onBlock(d);
      }
    }
  }
}

async function download(list) {
  if (list.file) return fs.readFileSync(list.file, 'utf8');
  const cached = path.join(CACHE, list.id + '.txt');
  if (useCache && fs.existsSync(cached)) return fs.readFileSync(cached, 'utf8');
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetch(list.url, { headers: { 'User-Agent': 'AdOchi-extension-build' } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const text = await res.text();
      if (/^\s*<(!doctype|html)/i.test(text)) throw new Error('got a web page, not a list');
      fs.mkdirSync(CACHE, { recursive: true });
      fs.writeFileSync(cached, text);
      return text;
    } catch (e) {
      if (attempt >= 3) throw new Error(`${list.id}: ${e.message}`);
      await new Promise((r) => setTimeout(r, 2000 * attempt));
    }
  }
}

function parents(domain) {
  const out = [];
  let i = domain.indexOf('.');
  while (i >= 0 && domain.indexOf('.', i + 1) >= 0) {
    out.push(domain.slice(i + 1));
    i = domain.indexOf('.', i + 1);
  }
  return out;
}

const covers = (set, domain) => set.has(domain) || parents(domain).some((p) => set.has(p));

async function collect(lists) {
  const block = new Set();
  const allow = new Set();
  for (const list of lists) {
    const text = await download(list);
    let n = 0;
    parse(text, (d) => { block.add(d); n++; }, (d) => allow.add(d));
    console.log(`  ${list.id.padEnd(18)} ${n.toLocaleString()} entries`);
    if (n === 0) throw new Error(`${list.id} is empty`);
  }
  // A rule for a.com already covers x.a.com, and listed exceptions win.
  const kept = [...block].filter((d) => !parents(d).some((p) => block.has(p)) && !covers(allow, d));
  return new Set(kept);
}

/** Drops anything that would break an essential site; returns what was dropped. */
function protectEssentials(set) {
  const dropped = [];
  for (const site of MUST_WORK) {
    for (const d of [site, ...parents(site)]) {
      if (set.delete(d)) dropped.push(d);
    }
  }
  return dropped;
}

function domainRules(set, firstId, resourceTypes) {
  const domains = [...set].sort();
  const rules = [];
  for (let i = 0; i < domains.length; i += DOMAINS_PER_RULE) {
    rules.push({
      id: firstId + rules.length,
      priority: 1,
      action: { type: 'block' },
      condition: { requestDomains: domains.slice(i, i + DOMAINS_PER_RULE), resourceTypes },
    });
  }
  return rules;
}

// ---- Packaging ---------------------------------------------------------------------------------

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, entry.name);
    const b = path.join(to, entry.name);
    if (entry.isDirectory()) copyDir(a, b);
    else fs.copyFileSync(a, b);
  }
}

function manifestFor(browser) {
  const m = JSON.parse(fs.readFileSync(path.join(SRC, 'manifest.json'), 'utf8'));
  if (browser === 'firefox') {
    m.background = { scripts: ['background.js'] };
    m.browser_specific_settings = { gecko: { id: 'adochi@adochi.app', strict_min_version: '128.0' } };
    m.developer = { name: 'x8code (Muhammad Sami Hussain)', url: 'https://x8code.com' };
  } else {
    m.background = { service_worker: 'background.js' };
    m.minimum_chrome_version = '111';
  }
  return m;
}

function zip(dir, file) {
  fs.rmSync(file, { force: true });
  // bsdtar ships with Windows 10+ and macOS; -a picks zip from the file name. On Windows use the
  // system copy by full path, since Git's GNU tar may come first on PATH and reads "D:" as a host.
  const tar = process.platform === 'win32' ? path.join(process.env.SystemRoot, 'System32', 'tar.exe') : 'tar';
  execFileSync(tar, ['-a', '-c', '-f', file, '-C', dir, '.'], { stdio: 'inherit' });
}

async function main() {
  console.log('Ad & tracker lists:');
  const ads = await collect(ADS);
  console.log('Scam & malware lists:');
  const threats = await collect(THREATS);

  const droppedAds = protectEssentials(ads);
  const droppedThreats = protectEssentials(threats);
  if (droppedAds.length || droppedThreats.length) {
    console.log('Kept working on purpose (lists named them):', [...droppedAds, ...droppedThreats].join(', '));
  }
  const missed = MUST_BLOCK.filter((d) => !covers(ads, d));
  if (missed.length) throw new Error('Ad domains not blocked: ' + missed.join(', '));

  // Ads never block a page you open yourself (only what it loads); scam sites are blocked outright.
  const rules = {
    ads: domainRules(ads, 1, NOT_MAIN_FRAME),
    threats: domainRules(threats, 1, ALL_TYPES),
    paths: PATH_RULES.map((urlFilter, i) => ({
      id: i + 1,
      priority: 1,
      action: { type: 'block' },
      condition: { urlFilter, resourceTypes: NOT_MAIN_FRAME },
    })),
  };
  const stats = { ads: ads.size, threats: threats.size, built: new Date().toISOString().slice(0, 10) };

  const cleaner = fs.readFileSync(path.join(APP_ASSETS, 'cleaner.js'), 'utf8');
  fs.rmSync(DIST, { recursive: true, force: true });
  const version = JSON.parse(fs.readFileSync(path.join(SRC, 'manifest.json'), 'utf8')).version;
  for (const browser of ['chrome', 'firefox']) {
    const out = path.join(DIST, browser);
    copyDir(SRC, out);
    fs.writeFileSync(path.join(out, 'content', 'cleaner.js'), cleaner);
    fs.writeFileSync(path.join(out, 'manifest.json'), JSON.stringify(manifestFor(browser), null, 2));
    fs.mkdirSync(path.join(out, 'rules'), { recursive: true });
    for (const [id, list] of Object.entries(rules)) {
      fs.writeFileSync(path.join(out, 'rules', id + '.json'), JSON.stringify(list));
    }
    fs.writeFileSync(path.join(out, 'rules', 'stats.json'), JSON.stringify(stats));
    zip(out, path.join(DIST, `adochi-${browser}-${version}.zip`));
  }

  console.log(`\nBlocked: ${stats.ads.toLocaleString()} ad/tracker sites, ${stats.threats.toLocaleString()} scam sites`);
  console.log(`Rules: ${rules.ads.length} + ${rules.threats.length} + ${rules.paths.length}`);
  console.log('Output:', DIST);
}

main().catch((e) => {
  console.error('Build failed:', e.message);
  process.exit(1);
});
