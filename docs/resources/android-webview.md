# Android System WebView and androidx.webkit

- **What it is:** the Chromium engine Android provides to apps. It is updated separately through the
  Play Store ("Android System WebView"), so the version depends on each TV.
- **Support library:** `androidx.webkit` (Jetpack). Gives access to newer WebView features on older
  Android versions, as long as the installed WebView supports them.
- Docs: https://developer.android.com/reference/android/webkit/WebView ·
  https://developer.android.com/reference/androidx/webkit/package-summary

## Hooks we use

| API | Use in tvshields | Availability |
|---|---|---|
| `WebViewClient.shouldInterceptRequest(view, WebResourceRequest)` | Filter every request (1.1) and serve redirects (1.3). Called **on background threads**. | API 21+ |
| `WebResourceResponse(mime, encoding, status, reason, headers, stream)` | Empty response or fake resource | API 21+ |
| `WebViewClient.onPageStarted` / `doUpdateVisitedHistory` | Track the current main URL (request source) | Always |
| `WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules)` | Inject cosmetic CSS and scriptlets **before** page code runs | Requires `WebViewFeature.DOCUMENT_START_SCRIPT` |
| `WebViewCompat.addWebMessageListener(webView, name, allowedOriginRules, listener)` | Safe page → app channel for generic cosmetic filtering | Requires `WebViewFeature.WEB_MESSAGE_LISTENER` |
| `WebView.evaluateJavascript` | Injection fallback when `DOCUMENT_START_SCRIPT` is missing (rules C4) | API 19+ |
| `WebView.setWebContentsDebuggingEnabled(true)` | **Debug only**: lets REA/DevTools inspect the TV's WebView | API 19+ |

Always check before use:

```kotlin
if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) { … } else { … }
```

## Known limitations

- `WebResourceRequest` does **not** report the resource type (script, image, xhr…). We infer it
  (see [architecture §5.1](../architecture.md#51-network-blocking-11)).
- It does not report the originating frame: iframes are evaluated against the main page.
- WebSockets are not intercepted.
- Service Worker requests go through `ServiceWorkerControllerCompat` and its own
  `shouldInterceptRequest`, which must be registered too.
- Response headers of requests we let through cannot be modified.
