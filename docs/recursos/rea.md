# REA (Reverse Engineer Anything)

- **Repositorio:** https://github.com/morluto/rea (clon local: `/home/mery/projects/rea`)
- **Instalación:** `npx rea-agents setup` (requiere Node.js 22.x/24.x/26.x)
- **Licencia:** MIT
- **Rol en tvshields:** herramienta de **observación y pruebas**. No se usa para descompilar Brave,
  que es open source (reglas L3).

## Para qué lo usamos

| Necesidad | Capacidad de REA | Guía en REA |
|---|---|---|
| Inventariar qué anuncios y rastreadores carga un sitio (fase 8.2) | Observación de sitios vía Chrome DevTools Protocol: `inspect_web_page`, `observe_web_session` | `docs/browser-observation.md` |
| Comparar el mismo sitio con y sin Shields | `compare_web_captures` | `docs/browser-observation.md` |
| Analizar capturas HAR guardadas | `inspect_web_network_capture` | `docs/web-network-captures.md` |
| Estudiar el script anti-bloqueador de un sitio para entender qué scriptlet le falta | `analyze_web_bundle`, `export_web_scripts` | `docs/website-script-export.md` |
| Ver la TV: paquetes, procesos, logs, capturas de pantalla | `list-adb-devices`, `read-adb-logcat`, `capture-adb-screen`, `inspect-adb-package` | `docs/adb-device-analysis.md` |
| Revisar nuestro propio APK (manifiesto, tamaño, firma) | `inspect-android-package`, `inspect_apk_signature` | `docs/android-analysis.md` |

## Inspeccionar la WebView de la TV directamente

En builds de **depuración**, tvshields activa `WebView.setWebContentsDebuggingEnabled(true)`.
Así la WebView de la TV expone DevTools y REA puede observarla como si fuera un Chrome local:

```bash
adb shell cat /proc/net/unix | grep webview_devtools_remote
```

```bash
adb forward tcp:9222 localabstract:webview_devtools_remote_<PID>
```

```bash
rea list-browser-targets http://127.0.0.1:9222
```

Con esto vemos, **en la TV real**, qué peticiones de anuncios se escapan y qué elementos quedan
visibles en cada página.

## Configuración de adb para REA

```bash
export REA_ADB_PATH=/ruta/absoluta/a/adb
```

```bash
rea inspect-adb-client
```

REA no instala adb ni el SDK: se usan las platform-tools instaladas en la fase 0.4.
