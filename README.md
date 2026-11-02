<p align="center">
  <img src="extension/src/icons/icon-128.png" width="96" alt="AdOchi logo">
</p>

<h1 align="center">AdOchi</h1>

<p align="center">
  <b>Free ad, tracker and scam blocker for Android and every major browser.</b><br>
  No account. No servers. Nothing leaves your device.<br>
  Made by <a href="https://x8code.com">x8code</a>.
</p>

---

Ads aren't only annoying. Fake ads and scam links are one of the main ways people lose their
personal details, bank logins and OTP codes. AdOchi blocks them before they load, on your phone
and in your browser, and warns you when a link is a known scam.

## What it does

### Android app

- **Blocks ads in every app, browser and game:** a local DNS filter with 210,000+ ad and tracker domains.
- **Stops scam, phishing and malware sites:** 370,000+ domains, with an alert that names the app that tried to open one.
- **Clean Browser for Facebook, YouTube, Instagram, TikTok and X:**
  - Removes sponsored posts.
  - Skips YouTube ads.
  - Jumps past Facebook's "Ad starting in 5" video breaks.
- **Phone scan:** finds apps that can read your SMS/OTPs, control your screen (Accessibility), act as device admin or hide their icon.
- **Message checks:** share any WhatsApp or SMS message to AdOchi to see if its link is a scam. The *full* build also checks new messages automatically.
- **Privacy report:** which apps try to track you, and how often.
- **Ad-free offline games:** cut the internet for games that don't need it.
- **More options:**
  - Default-browser mode.
  - Quick Settings tile.
  - Encrypted DNS (DNS-over-HTTPS).
  - Light mode via Private DNS.
  - Weekly report.

### Browser extension (Chrome, Edge, Brave, Opera, Firefox)

- **Blocking:** the same ad, tracker and scam lists, run by the browser itself (Manifest V3 `declarativeNetRequest`).
- **Clean social sites:** the same page cleaner as the app, for Facebook and YouTube.
- **Scam warning page:** shown instead of the dangerous site.
- **Popup controls:**
  - Blocked-on-this-page count and all-time total.
  - Pause on one site.
  - Switch lists on and off.

## Download

Ready-made builds are attached to each [GitHub release](../../releases):

| File | For |
|---|---|
| `AdOchi.apk` | Android 8+. The version to share. |
| `AdOchi-Full.apk` | Android 8+, adds automatic Message Guard (intended for the Play Store) |
| `AdOchi-Extension-Chrome-Edge-Brave.zip` | Chrome, Edge, Brave, Opera |
| `AdOchi-Extension-Firefox.zip` | Firefox 128+ |

**Why two APKs?** Google Play Protect blocks sideloaded apps that ask for notification access.
The direct build leaves that permission out, so it installs without switching anything off.

**Installing the extension before it's in a store:**

1. Unzip the file.
2. Open `chrome://extensions` (or `edge://extensions`).
3. Turn on **Developer mode**.
4. Click **Load unpacked** and pick the folder.

## Privacy

Everything runs on the device. AdOchi has no account, no analytics and no servers of its own.

- **Blocklists:** downloaded straight from their public sources.
- **Allowed lookups:** go to the public DNS resolver you choose.
- **Messages:** checked offline and never stored.

## Building

### Android app

You need **JDK 17 or newer** (JDK 25 works with the bundled Gradle 9.1) and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDirectDebug        # debug build, signed with the debug key
./gradlew testDirectDebugUnitTest    # unit tests (LiveListsTest needs internet)
node app/src/test/js/cleaner_test.js # page-cleaner checks
```

To build signed release APKs:

1. Create a keystore.
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in.
3. Run:

```bash
./gradlew assembleDirectRelease assembleFullRelease
```

There are two product flavors:

- `direct`: no notification access. Use it for sharing as an APK.
- `full`: adds the notification-listener Message Guard.

### Browser extension

You need **Node.js 20+**.

```bash
node extension/tools/build.mjs           # downloads the lists, writes extension/dist/{chrome,firefox} + zips
node extension/tools/build.mjs --cached  # rebuild from lists downloaded earlier
node extension/test/e2e.mjs chrome       # loads it into headless Chrome and checks blocking, warning, popup
```

The build never blocks a list of essential sites (Google, Facebook, WhatsApp, YouTube, Pakistani
banks and wallets, government sites…), whatever a list says. It also fails if any core ad network is missing.

## Project layout

```
app/                     Android app (Kotlin, Jetpack Compose)
  src/main/java/com/adochi/
    core/                blocklists, filter engine, link inspector, phone scanner, stats
    vpn/                 local DNS tunnel, DNS-over-HTTPS, notifications, Quick Settings tile
    browser/             Clean Browser (WebView) and scam warning page
    guard/               Message Guard (full flavor)
    ui/                  screens and components
  src/main/assets/       cleaner.js (shared with the extension) and built-in lists
  src/full/              manifest additions for the full flavor
extension/
  src/                   extension source (manifest, background, popup, warning page)
  tools/build.mjs        list download + rule generation + packaging
  test/e2e.mjs           real-browser test
```

## Blocklists

AdOchi stands on the work of these free projects:

- [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists)
- [OISD](https://oisd.nl)
- [AdGuard DNS filter](https://github.com/AdguardTeam/AdGuardSDNSFilter)
- [StevenBlack hosts](https://github.com/StevenBlack/hosts)
- [Phishing Army](https://phishing.army)
- [malware-filter](https://gitlab.com/malware-filter)

Their licenses apply to the lists.

## License

AdOchi is free software under the [GNU General Public License v3.0](LICENSE). You may use, study,
share and change it. If you share a changed version, you must share its source under the same license.

## Made by x8code

<a href="https://x8code.com"><img src="extension/src/icons/x8code-64.png" width="32" align="left" alt="x8code"></a>

**[x8code](https://x8code.com)** is a software house in Karachi, Pakistan. *Engineers who ship, not just estimate.*
Built by **Muhammad Sami Hussain**, Founder & Lead Software Engineer. More about him at [x8code.com/about](https://x8code.com/about).

Contact: [samihussain9t9@gmail.com](mailto:samihussain9t9@gmail.com) · [GitHub](https://github.com/sami-hussain-dev)
