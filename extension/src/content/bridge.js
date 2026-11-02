// Runs beside the page cleaner and passes its "ad hidden" signals to the background, in batches.
(function () {
  'use strict';
  const api = globalThis.browser ?? globalThis.chrome;
  let pending = 0;
  let timer = null;

  function flush() {
    timer = null;
    const count = pending;
    pending = 0;
    try {
      api.runtime.sendMessage({ type: 'hidden', count });
    } catch (e) {
      // Extension was updated or reloaded; nothing to report to.
    }
  }

  document.addEventListener('adochi:hidden', () => {
    pending++;
    if (!timer) timer = setTimeout(flush, 1000);
  });
})();
