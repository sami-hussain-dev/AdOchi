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

  // The app exposes AdOchiBridge; the browser extension listens for this event instead.
  function report() {
    try {
      AdOchiBridge.onAdHidden();
      return;
    } catch (e) { /* not in the app */ }
    try { document.dispatchEvent(new CustomEvent('adochi:hidden')); } catch (e) { /* very old engine */ }
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
    // "Ad · 0:15", "Ad 1 of 2", "Your video will resume after the ad".
    playing: /(?:^|\|\s*)ads?\s*[·•:]\s*\d|\bad\s+\d+\s+of\s+\d+|your\s+video\s+will\s+(?:resume|continue)|video\s+will\s+resume|(?:resumes?|continues?)\s+after\s+(?:this|the)\s+ad/i,
    skip: /^(skip|skip ad|skip ads)$/i
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
      return text.length > 0 && text.length < 40 && words[text.toLowerCase()] === true;
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
          report();
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
        report();
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

  function youtube() {
    var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];

    function prune(obj) {
      if (!obj || typeof obj !== 'object') return obj;
      AD_KEYS.forEach(function (k) { if (k in obj) delete obj[k]; });
      if (obj.playerResponse && typeof obj.playerResponse === 'object') prune(obj.playerResponse);
      return obj;
    }

    // Strip ad data from the player response before the player reads it.
    var nativeParse = JSON.parse;
    JSON.parse = function () { return prune(nativeParse.apply(this, arguments)); };
    var nativeJson = Response.prototype.json;
    Response.prototype.json = function () { return nativeJson.apply(this, arguments).then(prune); };
    try {
      var initial;
      Object.defineProperty(window, 'ytInitialPlayerResponse', {
        configurable: true,
        get: function () { return initial; },
        set: function (v) { initial = prune(v); }
      });
    } catch (e) { /* already defined */ }

    addStyle(hideRule([
      'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
      'ad-slot-renderer', 'ytm-ad-slot-renderer', 'ytm-mealbar-promo-renderer', 'ytm-statement-banner-renderer',
      'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-promoted-sparkles-web-renderer',
      'ytd-banner-promo-renderer', 'ytd-statement-banner-renderer', 'ytd-display-ad-renderer',
      'ytd-companion-slot-renderer', 'ytd-action-companion-ad-renderer', 'ytd-mealbar-promo-renderer',
      '#player-ads', '#masthead-ad', '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-ad-text-overlay'
    ]));
    // :has() in its own rule so older WebViews only drop this one.
    addStyle(hideRule([
      'ytm-rich-item-renderer:has(ad-slot-renderer)', 'ytm-item-section-renderer:has(ad-slot-renderer)',
      'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)'
    ]));

    // If an ad still starts, mute it, jump to its end and press Skip.
    var inAd = false;
    var savedMuted = false;
    var contentDuration = 0;
    setInterval(function () {
      var player = document.querySelector('.ad-showing, .ad-interrupting');
      var video = document.querySelector('video');
      if (!player && video && isFinite(video.duration)) contentDuration = video.duration;
      if (player) {
        if (!inAd) {
          inAd = true;
          savedMuted = video ? video.muted : false;
          report();
        }
        if (video) {
          video.muted = true;
          // Ads stitched into the video itself share its length; jumping to the end would skip the video.
          var stitched = contentDuration > 0 && Math.abs(video.duration - contentDuration) < 1;
          if (!stitched && isFinite(video.duration) && video.duration > 0) {
            try { video.currentTime = Math.max(video.duration - 0.1, 0); } catch (e) { /* not seekable yet */ }
          }
          video.playbackRate = 16;
        }
        var skip = document.querySelector(
          '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button'
        );
        if (skip) skip.click();
      } else if (inAd) {
        inAd = false;
        if (video) {
          video.muted = savedMuted;
          video.playbackRate = 1;
        }
      }
    }, 300);
  }

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
})();
