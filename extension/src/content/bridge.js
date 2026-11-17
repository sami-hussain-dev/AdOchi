// Runs beside the page cleaner and passes its "ad hidden" signals to the background, in batches.
(function () {
  'use strict';
  const api = globalThis.browser ?? globalThis.chrome;
  let pending = 0;
  let seconds = 0;
  let timer = null;

  function flush() {
    timer = null;
    const count = pending;
    const saved = seconds;
    pending = 0;
    seconds = 0;
    try {
      api.runtime.sendMessage({ type: 'hidden', count, seconds: saved });
    } catch (e) {
      // Extension was updated or reloaded; nothing to report to.
    }
  }

  // YouTube refused to play the video; the background decides what to do (once per page).
  let blockedSent = false;
  document.addEventListener('adochi:ytblocked', () => {
    if (blockedSent || window.top !== window) return;
    blockedSent = true;
    try {
      api.runtime.sendMessage({ type: 'ytblocked' });
    } catch (e) {
      // Extension was updated or reloaded.
    }
  });

  document.addEventListener('adochi:hidden', (e) => {
    pending++;
    // Seconds of video ad skipped (a number, so it crosses from the page's world unchanged).
    if (typeof e.detail === 'number' && e.detail > 0 && e.detail < 3600) seconds += e.detail;
    if (!timer) timer = setTimeout(flush, 1000);
  });
})();
