package com.adochi.browser

import com.adochi.Credits
import java.net.URLEncoder

/** Full-page warning shown instead of a known phishing/scam/malware site. */
object WarningPage {

    const val SCHEME = "adochi"
    const val BASE_URL = "https://adochi.warning/"

    fun html(target: String, host: String): String {
        val proceed = "$SCHEME://proceed?url=" + URLEncoder.encode(target, "UTF-8")
        return """
<!doctype html>
<html><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dangerous site blocked</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
         background: radial-gradient(circle at 50% 0%, #3a0f1d 0%, #070b16 60%); color:#e8ecf6;
         font-family: system-ui, -apple-system, Roboto, sans-serif; }
  .card { max-width: 420px; margin: 24px; padding: 28px 24px; border-radius: 28px;
          background: rgba(17,24,48,.85); border: 1px solid rgba(255,92,122,.35); text-align:center; }
  .icon { width:72px; height:72px; margin:0 auto 16px; border-radius:50%; display:flex; align-items:center;
          justify-content:center; background: rgba(255,92,122,.15); font-size:36px; }
  h1 { font-size:22px; margin:0 0 8px; }
  .host { display:inline-block; margin:8px 0 16px; padding:6px 12px; border-radius:999px;
          background:rgba(255,92,122,.12); color:#ff8fa3; font-weight:600; word-break:break-all; }
  p { color:#9aa4bf; line-height:1.5; font-size:15px; margin:0 0 12px; }
  a.primary { display:block; margin-top:20px; padding:14px; border-radius:16px; background:#2ee6a8;
              color:#00281b; font-weight:700; text-decoration:none; }
  a.secondary { display:block; margin-top:14px; color:#9aa4bf; font-size:13px; }
  .by { margin-top:22px; color:#6f7a96; font-size:12px; }
</style></head>
<body><div class="card">
  <div class="icon">&#x26A0;&#xFE0F;</div>
  <h1>Dangerous site blocked</h1>
  <div class="host">${escape(host)}</div>
  <p>This site is listed for phishing, scams or malware. Pages like this copy banks, wallets and social
     apps to steal passwords, OTP codes and card details.</p>
  <p>Banks and official services never ask for your PIN or OTP through a link.</p>
  <a class="primary" href="$SCHEME://back">Take me back to safety</a>
  <a class="secondary" href="${escape(proceed)}">I understand the risk, open it anyway</a>
  <div class="by">Protected by AdOchi · made by ${Credits.COMPANY} (${Credits.WEBSITE_LABEL})</div>
</div></body></html>
""".trimIndent()
    }

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
