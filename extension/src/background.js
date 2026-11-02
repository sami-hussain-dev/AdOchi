/*
 * AdOchi extension background. Blocking itself is done by the browser from the bundled rule lists
 * (declarativeNetRequest), so this only switches lists on and off, keeps counts, and shows the
 * warning page when a scam site is stopped.
 */
'use strict';

const api = globalThis.browser ?? globalThis.chrome;
const dnr = api.declarativeNetRequest;

const DEFAULTS = {
  enabled: true, // master switch
  ads: true, // ad & tracker lists
  threats: true, // scam, phishing & malware lists
  social: true, // page cleaner: sponsored posts and video ads on Facebook, YouTube...
  paused: [], // sites where the user turned AdOchi off
  total: 0, // everything blocked since install
};

const ALL_TYPES = [
  'main_frame', 'sub_frame', 'stylesheet', 'script', 'image', 'font', 'object',
  'xmlhttprequest', 'ping', 'csp_report', 'media', 'websocket', 'other',
];
const SITE_PRIORITY = 100;
const PROCEED_PRIORITY = 200;
const SCRIPT_CLEANER = 'adochi-cleaner';
const SCRIPT_BRIDGE = 'adochi-bridge';

async function settings() {
  return api.storage.local.get(DEFAULTS);
}

/** Brings rule lists, page scripts, pause rules and the toolbar badge in line with the settings. */
async function apply() {
  const s = await settings();

  // Page scripts first: they're quick, while the browser may still be indexing the big lists.
  await registerScripts(s);

  const want = { ads: s.enabled && s.ads, paths: s.enabled && s.ads, threats: s.enabled && s.threats };
  await dnr.updateEnabledRulesets({
    enableRulesetIds: Object.keys(want).filter((id) => want[id]),
    disableRulesetIds: Object.keys(want).filter((id) => !want[id]),
  });

  const old = await dnr.getDynamicRules();
  await dnr.updateDynamicRules({
    removeRuleIds: old.map((r) => r.id),
    addRules: s.paused.map((host, i) => ({
      id: i + 1,
      priority: SITE_PRIORITY,
      action: { type: 'allowAllRequests' },
      condition: { requestDomains: [host], resourceTypes: ['main_frame', 'sub_frame'] },
    })),
  });

  try {
    await dnr.setExtensionActionOptions({ displayActionCountAsBadgeText: s.enabled });
  } catch (e) {
    // Older browsers: no live count on the icon.
  }
  await api.action.setBadgeBackgroundColor({ color: '#0FA37F' });
  await api.action.setBadgeText({ text: s.enabled ? '' : 'off' });
  await api.action.setIcon({
    path: s.enabled
      ? { 16: 'icons/icon-16.png', 32: 'icons/icon-32.png' }
      : { 16: 'icons/icon-off-16.png', 32: 'icons/icon-off-32.png' },
  });
}

/** Page scripts are registered here, not in the manifest, so paused sites can be left out. */
async function registerScripts(s) {
  const registered = await api.scripting.getRegisteredContentScripts();
  if (registered.length) await api.scripting.unregisterContentScripts({ ids: registered.map((r) => r.id) });
  if (!s.enabled) return;

  const excludeMatches = s.paused.flatMap((host) => [`*://${host}/*`, `*://*.${host}/*`]);
  const matches = ['http://*/*', 'https://*/*'];
  const scripts = [];
  if (s.ads) {
    scripts.push({
      id: SCRIPT_BRIDGE,
      js: ['content/bridge.js'],
      css: ['content/generic.css'],
      matches,
      excludeMatches,
      runAt: 'document_start',
      allFrames: true,
      persistAcrossSessions: true,
    });
  }
  if (s.ads && s.social) {
    scripts.push({
      id: SCRIPT_CLEANER,
      js: ['content/cleaner.js'],
      matches,
      excludeMatches,
      runAt: 'document_start',
      // Must share the page's JavaScript to strip ad data before the video player reads it.
      world: 'MAIN',
      persistAcrossSessions: true,
    });
  }
  if (scripts.length) await api.scripting.registerContentScripts(scripts);
}

// ---- Counts ------------------------------------------------------------------------------------

const tabKey = (tabId) => `tab:${tabId}`;

async function tabInfo(tabId) {
  const key = tabKey(tabId);
  const stored = await api.storage.session.get(key);
  return stored[key] ?? { since: Date.now(), hidden: 0, lastSafe: null };
}

async function saveTabInfo(tabId, info) {
  await api.storage.session.set({ [tabKey(tabId)]: info });
}

async function blockedSince(tabId, since) {
  try {
    const { rulesMatchedInfo } = await dnr.getMatchedRules({ tabId, minTimeStamp: since });
    return rulesMatchedInfo.length;
  } catch (e) {
    return 0;
  }
}

/** Adds what was blocked on the page being left to the all-time total. */
async function closePage(tabId) {
  const info = await tabInfo(tabId);
  const count = (await blockedSince(tabId, info.since)) + info.hidden;
  if (count > 0) {
    const { total } = await settings();
    await api.storage.local.set({ total: total + count });
  }
  return info;
}

