// Checks cleaner.js without a browser: it loads on every supported site, and the Facebook
// video-ad patterns and ad-break pruning behave. Run with: node app/src/test/js/cleaner_test.js
'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/assets/cleaner.js'), 'utf8');

/** Just enough of a page for the script to start; returns the sandbox and its timers. */
function load(hostname) {
  const intervals = [];
  const noop = () => {};
  const element = () => ({ style: { setProperty: noop }, appendChild: noop, textContent: '' });
  const document = {
    head: element(),
    documentElement: element(),
    body: element(),
    createElement: element,
    addEventListener: noop,
    querySelector: () => null,
    querySelectorAll: () => [],
    createTreeWalker: () => ({ nextNode: () => null }),
    getElementById: () => null,
  };
  function Response() {}
  Response.prototype.json = function () { return Promise.resolve({}); };
  const sandbox = {
    location: { hostname },
    document,
    JSON: { parse: JSON.parse, stringify: JSON.stringify },
    CustomEvent: function () {},
    Response,
    MutationObserver: function () { this.observe = noop; },
    NodeFilter: { SHOW_TEXT: 4 },
    WeakMap,
    Math,
    Date,
    Object,
    Array,
    parseInt,
    parseFloat,
    isFinite,
    getComputedStyle: () => ({}),
    setTimeout: noop,
    setInterval: (fn) => intervals.push(fn),
    innerHeight: 800,
  };
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  intervals.forEach((fn) => fn()); // One tick of every timer must not throw on an empty page.
  return sandbox;
}

for (const host of ['m.facebook.com', 'www.facebook.com', 'm.youtube.com', 'www.instagram.com', 'x.com', 'www.tiktok.com', 'example.com']) {
  load(host);
}

// Ad-break lists are emptied, only on Facebook, and everything else is left alone.
const fb = load('m.facebook.com');
const parsed = fb.JSON.parse('{"video":{"id":1,"instream_video_ad_breaks":[{"offset":30}],"title":"x"},"ad_breaks":"not a list"}');
assert.deepStrictEqual(Array.from(parsed.video.instream_video_ad_breaks), []);
assert.strictEqual(parsed.video.title, 'x');
assert.strictEqual(parsed.ad_breaks, 'not a list');
assert.deepStrictEqual(Array.from(fb.JSON.parse('{"ad_breaks":[1]}').ad_breaks), []);
assert.deepStrictEqual(Array.from(load('example.com').JSON.parse('{"ad_breaks":[1]}').ad_breaks), [1]);

// The Facebook video patterns, taken from the script itself.
const literal = source.match(/var FACEBOOK_VIDEO = (\{[\s\S]*?\n {2}\});/)[1];
const video = vm.runInNewContext('(' + literal + ')');

const countdowns = {
  'Ad starting in 5': 5,
  'Ads starting in 10': 10,
  'Ad break in 3': 3,
  'Ad coming up in 4': 4,
  'Ad will play in 5': 5,
  '0:32 | Ad starting in 5 | Mute': 5,
  'اشتہار 5 میں شروع ہوگا': 5,
  'विज्ञापन 5 सेकंड में': 5,
};
for (const [text, seconds] of Object.entries(countdowns)) {
  const m = video.countdown.exec(text);
  assert.ok(m, 'countdown not found: ' + text);
  assert.strictEqual(parseInt(m[1] || m[2], 10), seconds, text);
}
for (const text of ['Add to story in 5 minutes', 'Read in 5 min', 'Download in 3 steps', 'Bad in 2', 'Admin 5', 'Leading in 5']) {
  assert.ok(!video.countdown.test(text), 'false countdown: ' + text);
}

for (const text of ['Ad · 0:15', 'Ad: 5', 'Live | Ad · 0:12', 'Ad 1 of 2', 'Your video will resume after the ad', 'Video will resume in 5']) {
  assert.ok(video.playing.test(text), 'ad not seen: ' + text);
}
for (const text of ['Ads Manager', 'Admin: 5 posts', 'Add a comment', 'Shared an ad campaign · 2h', 'Bad · 3']) {
  assert.ok(!video.playing.test(text), 'false ad: ' + text);
}
assert.ok(video.skip.test('Skip Ad') && !video.skip.test('Skip intro song'));

console.log('cleaner.js: all checks passed');
