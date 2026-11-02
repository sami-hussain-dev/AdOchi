'use strict';

const api = globalThis.browser ?? globalThis.chrome;
const target = new URLSearchParams(location.search).get('url') || '';

let host = '';
try {
  host = new URL(target).hostname;
} catch (e) {
  // Shown without a name.
}
document.getElementById('host').textContent = host || 'Unknown site';
if (!/^https?:/.test(target)) document.getElementById('proceed').hidden = true;

document.getElementById('back').addEventListener('click', () => {
  api.runtime.sendMessage({ type: 'back' });
});

document.getElementById('proceed').addEventListener('click', () => {
  if (confirm(`Open ${host}? AdOchi will allow it until you restart the browser.`)) {
    api.runtime.sendMessage({ type: 'proceed', url: target });
  }
});