api.webNavigation.onCommitted.addListener(async (details) => {
  if (details.frameId !== 0) return;
  const info = await closePage(details.tabId);
  const safe = /^https?:/.test(details.url) ? details.url : info.lastSafe;
  await saveTabInfo(details.tabId, { since: Date.now(), hidden: 0, lastSafe: safe });
});

api.tabs.onRemoved.addListener(async (tabId) => {
  await closePage(tabId);
  await api.storage.session.remove(tabKey(tabId));
});

// ---- Scam sites --------------------------------------------------------------------------------

/** A page blocked by the scam lists gets AdOchi's explanation instead of the browser's error page. */
api.webNavigation.onErrorOccurred.addListener(async (details) => {
  if (details.frameId !== 0 || !/^https?:/.test(details.url)) return;
  // Chrome names the reason; Firefox reports a number, so there the matched rule decides alone.
  if (details.error.startsWith('net::') && !details.error.includes('BLOCKED_BY_CLIENT')) return;
  const { rulesMatchedInfo } = await dnr.getMatchedRules({ tabId: details.tabId, minTimeStamp: Date.now() - 15000 });
  const threat = rulesMatchedInfo.some((m) => m.rule.rulesetId === 'threats');
  if (!threat) return;
  const warning = api.runtime.getURL('warning.html') + '?url=' + encodeURIComponent(details.url);
  await api.tabs.update(details.tabId, { url: warning });
});

/** "Open anyway": lets this one site through until the browser restarts. */
async function proceed(tabId, url) {
  const host = new URL(url).hostname;
  const rules = await dnr.getSessionRules();
  const id = rules.reduce((max, r) => Math.max(max, r.id), 0) + 1;
  await dnr.updateSessionRules({
    addRules: [{
      id,
      priority: PROCEED_PRIORITY,
      action: { type: 'allow' },
      condition: { requestDomains: [host], resourceTypes: ALL_TYPES },
    }],
  });
  await api.tabs.update(tabId, { url });
}

async function goBack(tabId) {
  const info = await tabInfo(tabId);
  if (info.lastSafe) await api.tabs.update(tabId, { url: info.lastSafe });
  else await api.tabs.remove(tabId);
}

// ---- Popup -------------------------------------------------------------------------------------

/** The site key used for pausing: the host without "www.", so it covers the whole site. */
function siteOf(url) {
  try {
    const u = new URL(url);
    if (!/^https?:$/.test(u.protocol)) return null;
    return u.hostname.replace(/^www\./, '');
  } catch (e) {
    return null;
  }
}

async function popupState(tabId, url) {
  const s = await settings();
  const site = siteOf(url);
  let page = 0;
  if (tabId != null) {
    const info = await tabInfo(tabId);
    page = (await blockedSince(tabId, info.since)) + info.hidden;
  }
  return {
    enabled: s.enabled,
    ads: s.ads,
    threats: s.threats,
    social: s.social,
    site,
    sitePaused: site != null && s.paused.includes(site),
    page,
    total: s.total + page,
  };
}

async function setOption(name, value) {
  if (!(name in DEFAULTS) || typeof value !== 'boolean') return;
  await api.storage.local.set({ [name]: value });
  await apply();
}

async function setSitePaused(site, paused) {
  const { paused: list } = await settings();
  const next = paused ? [...new Set([...list, site])] : list.filter((h) => h !== site);
  await api.storage.local.set({ paused: next });
  await apply();
}

api.runtime.onMessage.addListener((msg, sender, reply) => {
  const tabId = sender.tab?.id;
  // Settings may only change from the popup, and only the warning page may let a scam site through.
  const fromPopup = sender.url?.startsWith(api.runtime.getURL('popup.html'));
  const fromWarning = sender.url?.startsWith(api.runtime.getURL('warning.html'));
  const work = (async () => {
    switch (msg?.type) {
      case 'hidden': {
        if (tabId == null || !(msg.count > 0)) return null;
        const info = await tabInfo(tabId);
        info.hidden += Math.min(msg.count, 500);
        await saveTabInfo(tabId, info);
        return null;
      }
      case 'state':
        if (!fromPopup) return null;
        return popupState(msg.tabId, msg.url);
      case 'option':
        if (!fromPopup) return null;
        await setOption(msg.name, msg.value);
        return popupState(msg.tabId, msg.url);
      case 'site':
        if (!fromPopup) return null;
        if (typeof msg.site === 'string' && msg.site) await setSitePaused(msg.site, !!msg.paused);
        return popupState(msg.tabId, msg.url);
      case 'proceed':
        if (fromWarning && tabId != null && /^https?:/.test(msg.url)) await proceed(tabId, msg.url);
        return null;
      case 'back':
        if (fromWarning && tabId != null) await goBack(tabId);
        return null;
      default:
        return null;
    }
  })();
  work.then(reply, (e) => reply({ error: String(e) }));
  return true; // Answer asynchronously.
});

api.runtime.onInstalled.addListener(apply);
api.runtime.onStartup.addListener(apply);
