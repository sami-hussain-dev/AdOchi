'use strict';

const api = globalThis.browser ?? globalThis.chrome;
const $ = (id) => document.getElementById(id);

let tab = { id: null, url: '' };
let site = null;

function formatCount(n) {
  if (n < 1000) return String(n);
  if (n < 1e6) return (n / 1000).toFixed(n < 1e4 ? 1 : 0).replace(/\.0$/, '') + 'K';
  return (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
}

async function currentTab() {
  // ?tabId= lets the automated test open this page as a normal tab.
  const forced = Number(new URLSearchParams(location.search).get('tabId'));
  if (forced) return api.tabs.get(forced);
  const [active] = await api.tabs.query({ active: true, currentWindow: true });
  return active;
}

function send(message) {
  return api.runtime.sendMessage({ ...message, tabId: tab.id, url: tab.url });
}

function render(state) {
  if (!state || state.error) return;
  site = state.site;
  document.body.classList.toggle('off', !state.enabled);
  $('enabled').checked = state.enabled;
  $('ads').checked = state.ads;
  $('threats').checked = state.threats;
  $('social').checked = state.social;
  $('social').disabled = !state.ads;

  const hero = $('hero');
  const activeHere = state.enabled && state.site && !state.sitePaused;
  hero.classList.toggle('off', !activeHere);
  if (!state.enabled) {
    $('page').textContent = 'Protection is off';
    $('caption').textContent = 'Turn it on with the switch above';
  } else if (!state.site) {
    $('page').textContent = 'Nothing to block';
    $('caption').textContent = 'AdOchi works on websites';
  } else if (state.sitePaused) {
    $('page').textContent = 'Paused here';
    $('caption').textContent = 'Ads are allowed on this site';
  } else {
    $('page').textContent = formatCount(state.page);
    $('caption').textContent = state.page === 1 ? 'ad or tracker blocked on this page' : 'ads & trackers blocked on this page';
  }
  $('total').textContent = state.total > 0 ? `${formatCount(state.total)} blocked since you installed AdOchi` : '';

  const card = $('siteCard');
  card.classList.toggle('na', !state.site);
  card.classList.toggle('paused', !!state.sitePaused);
  $('site').textContent = state.site || 'This page';
  $('siteSub').textContent = !state.site
    ? 'Browser pages can\'t be filtered'
    : state.sitePaused ? 'Paused: tap to protect this site again' : 'Protection is on for this site';
  $('siteOn').checked = !!state.site && !state.sitePaused;
  $('siteOn').disabled = !state.site;
}

async function showListSizes() {
  try {
    const stats = await (await fetch(api.runtime.getURL('rules/stats.json'))).json();
    $('adsSub').textContent = `${formatCount(stats.ads)} ad & tracker sites`;
    $('threatsSub').textContent = `${formatCount(stats.threats)} scam & malware sites`;
  } catch (e) {
    // Keep the plain descriptions.
  }
}

async function main() {
  $('version').textContent = 'v' + api.runtime.getManifest().version;
  const t = await currentTab();
  if (t) tab = { id: t.id, url: t.url || '' };
  render(await send({ type: 'state' }));
  showListSizes();

  for (const name of ['enabled', 'ads', 'threats', 'social']) {
    $(name).addEventListener('change', async (e) => {
      render(await send({ type: 'option', name, value: e.target.checked }));
    });
  }
  $('siteOn').addEventListener('change', async (e) => {
    const state = await send({ type: 'site', site, paused: !e.target.checked });
    render(state);
    if (tab.id != null) api.tabs.reload(tab.id); // Apply to the page right away.
  });
}

main();
