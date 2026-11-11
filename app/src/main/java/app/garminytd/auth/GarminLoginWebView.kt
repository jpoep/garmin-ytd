package app.garminytd.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.garminytd.BuildConfig
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "GarminLogin"
private const val BRIDGE = "GarminYtdBridge"

/**
 * Garmin's own mobile sign-in page in a WebView. The user types credentials (and MFA) into Garmin's
 * page, so the app never sees the password, and the request comes from Chromium's real network stack,
 * which Garmin's Cloudflare TLS-fingerprinting lets through.
 *
 * It is not documented how the mobile page hands back the CAS ticket, so we listen on three channels:
 *  1. a navigation/redirect to SERVICE_URL?ticket=ST-...  (classic CAS)
 *  2. the JSON of /mobile/api/login or /mfa/verifyCode containing "serviceTicketId" (JS fetch/XHR hook)
 *  3. the final page's HTML containing ticket=ST-... (embed-widget style)
 * Whichever fires first wins; the rest are ignored.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GarminLoginWebView(
    onTicket: (String) -> Unit,
    onBlocked: (Int) -> Unit,
    onCrashed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ticketCallback = rememberUpdatedState(onTicket)
    val blockedCallback = rememberUpdatedState(onBlocked)
    val crashedCallback = rememberUpdatedState(onCrashed)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(context).apply {
                val main = Handler(Looper.getMainLooper())
                val delivered = AtomicBoolean(false)
                fun deliver(ticket: String, via: String) {
                    if (!GarminConfig.TICKET_REGEX.matches(ticket)) return
                    if (delivered.compareAndSet(false, true)) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "ticket captured via $via")
                        main.post {
                            stopLoading()
                            ticketCallback.value(ticket)
                        }
                    }
                }

                // Garmin serves its dark-themed page for the *_DARK client and expects a dark canvas behind it.
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)

                addJavascriptInterface(TicketBridge { deliver(it, "js-hook") }, BRIDGE)
                val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, setOf("https://sso.garmin.com"))
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        debugLog("navigate", url)
                        ticketIn(url)?.let { deliver(it, "redirect"); return true }
                        // Keep the user on Garmin's login pages; nothing else belongs in this WebView.
                        val host = request.url.host.orEmpty()
                        return !(host == "garmin.com" || host.endsWith(".garmin.com"))
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (url.startsWith(GarminConfig.SERVICE_URL)) {
                            // The redirect target is an app-internal URL with no real page behind it.
                            ticketIn(url)?.let { deliver(it, "intercept") }
                            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return null
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        debugLog("page", url)
                        ticketIn(url)?.let { deliver(it, "page-start") }
                        if (!docStart) view.evaluateJavascript(HOOK_JS, null)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (!docStart) view.evaluateJavascript(HOOK_JS, null)
                        view.evaluateJavascript(SCAN_JS) { result ->
                            GarminConfig.TICKET_REGEX.find(result.orEmpty())?.let { deliver(it.value, "html-scan") }
                        }
                    }

                    // Chromium's renderer died (e.g. killed for memory). Without this the whole app would crash;
                    // instead leave the login screen, which releases this WebView, and let the user retry.
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        debugLog("renderer gone (crash=${'$'}{detail.didCrash()})", "")
                        main.post { crashedCallback.value() }
                        return true
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        val code = errorResponse.statusCode
                        debugLog("http $code", request.url.toString())
                        if (code == 427 || code == 429) main.post { blockedCallback.value(code) }
                    }
                }
                loadUrl(GarminConfig.signInUrl())
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Exposed to JS as window.GarminYtdBridge. Only accepts strings shaped like a CAS ticket. */
class TicketBridge(private val onTicket: (String) -> Unit) {
    @JavascriptInterface
    fun onTicket(ticket: String) {
        if (GarminConfig.TICKET_REGEX.matches(ticket)) onTicket(ticket)
    }
}

/** Clears Garmin's SSO cookies so "Disconnect" really signs out of the WebView session too. */
fun clearGarminWebSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}

private fun ticketIn(url: String): String? {
    if (!url.contains("ticket=")) return null
    return Regex("""[?&]ticket=(ST-[A-Za-z0-9._\-]+)""").find(url)?.groupValues?.get(1)
}

private fun debugLog(what: String, url: String) {
    if (BuildConfig.DEBUG) Log.d(TAG, "$what: ${url.replace(Regex("""ST-[A-Za-z0-9._\-]+"""), "ST-<redacted>")}")
}

/** Wraps fetch + XHR so any JSON response carrying a service ticket is forwarded to the bridge. */
private val HOOK_JS = """
(function () {
  if (window.__gytdHooked) return;
  window.__gytdHooked = true;
  var re1 = /"serviceTicketId"\s*:\s*"(ST-[A-Za-z0-9._\-]+)"/;
  var re2 = /ticket=(ST-[A-Za-z0-9._\-]+)/;
  function scan(text) {
    try {
      if (typeof text !== 'string') return;
      var m = re1.exec(text) || re2.exec(text);
      if (m && window.$BRIDGE) window.$BRIDGE.onTicket(m[1]);
    } catch (e) {}
  }
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function () {
      return origFetch.apply(this, arguments).then(function (resp) {
        try { resp.clone().text().then(scan); } catch (e) {}
        return resp;
      });
    };
  }
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('load', function () {
      try {
        if (this.responseType === '' || this.responseType === 'text') scan(this.responseText);
        else if (this.responseType === 'json') scan(JSON.stringify(this.response));
      } catch (e) {}
    });
    return origSend.apply(this, arguments);
  };
})();
""".trimIndent()

private const val SCAN_JS =
    """(function(){var m=/ticket=(ST-[A-Za-z0-9._\-]+)/.exec(document.documentElement.innerHTML);return m?m[1]:'';})()"""
