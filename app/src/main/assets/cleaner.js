/*
 * AdOchi page cleaner. Runs at document start in the app's Clean Browser and in the browser extension.
 * Removes sponsored posts (Facebook, Instagram, X, TikTok), YouTube video ads and common ad slots.
 */
(function () {
  'use strict';
  if (window.__adochiCleaner) return;
  window.__adochiCleaner = true;

  var hostname = location.hostname;
  function on(domain) {
    return hostname === domain || hostname.slice(-(domain.length + 1)) === '.' + domain;
  }

  /**
   * Counts one removed ad. `seconds` is the ad time saved (video ads only). The app exposes
   * AdOchiBridge; the browser extension listens for the event instead.
   */
  function report(seconds) {
    var saved = seconds > 0 && seconds < 3600 ? Math.round(seconds) : 0;
    try {
      AdOchiBridge.onAdHidden();
      if (saved) AdOchiBridge.onTimeSaved(saved);
      return;
    } catch (e) { /* not in the app */ }
    try { document.dispatchEvent(new CustomEvent('adochi:hidden', { detail: saved })); } catch (e) { /* very old engine */ }
  }

  function hide(el) {
    if (!el || el.__adochiHidden || el === document.body || el === document.documentElement) return;
    el.__adochiHidden = true;
    el.style.setProperty('display', 'none', 'important');
    report();
  }

  function addStyle(css) {
    var style = document.createElement('style');
    style.textContent = css;
    (function attach() {
      var parent = document.head || document.documentElement;
      if (parent) parent.appendChild(style); else setTimeout(attach, 10);
    })();
  }

  function hideRule(selectors) {
    return selectors.join(',\n') + ' { display: none !important; }\n';
  }

  // A wrapper that still reports "[native code]", so a page check sees nothing changed.
  function wrap(owner, name, apply) {
    var original = owner && owner[name];
    if (typeof original !== 'function' || typeof Proxy !== 'function') return;
    try {
      owner[name] = new Proxy(original, { apply: function (target, self, args) { return apply(target, self, args); } });
    } catch (e) { /* frozen */ }
  }

  /** Calls obj.name(...) when the page has it; undefined when it is missing or throws. */
  function call(obj, name) {
    try {
      return obj && typeof obj[name] === 'function' ? obj[name].apply(obj, [].slice.call(arguments, 2)) : undefined;
    } catch (e) {
      return undefined;
    }
  }

  function own(obj, key) {
    return Object.prototype.hasOwnProperty.call(obj, key);
  }

  // Common ad slots on ordinary websites.
  addStyle(hideRule([
    'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads_iframe"]', '[id^="div-gpt-ad"]',
    'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication.com"]', 'amp-ad', 'amp-embed[type="taboola"]',
    '[data-ad-slot]', 'div[id^="taboola-"]', '.OUTBRAIN', '[data-widget-id^="outbrain"]'
  ]));

  var SPONSORED = [
    'Sponsored', 'Sponsorisé', 'Sponsorisée', 'Gesponsert', 'Patrocinado', 'Patrocinada', 'Sponsorizzato',
    'Gesponsord', 'Sponsorowane', 'Реклама', 'Sponsorlu', 'Bersponsor', 'Disponsori', 'Được tài trợ',
    'ได้รับการสนับสนุน', 'प्रायोजित', 'স্পনসর্ড', 'سپانسر شدہ', 'سپانسرڈ', 'مُموَّل', 'ممول', '赞助内容',
    '贊助', '広告', '광고', 'Sponsoreret', 'Sponset', 'Sponsrad', 'Χορηγούμενη', 'Sponzorováno', 'Paid partnership'
  ];

  var FACEBOOK_VIDEO = {
    // "Ad starting in 5", "Ads starting in 5", "Ad break in 5", "Ad coming up in 5", Urdu/Hindi/Arabic.
    countdown: /\bads?\b(?:\s+break)?\s+(?:starting|starts|will\s+(?:start|play|begin)|begins?|coming(?:\s+up)?)?\s*in\s*(\d{1,2})|(?:اشتہار|विज्ञापन|إعلان)[^|]{0,30}?(\d{1,2})/i,
    // "Ad · 0:15", "Ad 1 of 2", "Your video will resume after the ad", "Video will play after ad", Urdu/Hindi/Arabic.
    playing: /(?:^|\|\s*)ads?\s*[·•:]\s*\d|\bad\s+\d+\s+of\s+\d+|your\s+video\s+will\s+(?:resume|continue)|video\s+will\s+resume|video\s+will\s+(?:play|continue|start)\s+after|(?:resumes?|continues?|plays?)\s+after\s+(?:this\s+|the\s+)?ad\b|(?:^|\|\s*)(?:اشتہار|विज्ञापन|إعلان)\s*[·•:]?\s*\d/i,
    skip: /^(skip|skip ad|skip ads|skip video ad|اشتہار چھوڑیں)$/i
  };

  /**
   * Hides feed items that carry a sponsored label. The label is found by its text or aria-label,
   * then the surrounding post is located by known container selectors or by layout.
   */
  function feedCleaner(cfg) {
    var words = {};
    cfg.words.forEach(function (w) { words[w.toLowerCase()] = true; });

    function isLabel(text) {
      if (!text) return false;
      text = clean(text);
      if (!(text.length > 0 && text.length < 60)) return false;
      var lower = text.toLowerCase();
      if (words[lower] === true) return true;
      // "Sponsored · Paid for by …", "Sponsored · 🌐": the label followed by a separator.
      var cut = lower.search(/\s*[·•|]/);
      return cut > 0 && words[lower.slice(0, cut)] === true;
    }

    // Labels are often written as "Sponsored · 🌐" or padded with invisible characters.
    function clean(text) {
      return text.replace(/[​-‏⁠﻿]/g, '').replace(/[\s·•|·]+$/, '').replace(/^[\s·•|]+/, '').trim();
    }

    var longWords = cfg.words.filter(function (w) { return w.length >= 5; }).map(function (w) { return w.toLowerCase(); });

    function sortedLetters(text) {
      return text.toLowerCase().replace(/\s+/g, '').split('').sort().join('');
    }
    var anagrams = {};
    longWords.forEach(function (w) { anagrams[sortedLetters(w)] = true; });

    function hasInOrder(word, text) {
      var j = 0;
      for (var i = 0; i < text.length && j < word.length; i++) if (text[i] === word[j]) j++;
      return j === word.length;
    }

    function hasAllLetters(word, text) {
      var counts = {};
      for (var i = 0; i < text.length; i++) counts[text[i]] = (counts[text[i]] || 0) + 1;
      for (var j = 0; j < word.length; j++) {
        if (!counts[word[j]]) return false;
        counts[word[j]]--;
      }
      return true;
    }

    function isHidden(el) {
      var s = getComputedStyle(el);
      if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) === 0) return true;
      if (parseFloat(s.fontSize) === 0) return true;
      if (s.position === 'absolute' || s.position === 'fixed') {
        // Decoy letters are pushed out of view or clipped away.
        if (Math.abs(parseFloat(s.top)) > 50 || Math.abs(parseFloat(s.left)) > 50) return true;
        if (s.clip && s.clip !== 'auto') return true;
      }
      var r = el.getBoundingClientRect();
      return r.width === 0 || r.height === 0;
    }

    /** The text a person actually sees, skipping the hidden decoy letters some sites mix into labels. */
    function visibleText(el) {
      var out = '';
      var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {
        var hidden = false;
        for (var p = node.parentElement; p && p !== el.parentElement; p = p.parentElement) {
          if (isHidden(p)) { hidden = true; break; }
        }
        if (!hidden) out += node.nodeValue;
      }
      return out;
    }

    /** A label split into many single-letter pieces, possibly shuffled or padded with decoys. */
    function isScrambledLabel(el) {
      if (el.children.length < 3) return false;
      var raw = (el.textContent || '').replace(/\s+/g, '').toLowerCase();
      if (raw.length < 5 || raw.length > 80) return false;
      var candidate = longWords.some(function (w) {
        return raw.length <= w.length * 4 && (hasInOrder(w, raw) || hasAllLetters(w, raw));
      });
      if (!candidate) return false;
      var seen = clean(visibleText(el));
      return isLabel(seen) || (seen.length >= 6 && anagrams[sortedLetters(seen)] === true);
    }

    function feedItemByLayout(el) {
      // Climb to the element that sits in a list of siblings and looks like a whole post.
      var node = el;
      var viewport = window.innerHeight || 800;
      while (node && node.parentElement && node.parentElement !== document.body) {
        var parent = node.parentElement;
        var h = node.offsetHeight;
        if (parent.children.length >= 3 && h > 120 && h < viewport * 4) return node;
        node = parent;
      }
      return null;
    }

    function containerOf(el) {
      if (!el) return null;
      for (var i = 0; i < cfg.containers.length; i++) {
        var c = el.closest(cfg.containers[i]);
        if (c) return c;
      }
      return cfg.layoutFallback ? feedItemByLayout(el) : null;
    }

    function scan(root) {
      if (!root || root.nodeType !== 1 || root.__adochiHidden) return;
      cfg.selectors.forEach(function (sel) {
        if (root.matches && root.matches(sel)) hide(containerOf(root));
        root.querySelectorAll(sel).forEach(function (el) { hide(containerOf(el)); });
      });
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {
        if (node.nodeValue.length < 60 && isLabel(node.nodeValue)) hide(containerOf(node.parentElement));
      }
      root.querySelectorAll('[aria-label]').forEach(function (el) {
        if (isLabel(el.getAttribute('aria-label'))) hide(containerOf(el));
      });
      root.querySelectorAll('[aria-labelledby]').forEach(function (el) {
        var ref = document.getElementById(el.getAttribute('aria-labelledby'));
        if (ref && isLabel(ref.textContent)) hide(containerOf(el));
      });
      if (cfg.scrambled) {
        root.querySelectorAll(cfg.scrambled).forEach(function (el) {
          if (el.__adochiChecked) return;
          el.__adochiChecked = true;
          if (isScrambledLabel(el)) hide(containerOf(el));
        });
      }
    }

    var pending = [];
    var timer = null;
    function flush() {
      timer = null;
      var batch = pending;
      pending = [];
      batch.forEach(scan);
    }
    function queue(node) {
      pending.push(node);
      if (!timer) timer = setTimeout(flush, 150);
    }

    new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        m.addedNodes.forEach(function (n) {
          if (n.nodeType === 1) queue(n);
          else if (n.nodeType === 3 && n.parentElement) queue(n.parentElement);
        });
        if (m.type === 'characterData' && m.target.parentElement) queue(m.target.parentElement);
      });
    }).observe(document, { childList: true, subtree: true, characterData: true });

    document.addEventListener('DOMContentLoaded', function () { queue(document.body); });
  }

  /**
   * In-stream video ads. Before a break the player shows "Ad starting in 5"; seeking past that point
   * skips the break, so we jump ahead by the countdown. If an ad plays anyway it is muted, sped up and skipped.
   */
  function videoAdSkipper(cfg) {
    var state = new WeakMap();

    function playerOf(video) {
      var vr = video.getBoundingClientRect();
      var best = video;
      var node = video;
      for (var i = 0; i < 8 && node.parentElement && node.parentElement !== document.body; i++) {
        node = node.parentElement;
        var r = node.getBoundingClientRect();
        if (r.height > vr.height * 1.6 + 80 || r.width > vr.width * 1.3 + 40) break;
        best = node;
      }
      return best;
    }

    function overlayText(player) {
      var parts = [];
      var walker = document.createTreeWalker(player, NodeFilter.SHOW_TEXT, null);
      var node;
      var count = 0;
      while ((node = walker.nextNode()) && count++ < 300) {
        var t = node.nodeValue.trim();
        if (t && t.length < 80) parts.push(t);
      }
      player.querySelectorAll('[aria-label]').forEach(function (el) {
        var t = el.getAttribute('aria-label');
        if (t && t.length < 80) parts.push(t);
      });
      return parts.join(' | ');
    }

    function clickSkip(player) {
      var buttons = player.querySelectorAll('[role="button"], button');
      for (var i = 0; i < buttons.length; i++) {
        var label = (buttons[i].getAttribute('aria-label') || buttons[i].textContent || '').trim();
        if (cfg.skip.test(label)) { buttons[i].click(); return; }
      }
    }

    function check(video) {
      var rect = video.getBoundingClientRect();
      if (rect.width < 80 || rect.height < 60) return;
      var s = state.get(video);
      if (!s) {
        s = { inAd: false, jumps: 0, last: 0, muted: false, rate: 1 };
        state.set(video, s);
      }
      var player = playerOf(video);
      var text = overlayText(player);

      if (cfg.playing.test(text)) {
        if (!s.inAd) {
          s.inAd = true;
          s.muted = video.muted;
          s.rate = video.playbackRate || 1;
          report(isFinite(video.duration) && video.duration < 120 ? video.duration - video.currentTime : 15);
        }
        video.muted = true;
        try { video.playbackRate = 16; } catch (e) { /* rate not allowed */ }
        // Only short clips are ads on their own; never jump to the end of the real video.
        if (isFinite(video.duration) && video.duration > 0 && video.duration < 120) {
          try { video.currentTime = Math.max(video.duration - 0.1, 0); } catch (e) { /* not seekable yet */ }
        }
        clickSkip(player);
        return;
      }
      if (s.inAd) {
        s.inAd = false;
        video.muted = s.muted;
        video.playbackRate = s.rate;
      }

      var countdown = cfg.countdown.exec(text);
      if (!countdown) {
        s.jumps = 0;
        return;
      }
      var now = Date.now();
      if (video.paused || now - s.last < 1500 || s.jumps >= 4) return;
      var seconds = parseInt(countdown[1] || countdown[2] || '5', 10);
      if (!(seconds > 0 && seconds <= 30)) seconds = 5;
      var target = video.currentTime + seconds + 1.5;
      if (isFinite(video.duration) && video.duration > 0) target = Math.min(target, video.duration - 0.5);
      if (target <= video.currentTime) return;
      s.last = now;
      s.jumps++;
      try {
        video.currentTime = target;
        report(15); // A Facebook ad break is about 15 seconds.
      } catch (e) { /* not seekable */ }
    }

    setInterval(function () {
      document.querySelectorAll('video').forEach(check);
    }, 400);
  }

  /** Empties ad-break lists in data the page parses, only when the raw text mentions them. */
  function pruneJsonKeys(pattern, keys) {
    var wanted = {};
    keys.forEach(function (k) { wanted[k] = true; });
    function walk(obj, depth) {
      if (!obj || typeof obj !== 'object' || depth > 40) return;
      if (Array.isArray(obj)) {
        for (var i = 0; i < obj.length; i++) walk(obj[i], depth + 1);
        return;
      }
      for (var k in obj) {
        if (!Object.prototype.hasOwnProperty.call(obj, k)) continue;
        if (wanted[k] === true && Array.isArray(obj[k])) {
          if (obj[k].length) report();
          obj[k] = [];
        } else {
          walk(obj[k], depth + 1);
        }
      }
    }
    var nativeParse = JSON.parse;
    JSON.parse = function (text) {
      var result = nativeParse.apply(this, arguments);
      if (typeof text === 'string' && pattern.test(text)) {
        try { walk(result, 0); } catch (e) { /* leave data as is */ }
      }
      return result;
    };
  }

  /**
   * YouTube, in four layers:
   *  1. Ask for the video without ads. The player asks YouTube's server for every video; with the
   *     "params" below the server itself answers without ads (YouTube uses them for some of its own
   *     players). Nothing is deleted or blocked on the page, so there is nothing to detect and the
   *     video starts at once. The first video of a page arrives with the page, so when it carries ads
   *     the player is asked to load it again, which goes through this same ad-free request.
   *  2. YouTube's own ad-blocker check and its artificial start delay are switched off.
   *  3. Any ad that still comes is muted, hidden and played at 16x; a long or stuck one is moved to its end.
   *  4. The "ad blockers are not allowed" message is taken out of the data and closed.
   * Deleting the ads from the player's data (what blockers used to do) is not done: YouTube answers
   * that with a black screen for the length of the ad.
   */
  function youtube() {
    var ENFORCEMENT = /enforcementMessageViewModel/;
    var REEL_AD = /"isAd"\s*:\s*true/;
    var fnText = Function.prototype.toString;
    var nativeParse = JSON.parse;
    var nativeStringify = JSON.stringify;

    // ---- 1. Ad-free request ----------------------------------------------------------------------

    var NO_AD_PARAMS = 'oAEBogYBLg';
    var MEMORY_KEY = 'adochi.youtube';
    var REST_HOURS = 6;
    var askedAdFree = {}; // video id -> asked for without ads
    var askNormally = {}; // video id -> YouTube refused the ad-free request, so it is asked for the normal way
    var answerChecked = '';

    function memory() {
      try { return nativeParse(localStorage.getItem(MEMORY_KEY) || '{}') || {}; } catch (e) { return {}; }
    }

    function remember(m) {
      try { localStorage.setItem(MEMORY_KEY, nativeStringify(m)); } catch (e) { /* storage blocked */ }
    }

    /** False for a while after YouTube refused ad-free requests twice in a row. */
    function adFreeAllowed() {
      return !(memory().restUntil > Date.now());
    }

    /** The player's request for a video on youtube.com or m.youtube.com (not embeds, not playlists with own params). */
    function isPlayerRequest(body) {
      return !!body && typeof body === 'object' && !Array.isArray(body) && !!body.attestationRequest &&
        typeof body.videoId === 'string' && !body.params && !!body.context && !!body.context.client &&
        /^M?WEB$/.test(String(body.context.client.clientName || '')) &&
        !!body.playbackContext && !!body.playbackContext.contentPlaybackContext;
    }

    wrap(JSON, 'stringify', function (target, self, args) {
      var body = args[0];
      if (isPlayerRequest(body) && !askNormally[body.videoId] && adFreeAllowed()) {
        // A copy, so the page's own object stays as it was.
        var copy = {};
        for (var k in body) if (own(body, k)) copy[k] = body[k];
        copy.params = NO_AD_PARAMS;
        askedAdFree[body.videoId] = true;
        args = [copy].concat([].slice.call(args, 1));
      }
      return Reflect.apply(target, self, args);
    });

    function videoId(player) {
      var data = call(player, 'getVideoData');
      return (data && data.video_id) || new URLSearchParams(location.search).get('v') || '';
    }

    /** Where a link with "&t=1m30s" asks the video to start. */
    function startSeconds() {
      var t = new URLSearchParams(location.search).get('t') || '';
      var m = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$/.exec(t);
      return m ? (+m[1] || 0) * 3600 + (+m[2] || 0) * 60 + (+m[3] || 0) : 0;
    }

    /** YouTube refused the ad-free request: ask for that video again the normal way. */
    function checkAdFreeAnswer(player) {
      var id = videoId(player);
      if (!id || !askedAdFree[id] || askNormally[id]) return;
      var response = call(player, 'getPlayerResponse');
      var status = response && response.playabilityStatus && response.playabilityStatus.status;
      if (!status || (response.videoDetails && response.videoDetails.videoId !== id)) return;
      var m;
      if (status === 'OK') {
        if (answerChecked === id) return;
        answerChecked = id;
        m = memory();
        if (m.refused) {
          m.refused = 0;
          remember(m);
        }
        return;
      }
      // Age checks and sign-in walls are not about ads.
      if (status !== 'UNPLAYABLE' && status !== 'ERROR') return;
      askNormally[id] = true;
      m = memory();
      m.refused = (m.refused || 0) + 1;
      if (m.refused >= 2) {
        m.refused = 0;
        m.restUntil = Date.now() + REST_HOURS * 3600 * 1000;
      }
      remember(m);
      call(player, 'loadVideoById', id, startSeconds());
    }

    // The first video comes inside the page. Its ads are noted (never deleted), and the player then
    // loads the same video again through the ad-free request.
    var firstHasAds = false;
    var firstReloaded = false;
    try {
      var firstResponse;
      Object.defineProperty(window, 'ytInitialPlayerResponse', {
        configurable: true,
        enumerable: true,
        get: function () { return firstResponse; },
        set: function (value) {
          firstResponse = value;
          firstHasAds = !!value && typeof value === 'object' &&
            ['adPlacements', 'adSlots', 'playerAds'].some(function (k) { return Array.isArray(value[k]) && value[k].length > 0; });
        },
      });
    } catch (e) { /* already defined by the page */ }

    function reloadFirstVideo(player) {
      if (!firstHasAds || firstReloaded) return;
      // In a playlist the reload would lose the list, so there the ads are skipped instead.
      if (location.pathname !== '/watch' || /[?&]list=/.test(location.search) || !adFreeAllowed()) {
        firstReloaded = true;
        return;
      }
      var id = videoId(player);
      if (!id || typeof player.loadVideoById !== 'function') return;
      firstReloaded = true;
      call(player, 'loadVideoById', id, startSeconds());
      report(15);
    }

    // ---- 2. YouTube's ad-blocker check and start delay -------------------------------------------

    // The check hands what it found to a callback named onAbnormalityDetected; it gets one that does nothing.
    var ABNORMAL = /onAbnormalityDetected/;
    if (typeof Promise === 'function') {
      wrap(Promise.prototype, 'then', function (target, self, args) {
        if (typeof args[0] === 'function') {
          try { if (ABNORMAL.test(fnText.call(args[0]))) args[0] = function () {}; } catch (e) { /* unreadable */ }
        }
        return Reflect.apply(target, self, args);
      });
    }

    // The delay is a 17-second timer before the video may start; it is cut to a moment.
    wrap(window, 'setTimeout', function (target, self, args) {
      if (args[1] === 17000 && typeof args[0] === 'function') {
        try { if (fnText.call(args[0]).indexOf('[native code]') >= 0) args[1] = 17; } catch (e) { /* unreadable */ }
      }
      return Reflect.apply(target, self, args);
    });

    // ---- 4. "Ad blockers are not allowed" in the data; ad entries between Shorts ------------------

    function dropEnforcement(obj, depth) {
      if (!obj || typeof obj !== 'object' || depth > 12) return;
      var aux = obj.auxiliaryUi;
      if (aux && aux.messageRenderers && aux.messageRenderers.enforcementMessageViewModel) {
        delete aux.messageRenderers.enforcementMessageViewModel;
        report();
      }
      for (var k in obj) {
        if (own(obj, k) && obj[k] && typeof obj[k] === 'object') dropEnforcement(obj[k], depth + 1);
      }
    }

    function isReelAd(entry) {
      var endpoint = entry && entry.command && entry.command.reelWatchEndpoint;
      return !!(endpoint && endpoint.adClientParams && endpoint.adClientParams.isAd);
    }

    /** Takes the ad Shorts out of the list of Shorts to swipe through. */
    function dropReelAds(obj, depth) {
      if (!obj || typeof obj !== 'object' || depth > 8) return;
      if (Array.isArray(obj)) {
        for (var i = obj.length - 1; i >= 0; i--) {
          if (isReelAd(obj[i])) {
            obj.splice(i, 1);
            report();
          } else {
            dropReelAds(obj[i], depth + 1);
          }
        }
        return;
      }
      for (var k in obj) {
        if (own(obj, k) && obj[k] && typeof obj[k] === 'object') dropReelAds(obj[k], depth + 1);
      }
    }

    wrap(JSON, 'parse', function (target, self, args) {
      var result = Reflect.apply(target, self, args);
      if (typeof args[0] === 'string') {
        try {
          if (ENFORCEMENT.test(args[0])) dropEnforcement(result, 0);
          if (REEL_AD.test(args[0])) dropReelAds(result, 0);
        } catch (e) { /* leave data as is */ }
      }
      return result;
    });

    if (window.Response && Response.prototype) {
      wrap(Response.prototype, 'json', function (target, self, args) {
        var promise = Reflect.apply(target, self, args);
        if (!/\/youtubei\/v1\/reel\//.test((self && self.url) || '')) return promise;
        return promise.then(function (data) {
          try { dropReelAds(data, 0); } catch (e) { /* leave data as is */ }
          return data;
        });
      });
    }

    addStyle(hideRule([
      'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
      'ad-slot-renderer', 'ytm-ad-slot-renderer', 'ytm-mealbar-promo-renderer', 'ytm-statement-banner-renderer',
      'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-promoted-sparkles-web-renderer',
      'ytd-banner-promo-renderer', 'ytd-statement-banner-renderer', 'ytd-display-ad-renderer',
      'ytd-companion-slot-renderer', 'ytd-action-companion-ad-renderer', 'ytd-mealbar-promo-renderer',
      'ytd-player-legacy-desktop-watch-ads-renderer', 'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
      '#player-ads', '#masthead-ad', '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-ad-text-overlay',
      '.ytp-featured-product', '.ytp-suggested-action', 'tp-yt-paper-dialog ytd-enforcement-message-view-model'
    ]));
    // :has() in its own rule so older WebViews only drop this one.
    addStyle(hideRule([
      'ytm-rich-item-renderer:has(ad-slot-renderer)', 'ytm-item-section-renderer:has(ad-slot-renderer)',
      'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)', 'ytd-rich-section-renderer:has(ytd-statement-banner-renderer)',
      'tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)', 'ytd-reel-video-renderer:has(ytd-ad-slot-renderer)'
    ]));
    // While an ad runs, nothing of it is shown (it is over in a moment anyway).
    addStyle('.ad-showing video, .ad-interrupting video, .ad-showing .video-ads, .ad-showing .ytp-ad-player-overlay,' +
      ' .ad-showing .ytp-ad-player-overlay-layout { opacity: 0 !important; }\n');

    // ---- 3. Ads that still come -----------------------------------------------------------------

    // Ads are muted, hidden and played at the top speed the browser allows (16x in Chrome, Edge,
    // Firefox and the Android WebView), so a 15 s ad is over in about 1 s. An ad that would still
    // take long at that speed (YouTube also runs 1-3 minute ads), or that stops to load, is moved to
    // its end after a second.
    var AD_RATES = [16, 10, 8, 4, 2];
    var adRate = AD_RATES[0];
    var LONG_AT_SPEED = 3; // seconds
    var STUCK_MS = 1500;

    /*
     * While an ad runs, its speed and sound belong to AdOchi. YouTube's player puts them back to 1x
     * and sound on when an ad starts and between the ads of a break. Those writes are kept aside
     * instead of applied, and read back exactly as written, so the ad never drops to 1x or makes a
     * sound and the player only ever sees the values it set itself. The page's own getters and setters
     * are wrapped so that they still report "[native code]".
     */
    var held = typeof WeakMap === 'function' ? new WeakMap() : null;
    var media = window.HTMLMediaElement && HTMLMediaElement.prototype;
    var native = {};
    ['playbackRate', 'muted'].forEach(function (prop) {
      var d = media && Object.getOwnPropertyDescriptor(media, prop);
      if (!held || typeof Proxy !== 'function' || !d || !d.get || !d.set || !d.configurable) return;
      try {
        Object.defineProperty(media, prop, {
          configurable: true,
          enumerable: d.enumerable,
          get: new Proxy(d.get, { apply: function (target, self, args) {
            var state = held.get(self);
            return state ? state.want[prop] : Reflect.apply(target, self, args);
          } }),
          set: new Proxy(d.set, { apply: function (target, self, args) {
            var state = held.get(self);
            if (!state) return Reflect.apply(target, self, args);
            var value = prop === 'muted' ? !!args[0] : +args[0];
            if (prop === 'playbackRate' && !(value > 0 && isFinite(value))) return Reflect.apply(target, self, args);
            state.want[prop] = value;
            state.wrote[prop] = true;
          } })
        });
        native[prop] = d;
      } catch (e) { /* left as it is: the ad is then simply set again on every change */ }
    });

    function getNative(video, prop) {
      return native[prop] ? native[prop].get.call(video) : video[prop];
    }

    function setNative(video, prop, value) {
      if (native[prop]) native[prop].set.call(video, value); else video[prop] = value;
    }

    /** Top speed, or the next one down where the browser refuses it. */
    function speedUp(video) {
      for (;;) {
        try { setNative(video, 'playbackRate', adRate); return; } catch (e) { /* not allowed here */ }
        var i = AD_RATES.indexOf(adRate);
        if (i < 0 || i === AD_RATES.length - 1) return;
        adRate = AD_RATES[i + 1];
      }
    }

    var ad = null; // { video, saved, want, wrote, since, lastTime, lastMove, jumped } while an ad runs
    // The viewer's speed for the video. YouTube sets 1x as an ad starts, before AdOchi sees the ad.
    var videoRate = 1;
    var counted = '';
    var blockedSent = false;

    function startAd(video) {
      var rate = videoRate;
      var muted = getNative(video, 'muted');
      var now = Date.now();
      ad = {
        video: video, saved: { playbackRate: rate, muted: muted }, want: { playbackRate: rate, muted: muted }, wrote: {},
        since: now, lastTime: -1, lastMove: now, jumped: 0
      };
      if (held) held.set(video, ad);
      counted = '';
    }

    function endAd() {
      var state = ad;
      ad = null;
      if (held) held.delete(state.video);
      var video = state.video;
      // The sound the player last asked for. For speed, a speed it asked for itself (the viewer's
      // choice for the video), otherwise the speed from before the ad.
      var rate = state.wrote.playbackRate && state.want.playbackRate !== 1 ? state.want.playbackRate : state.saved.playbackRate;
      try { setNative(video, 'muted', state.want.muted); } catch (e) { /* gone */ }
      // Only undo our own speed, so a speed set some other way stays.
      try { if (getNative(video, 'playbackRate') === adRate) setNative(video, 'playbackRate', rate); } catch (e) { /* gone */ }
    }

    function playerNode() {
      return document.querySelector('#movie_player, .html5-video-player');
    }

    function adShowing(player) {
      return !!player && /(^|\s)(ad-showing|ad-interrupting)(\s|$)/.test(player.className);
    }

    /** "0:15" or "1:02:03" to seconds. */
    function seconds(text) {
      var m = /(\d+):(\d{2})(?::(\d{2}))?/.exec(text || '');
      if (!m) return 0;
      return m[3] ? (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]) : (+m[1]) * 60 + (+m[2]);
    }

    function remainingAdTime(player) {
      var el = player.querySelector('.ytp-ad-duration-remaining, .ytp-ad-preview-text, .ytp-ad-preview-text-modern, .ytp-ad-text');
      return el ? seconds(el.textContent) : 0;
    }

    /** Moves the running ad to its end, never the video itself. */
    function jumpToEnd(player, video) {
      ad.jumped = Date.now();
      var stats = call(player, 'getStatsForNerds');
      var length = +(((call(player, 'getPlayerResponse') || {}).videoDetails || {}).lengthSeconds) || 0;
      if (/^SSAP, AD/.test(String((stats && stats.debug_info) || ''))) {
        // The ad is stitched into the video's own stream: the player's seek moves past it.
        var progress = call(player, 'getProgressState');
        if (progress && progress.duration > 0 && (!length || progress.duration < length - 3)) call(player, 'seekTo', progress.duration);
        return;
      }
      // A separate ad clip. A clip as long as the video is the video, so it is left alone.
      if (!(isFinite(video.duration) && video.duration > 0) || (length && Math.abs(video.duration - length) < 3)) return;
      try { video.currentTime = Math.max(video.duration - 0.1, 0); } catch (e) { /* not seekable yet */ }
    }

    function tick() {
      var player = playerNode();
      var video = player ? player.querySelector('video') : document.querySelector('video');
      if (!adShowing(player) || !video) {
        if (ad) endAd();
        var now = video ? getNative(video, 'playbackRate') : 0;
        if (now > 0 && now !== adRate) videoRate = now;
        return;
      }
      if (ad && ad.video !== video) endAd();
      if (!ad) startAd(video);
      if (!getNative(video, 'muted')) setNative(video, 'muted', true);
      if (getNative(video, 'playbackRate') !== adRate) speedUp(video);
      if (!(isFinite(video.duration) && video.duration > 0)) return;
      // Each ad of a break is counted once, with the time it would have taken.
      var id = (video.currentSrc || video.src || '') + '|' + video.duration;
      if (counted !== id) {
        counted = id;
        var rest = remainingAdTime(player);
        // An ad stitched into the stream reports the whole video's length, so the ad's own timer is used.
        report(rest > 0 ? rest : Math.min(video.duration - video.currentTime, 180));
      }
      var time = Date.now();
      if (video.currentTime !== ad.lastTime) {
        ad.lastTime = video.currentTime;
        ad.lastMove = time;
      }
      var long = (video.duration - video.currentTime) / adRate > LONG_AT_SPEED;
      var stuck = time - ad.lastMove > STUCK_MS;
      if (time - ad.since > 1000 && time - ad.jumped > 1500 && (long || stuck)) jumpToEnd(player, video);
    }

    /** "Ad blockers are not allowed" pop-up over a playing video: close it and keep the video going. */
    function closeEnforcement() {
      var box = document.querySelector('tp-yt-paper-dialog ytd-enforcement-message-view-model');
      if (!box) return;
      box.closest('tp-yt-paper-dialog').remove();
      document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function (b) { b.remove(); });
      var video = document.querySelector('#movie_player video, video');
      if (video && video.paused) video.play().catch(function () {});
      report();
    }

    var BLOCKED_TEXT = /ad ?blockers? (violate|are not allowed)|allowlisted|video playback is blocked/i;

    /**
     * YouTube refused to play the video at all. Nothing on the page can undo that, so AdOchi tells the
     * extension, which steps back from YouTube for a while and reloads the video.
     */
    function checkBlocked() {
      if (blockedSent) return;
      var err = document.querySelector('#movie_player .ytp-error, #error-screen, yt-playability-error-supported-renderers');
      if (!err || !BLOCKED_TEXT.test(err.textContent || '')) return;
      blockedSent = true;
      try {
        if (window.AdOchiBridge && typeof window.AdOchiBridge.onYouTubeBlocked === 'function') window.AdOchiBridge.onYouTubeBlocked();
      } catch (e) { /* app without this hook */ }
      document.dispatchEvent(new CustomEvent('adochi:ytblocked'));
    }

    // React the moment the player switches into an ad, and keep checking in case an event is missed.
    // ratechange and volumechange catch a speed or sound reset where holding them is not possible,
    // loadstart the next ad of a break, timeupdate an ad that stops moving.
    ['loadstart', 'loadedmetadata', 'durationchange', 'play', 'playing', 'ratechange', 'volumechange', 'timeupdate'].forEach(function (name) {
      document.addEventListener(name, function (e) { if (e.target && e.target.tagName === 'VIDEO') tick(); }, true);
    });
    var classWatch = new MutationObserver(tick);
    var watched = null;
    setInterval(function () {
      var player = playerNode();
      if (player && player !== watched) {
        watched = player;
        classWatch.disconnect();
        classWatch.observe(player, { attributes: true, attributeFilter: ['class'] });
      }
      if (player) {
        reloadFirstVideo(player);
        checkAdFreeAnswer(player);
      }
      tick();
      checkBlocked();
      closeEnforcement();
    }, 250);
  }

  /**
   * Ads in AI chats (ChatGPT, Copilot, Perplexity, Gemini, Grok, Meta AI, DeepSeek). Found three ways:
   * known ad boxes by their markup, the ad data in the chat itself (ChatGPT), and as a last resort the
   * small "Sponsored" / "Ad" label or ChatGPT's "Ads do not influence the answers" note. From a label
   * the ad box around it is hidden: the largest small box that holds no part of the real answer.
   */
  function aiChatAds(cfg) {
    if (cfg.css) addStyle(hideRule(cfg.css));
    // :has() in its own rule so older browsers only drop this one.
    if (cfg.cssHas) addStyle(hideRule(cfg.cssHas));

    var labels = {};
    (cfg.labels || []).forEach(function (w) { labels[w.toLowerCase()] = true; });
    var CONTENT = cfg.content;
    var NEVER = 'nav, header, [role="navigation"], [role="banner"], [role="dialog"]';
    var MAX_TEXT = 700;
    var MAX_HEIGHT = 560;

    function isSmall(el) {
      return (el.textContent || '').length <= MAX_TEXT && el.getBoundingClientRect().height <= MAX_HEIGHT;
    }

    /** The ad box around a label: grows until the next step would take in the answer or get big. */
    function adBoxOf(anchor) {
      var node = anchor;
      while (node.parentElement && node.parentElement !== document.body && node.parentElement !== document.documentElement) {
        var parent = node.parentElement;
        if (parent.matches(CONTENT) || parent.querySelector(CONTENT) || !isSmall(parent)) break;
        node = parent;
      }
      return node;
    }

    function hideAd(anchor, needsLink) {
      if (!anchor || anchor.closest(NEVER)) return;
      var box = adBoxOf(anchor);
      // A bare word with no link or picture next to it is not an ad box.
      if (needsLink && !box.querySelector('a[href], img, [role="link"]')) return;
      hide(box);
    }

    function isLabel(el) {
      var text = (el.textContent || '').replace(/[​-‏﻿]/g, '').trim().toLowerCase();
      return text.length < 20 && labels[text] === true;
    }

    function scan(root) {
      if (!root || root.nodeType !== 1 || root.__adochiHidden) return;
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {
        var text = node.nodeValue;
        var el = node.parentElement;
        if (!el || text.length > 160) continue;
        if (cfg.note && cfg.note.test(text.trim())) {
          hideAd(el, false);
        } else if (text.length < 20 && isLabel(el) && el.children.length === 0 && !el.closest(CONTENT)) {
          hideAd(el, true);
        }
      }
      if (cfg.buttons) {
        root.querySelectorAll('[aria-label]').forEach(function (el) {
          if (cfg.buttons.test(el.getAttribute('aria-label') || '')) hideAd(el, false);
        });
      }
    }

    var pending = [];
    var timer = null;
    function flush() {
      timer = null;
      var batch = pending;
      pending = [];
      batch.forEach(function (n) { if (n.isConnected) scan(n); });
    }
    function queue(node) {
      pending.push(node);
      if (!timer) timer = setTimeout(flush, 120);
    }
    new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        m.addedNodes.forEach(function (n) {
          if (n.nodeType === 1) queue(n);
          else if (n.nodeType === 3 && n.parentElement) queue(n.parentElement);
        });
      });
    }).observe(document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', function () { queue(document.body); });
  }

  /**
   * ChatGPT sends its ads inside the chat data. They are marked hidden there before the page reads
   * them: "suppress_ads" is switched on, ad blocks are set to hidden and ad HTML is left out.
   */
  function chatGptAdData() {
    var AD_DATA = /"suppress_ads"|"type"\s*:\s*"ads"|"renderedAdsHtml"|"ads"\s*:/;
    var AD_ALLOWED = /"type":"ads","visibility":\{"status":"allowed"/g;
    var AD_HTML = /<template data-assistant-ads-html=""><aside aria-label[\s\S]+?<\/aside><\/template>/g;

    function walk(obj, depth, inMessages) {
      if (!obj || typeof obj !== 'object' || depth > 30) return;
      if (Array.isArray(obj)) {
        for (var i = 0; i < obj.length; i++) walk(obj[i], depth + 1, inMessages);
        return;
      }
      if (own(obj, 'suppress_ads') && obj.suppress_ads !== true) obj.suppress_ads = true;
      if (obj.type === 'ads' && obj.visibility && typeof obj.visibility === 'object' && obj.visibility.status !== 'hidden') {
        obj.visibility.status = 'hidden';
        report();
      }
      if (own(obj, 'renderedAdsHtml')) delete obj.renderedAdsHtml;
      if (inMessages && own(obj, 'ads')) {
        delete obj.ads;
        report();
      }
      for (var k in obj) {
        if (own(obj, k) && obj[k] && typeof obj[k] === 'object') walk(obj[k], depth + 1, k === 'messages');
      }
    }

    wrap(JSON, 'parse', function (target, self, args) {
      var result = Reflect.apply(target, self, args);
      if (typeof args[0] === 'string' && AD_DATA.test(args[0])) {
        try { walk(result, 0, false); } catch (e) { /* leave data as is */ }
      }
      return result;
    });

    // Answers stream in as text before they are parsed; ad blocks in that text are marked hidden too.
    if (window.TextDecoder && TextDecoder.prototype) {
      wrap(TextDecoder.prototype, 'decode', function (target, self, args) {
        var text = Reflect.apply(target, self, args);
        if (typeof text === 'string' && text.indexOf('ads') >= 0) {
          text = text.replace(AD_ALLOWED, '"type":"ads","visibility":{"status":"hidden"').replace(AD_HTML, '');
        }
        return text;
      });
    }
  }

  var AI_CONTENT = '.markdown, .prose, [class*="markdown"], textarea, [contenteditable="true"], form';
  var AI_LABELS = ['Sponsored', 'Ad', 'Ads', 'Promoted'];

  var CHATGPT = {
    css: [
      'section[data-conversation-ads-supplement]', 'aside[data-assistant-ads-unit]', 'div[data-assistant-ads]',
      '[data-testid="bazaar-v2-elevated-box"]', '[data-testid="bazaar-single-card-container"]', '[class*="_assistantAds"]',
      'a[rel][target="_blank"][href*="&oppref="]'
    ],
    cssHas: [
      'div[data-message-supplement]:has(aside[data-partial-island="assistant-ads"])', 'section:has(> [aria-label="Sponsored"])',
      'div[role="complementary"]:has(a[href*="oppref="])',
      '.border-token-border-default.mt-2.border-t:has(button[aria-label="Ad options"])',
      'div.opacity-100.transition-opacity.ease-out:has(button[aria-label="Sponsored options"], button[aria-label="Ad options"])'
    ],
    note: /^ads do not influence the answers/i,
    buttons: /^(ad|sponsored) options$/i,
    labels: AI_LABELS,
    content: AI_CONTENT
  };

  var COPILOT = {
    css: ['.b_ad', 'div[data-testid="ads-card"]'],
    cssHas: ['div[aria-live="polite"][aria-atomic="true"]:has(span[data-ad-part="title"])'],
    buttons: /^(ad|sponsored) (options|info)$/i,
    labels: AI_LABELS,
    content: AI_CONTENT
  };

  var AI_GENERIC = { buttons: /^(ad|sponsored) (options|info)$/i, labels: AI_LABELS, content: AI_CONTENT };

  /** Sponsored results on Google and Bing (the AI answers on top of the results included). */
  var GOOGLE_ADS = [
    '#tads', '#tadsb', '#bottomads', '#google-s-ad', 'div[data-text-ad]', 'div[data-is-ad="1"]', 'div[data-section-type="ads"]',
    'div[data-ads-title="1"]', 'div[data-is-promoted-hotel-ad="true"]', '.commercial-unit-desktop-rhs:not(.mnr-c)',
    '.commercial-unit-mobile-top > div[data-pla="1"]', 'a[href^="https://www.googleadservices.com/pagead/aclk?"]'
  ];
  var BING_ADS = ['.b_ad', '.b_adBottom', '.ad_sc', '#b_wt_adepd', '.adsMvCarousel'];

  if (on('youtube.com')) youtube();

  if (on('facebook.com')) {
    pruneJsonKeys(/instream|ad_break|adBreak/, [
      'instream_video_ad_breaks', 'instream_ad_breaks', 'ad_breaks', 'adBreaks', 'instream_video_ads'
    ]);
    feedCleaner({
      words: SPONSORED.concat(['Paid for by']),
      selectors: [
        'a[href*="/ads/about"]', 'a[href*="ads/about/"]', 'a[href*="/ads/preferences"]', 'a[href*="ad_preferences"]',
        '[data-testid="story-sponsored-label"]', '[data-ad-preview="message"]', '[data-ad-comet-preview="message"]'
      ],
      scrambled: 'a[role="link"] span, a span[id], span[dir="auto"] > span, a[aria-label] span',
      containers: ['[data-pagelet^="FeedUnit"]', 'div[data-tracking-duration-id]', '[role="article"]', 'article'],
      layoutFallback: true
    });
    videoAdSkipper(FACEBOOK_VIDEO);
  }

  if (on('instagram.com')) {
    feedCleaner({ words: SPONSORED, selectors: [], containers: ['article'], layoutFallback: false });
  }

  if (on('x.com') || on('twitter.com')) {
    feedCleaner({
      words: ['Ad', 'Promoted'],
      selectors: ['[data-testid="placementTracking"]'],
      containers: ['[data-testid="cellInnerDiv"]'],
      layoutFallback: false
    });
  }

  if (on('tiktok.com')) {
    feedCleaner({
      words: ['Sponsored', 'Ad', 'Promoted'],
      selectors: ['[data-e2e="ad-tag"]'],
      containers: ['[data-e2e="recommend-list-item-container"]', 'article'],
      layoutFallback: false
    });
  }

  if (on('chatgpt.com') || on('chat.openai.com')) {
    chatGptAdData();
    aiChatAds(CHATGPT);
  }
  if (on('copilot.microsoft.com') || on('copilot.com')) aiChatAds(COPILOT);
  if (on('perplexity.ai') || on('gemini.google.com') || on('grok.com') || on('meta.ai') || on('chat.deepseek.com')) aiChatAds(AI_GENERIC);

  if (/^(www\.)?google\.(com?\.)?[a-z]{2,3}$/.test(hostname)) addStyle(hideRule(GOOGLE_ADS));
  if (on('bing.com')) {
    addStyle(hideRule(BING_ADS));
    addStyle(hideRule(['.slide:has(.rtb_ad_caritem_mvtr)']));
    aiChatAds(COPILOT);
  }
})();
