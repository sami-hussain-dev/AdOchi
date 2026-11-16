/*
 * AdOchi app only (not the browser extension). Background play for the Clean Browser:
 *  - YouTube stops a video as soon as the page is hidden (app in the background, screen off).
 *    While the user has background play on, the page is told it is still visible.
 *  - Play/pause of the page's video is passed to the app, which shows a notification with
 *    play/pause controls and keeps the app alive while something plays.
 */
(function () {
  'use strict';
  if (window.__adochiMedia) return;
  window.__adochiMedia = true;

  function bridge(name) {
    try {
      if (typeof AdOchiBridge === 'object' && typeof AdOchiBridge[name] === 'function') return AdOchiBridge;
    } catch (e) { /* no bridge */ }
    return null;
  }

  function wanted() {
    var b = bridge('backgroundPlay');
    try { return !!(b && b.backgroundPlay()); } catch (e) { return false; }
  }

  var host = location.hostname;
  var youtube = host === 'youtube.com' || /\.youtube\.com$/.test(host);

  if (youtube && wanted()) {
    try {
      Object.defineProperty(Document.prototype, 'hidden', { configurable: true, get: function () { return false; } });
      Object.defineProperty(Document.prototype, 'visibilityState', { configurable: true, get: function () { return 'visible'; } });
      Object.defineProperty(Document.prototype, 'webkitHidden', { configurable: true, get: function () { return false; } });
      Object.defineProperty(Document.prototype, 'webkitVisibilityState', { configurable: true, get: function () { return 'visible'; } });
    } catch (e) { /* already locked */ }
    ['visibilitychange', 'webkitvisibilitychange'].forEach(function (name) {
      window.addEventListener(name, function (e) { e.stopImmediatePropagation(); }, true);
      document.addEventListener(name, function (e) { e.stopImmediatePropagation(); }, true);
    });
  }

  var last = null;
  function send(playing) {
    var b = bridge('onMediaState');
    if (!b || playing === last) return;
    last = playing;
    var title = (document.title || '').replace(/\s*-\s*YouTube\s*$/, '');
    try { b.onMediaState(playing, title.slice(0, 120)); } catch (e) { /* app gone */ }
  }

  function isAd() {
    return !!document.querySelector('.ad-showing, .ad-interrupting');
  }

  document.addEventListener('playing', function (e) {
    if (e.target && e.target.tagName === 'VIDEO' && !isAd()) send(true);
  }, true);
  ['pause', 'ended', 'emptied'].forEach(function (name) {
    document.addEventListener(name, function (e) {
      if (e.target && e.target.tagName === 'VIDEO' && !isAd()) send(false);
    }, true);
  });
  window.addEventListener('pagehide', function () { send(false); });
})();
