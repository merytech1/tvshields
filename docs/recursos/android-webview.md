# Android System WebView y androidx.webkit

- **Qué es:** el motor Chromium que Android ofrece a las apps. Se actualiza aparte desde la Play Store
  ("Android System WebView"), así que la versión depende de cada TV.
- **Librería de apoyo:** `androidx.webkit` (Jetpack). Da acceso a funciones nuevas de la WebView en
  Android antiguo, siempre que la WebView instalada las soporte.
- Documentación: https://developer.android.com/reference/android/webkit/WebView ·
  https://developer.android.com/reference/androidx/webkit/package-summary

## Puntos de enganche que usamos

| API | Uso en tvshields | Disponibilidad |
|---|---|---|
| `WebViewClient.shouldInterceptRequest(view, WebResourceRequest)` | Filtrar cada petición (1.1) y servir redirects (1.3). Se llama **en hilos de fondo**. | API 21+ |
| `WebResourceResponse(mime, encoding, status, reason, headers, stream)` | Respuesta vacía o recurso falso | API 21+ |
| `WebViewClient.onPageStarted` / `doUpdateVisitedHistory` | Saber la URL principal actual (origen de peticiones) | Siempre |
| `WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules)` | Inyectar CSS cosmético y scriptlets **antes** de que corra la página | Requiere `WebViewFeature.DOCUMENT_START_SCRIPT` |
| `WebViewCompat.addWebMessageListener(webView, name, allowedOriginRules, listener)` | Canal seguro página → app para el cosmético genérico | Requiere `WebViewFeature.WEB_MESSAGE_LISTENER` |
| `WebView.evaluateJavascript` | Alternativa de inyección si falta `DOCUMENT_START_SCRIPT` (reglas C4) | API 19+ |
| `WebView.setWebContentsDebuggingEnabled(true)` | **Solo en debug**: permite que REA/DevTools inspeccionen la WebView de la TV | API 19+ |

Siempre se comprueba antes de usar:

```kotlin
if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) { … } else { … }
```

## Limitaciones conocidas

- `WebResourceRequest` **no** dice el tipo de recurso (script, imagen, xhr…). Lo deducimos
  (ver [arquitectura §5.1](../arquitectura.md#51-bloqueo-de-red-11)).
- No informa del frame de origen: los iframes se evalúan respecto a la página principal.
- No intercepta WebSockets.
- Las peticiones de *Service Workers* pasan por `ServiceWorkerControllerCompat` y su propio
  `shouldInterceptRequest`: hay que registrarlo también.
- No se pueden modificar las cabeceras de respuesta de las peticiones que dejamos pasar.
