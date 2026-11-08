# Arquitectura y diseño

## 1. Intención

Un navegador de uso general para televisores Android cuya razón de existir son los
**Shields**: navegar sin anuncios ni rastreadores, igual que en Brave, en una TV con
pocos recursos y usando solo el control remoto.

**Éxito** = abrir sitios con anuncios en la TV de referencia y que no aparezcan ni
anuncios, ni huecos, ni avisos de "desactiva tu bloqueador", sin que la navegación
se sienta más lenta que en el navegador que se usa hoy.

### Objetivos
- Shields 1.1, 1.2 y 1.3 equivalentes a los de Brave, con el mismo motor y las mismas listas.
- APK pequeño (≤ 100 MB, meta ≤ 25 MB) e instalable en cualquier TV Android 7+.
- Uso cómodo con el control remoto.

### No-objetivos
- Sincronización, cartera de criptomonedas, Brave Rewards, VPN, Tor, IA.
- Funcionar en Tizen, webOS o Roku.
- Garantizar el bloqueo de anuncios insertados en el propio flujo de video (ver [reglas C7](reglas.md)).
- Extensiones de terceros.

### Anti-objetivos (fracaso aunque "funcione")
- Que la TV se trabe o la navegación se note lenta por el bloqueo.
- Que la app envíe datos de navegación a cualquier servidor (T9).
- Que use la marca Brave (P7).

## 2. Enfoques evaluados

| | A. WebView + `adblock-rust` ✅ **elegido** | B. GeckoView + uBlock Origin | C. Fork de Chromium / brave-core |
|---|---|---|---|
| Idea | WebView del sistema; el motor de Brave filtra cada petición e inyecta CSS/scripts | Motor de Firefox embebido con uBO como extensión | Compilar el navegador de Brave para TV |
| Tamaño APK | ~15–25 MB | ~80 MB | > 150 MB |
| RAM | Baja (reutiliza la WebView ya cargada en el sistema) | Media-alta | Alta |
| Fidelidad a Brave | Mismo motor y mismas listas | Mismas capacidades, otro motor | Total |
| Código propio | Más (puente JNI, integración) | Menos | Enorme (parches a Chromium) |
| Licencia impuesta | Ninguna (MPL es por archivo) | GPLv3 para todo el proyecto | BSD/MPL |
| Riesgo principal | Depende de la versión de WebView de la TV | Peso en TVs de 2 GB | Compilación: > 100 GB de disco y horas |

**Por qué A:** es el único que cumple con holgura el requisito de ligereza (T1, T7) y usa
literalmente el motor de los Shields de Brave. B queda como plan de contingencia si la
WebView resultara inviable. C se descarta por coste.

## 3. Vista general

```
┌──────────────────────────── APK tvshields ────────────────────────────┐
│                                                                       │
│  ui/            Compose for TV: navegador, barra, panel Shields,      │
│                 ajustes, cursor virtual                               │
│     │                                                                 │
│  browser/       TvWebView, ShieldsWebViewClient, tabs                 │
│     │  shouldInterceptRequest / onPageStarted / WebMessageListener    │
│     ▼                                                                 │
│  shields/       ShieldsManager ── SiteSettings (por dominio)          │
│     │           RequestTypeGuesser, CosmeticInjector, Stats           │
│     │  JNI                                                            │
│     ▼                                                                 │
│  libtvshields_engine.so  (Rust)                                       │
│     └── adblock-rust  Engine                                          │
│                                                                       │
│  lists/         FilterListRepository, ListUpdateWorker                │
│                 (descarga → compila → serializa → recarga)            │
│  search/        SearchEngines, resolución texto → URL                 │
│  assets/js/     scripts propios (cosmético genérico, procedurales)    │
└───────────────────────────────────────────────────────────────────────┘
        ▲ almacenamiento interno: listas .txt, resources.json,
        │ engine.dat (serializado), ajustes (DataStore)
```

## 4. Estructura del repositorio

```
tvshields/
├── README.md
├── docs/
│   ├── reglas.md
│   ├── arquitectura.md
│   ├── fases.md
│   └── recursos/              # una ficha por recurso externo
├── tools/
│   └── resources/             # generador de resources.json (Node.js, solo CI)
├── engine/                    # crate Rust: tvshields-engine
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs             # funciones JNI exportadas
│       └── engine.rs          # envoltura sobre adblock::Engine
├── app/                       # módulo Android
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/js/
│       ├── jniLibs/           # generado por cargo-ndk (no versionado)
│       └── java/…/tvshields/
│           ├── ui/
│           ├── browser/
│           ├── shields/
│           ├── lists/
│           └── search/
├── settings.gradle.kts
└── .github/workflows/         # CI: build Rust + APK, tests, tamaño
```

## 5. Shields: flujos

### 5.1 Bloqueo de red (1.1)

```
WebView pide recurso
   │
   ▼
ShieldsWebViewClient.shouldInterceptRequest(view, request)   [hilo de fondo]
   │
   ├─ ¿Shields apagados para el dominio de la página? ──► null (sigue normal)
   │
   ├─ type = RequestTypeGuesser(request)        # "document", "script", "image", …
   ├─ source = URL del documento principal de esta WebView
   │
   ▼  JNI
engine.check_network_request(Request::new(url, source, type))
   │
   ├─ redirect = Some(data:…)  ──► WebResourceResponse con ese contenido  (1.3)
   ├─ matched y sin excepción  ──► WebResourceResponse vacío (204)  + Stats++
   └─ resto                    ──► null
```

**Tipo de petición.** La WebView no lo informa. `RequestTypeGuesser` lo deduce, en este orden:
1. `request.isForMainFrame` → `document`.
2. Cabecera `Accept`: `text/css` → `stylesheet`, `image/*` → `image`, `text/html` → `sub_frame`.
3. Extensión de la URL: `.js`/`.mjs` → `script`, `.css`, imágenes, `.mp4/.m3u8/.mpd/.ts` → `media`, fuentes → `font`.
4. Si nada aplica → `other`.

Limitación conocida: XHR/fetch no se distinguen de forma fiable (`xmlhttprequest` → `other`).

**URL de origen (`source`).** Es la URL del documento principal, guardada en
`onPageStarted`/`doUpdateVisitedHistory`. Los recursos de iframes se evalúan respecto a
la página principal (limitación aceptada).

### 5.2 Ocultación cosmética (1.2)

Dos partes, igual que en Brave:

**a) Específica de la URL**, al empezar cada página:
```
engine.url_cosmetic_resources(url) → UrlSpecificResources {
    hide_selectors,       # → <style>sel1, sel2 { display:none !important }</style>
    procedural_actions,   # → JSON para el script procedural propio (assets/js)
    exceptions,           # → se guardan para la parte (b)
    injected_script,      # → 1.3
    generichide,          # → si true, desactiva la parte (b)
}
```
Se inyecta con `WebViewCompat.addDocumentStartJavaScript` (antes de que corra el código de
la página). Alternativa si no existe: `evaluateJavascript` en `onPageStarted` (reglas C4).

**b) Genérica**, mientras la página cambia:
```
assets/js/cosmetic.js (en la página)
   MutationObserver junta class e id nuevos
   │  postMessage (WebMessageListener, no addJavascriptInterface)
   ▼
CosmeticInjector → engine.hidden_class_id_selectors(classes, ids, exceptions)
   │
   ▼
inyecta <style> con los selectores devueltos
```
Se agrupa en lotes (debounce ~100 ms) para no saturar la TV (T7).

**Seguridad del canal JS → Kotlin.** Se usa `WebMessageListener` porque permite validar
el origen del mensaje. El mensaje solo admite listas de cadenas cortas (class/id) con tope
de tamaño. Cualquier otra cosa se descarta.

### 5.3 Reemplazo de scripts (1.3, tipo SugarCoat)

Dos mecanismos del motor:

| Mecanismo | Regla típica | Qué hacemos |
|---|---|---|
| **Redirect** | `||ads.example/ad.js$script,redirect=noopjs` | El motor devuelve el recurso falso (`BlockerResult.redirect`) y lo servimos como respuesta. |
| **Scriptlet** | `example.com##+js(set-constant, adBlockDetected, false)` | Llega en `injected_script` y se inyecta al inicio del documento. |

**De dónde salen los recursos** (las reglas solo los nombran; el motor necesita su contenido):

| Origen | Contenido | Licencia | Formato |
|---|---|---|---|
| `brave/adblock-resources` → `dist/resources.json` | 17 recursos propios de Brave, incluidos los `async-sugarcoat-*.js` (SugarCoat real) y arreglos de YouTube | MPL-2.0 | Ya es `Vec<Resource>` en JSON |
| uBlock Origin (`gorhill/uBlock`: `src/js/resources/`, `src/web_accessible_resources/`) | Todos los scriptlets y redirects estándar que usan las listas | GPLv3 | Módulos ES: **hay que transformarlos** a `Resource` con un script JS (así lo recomienda `adblock-rust`) |

Por eso el proyecto incluye un **generador de recursos** (`tools/resources/`, Node.js) que se
ejecuta en CI. Descarga ambas fuentes en versiones fijadas, transforma los recursos de uBO,
los une y publica un único `resources.json` como asset de GitHub Releases. La app lo descarga
junto a las listas y lo carga con `engine.use_resources(...)`. Ver [reglas C11](reglas.md).

### 5.4 Ciclo de vida de las listas

```
Arranque
  ├─ ¿existe engine.dat compatible? ──sí──► Engine::deserialize (rápido)
  └─ no ──► descargar listas + resources ──► FilterSet ──► Engine::new_with_filter_set
                                             └─► serialize → engine.dat

ListUpdateWorker (WorkManager, cada 24 h, requiere red)
  descarga con ETag/If-Modified-Since ──► si cambió: recompila en segundo plano
  ──► intercambio atómico de la instancia ──► serialize
```

**Listas por defecto** (las que Brave activa por defecto, tomadas de su `list_catalog.json`):
Brave Default Adblock Filters (uBO + EasyList), Brave Default Privacy Filters (EasyPrivacy + uBO privacy),
Brave First Party Filters, Cookie notice blocker, Mobile app promo blocker. Las regionales y de
"molestias" quedan como opcionales en Ajustes. Detalle en [recursos/listas-de-filtros.md](recursos/listas-de-filtros.md).

## 6. Control remoto

- **Cursor virtual**: una capa sobre la WebView dibuja un puntero. Las flechas lo mueven (con
  aceleración) y OK genera `MotionEvent` de toque en esa posición. En los bordes, el cursor
  hace scroll.
- **Atajos**: Atrás = página anterior (o salir del modo), Menú/mantener OK = barra de
  direcciones y panel de Shields.
- **Teclado en pantalla**: el del sistema (IME de la TV) para la barra de direcciones.
- Toda la interfaz propia (Compose for TV) es navegable por foco.

## 7. Búsqueda

`search/` resuelve lo que se escribe en la barra:
- Si parece URL (`dominio.tld`, `http…`) → se abre.
- Si no → plantilla del buscador elegido (`https://duckduckgo.com/?q=%s`).

Buscadores precargados: DuckDuckGo (defecto), Brave Search, Google, Bing, Startpage. Sin
sugerencias en vivo (reglas C10).

## 8. Datos guardados

| Dato | Dónde |
|---|---|
| Ajustes generales, buscador, sitios con Shields apagados | Jetpack DataStore |
| Listas descargadas, `resources.json` | `filesDir/lists/` |
| Motor serializado | `filesDir/engine.dat` (incluye la versión del crate; si cambia, se regenera) |
| Contadores de bloqueos | En memoria por pestaña. El total acumulado va en DataStore. |

No hay historial sincronizado ni cuentas.

## 9. Tecnologías

| Pieza | Elección | Por qué |
|---|---|---|
| Lenguaje app | Kotlin | Estándar de Android |
| UI | Jetpack Compose for TV (`androidx.tv`) | Componentes con foco para control remoto |
| Web | Android System WebView + `androidx.webkit` | Ligero (T4). Da `addDocumentStartJavaScript` y `WebMessageListener` |
| Motor bloqueo | `adblock` (crates.io), versión 0.13.x | Motor de Brave (T5) |
| Features del crate | `default-features = false` + `embedded-domain-resolver`, `full-regex-handling` (es decir, los de por defecto **menos** `single-thread`) | Hilos (C2). `resource-assembler` no hace falta: los recursos llegan ya como JSON |
| Puente | crate `jni` (JNI manual) | Llamadas por petición: JNI directo es más rápido que JNA/UniFFI |
| Compilación nativa | `cargo-ndk` + Android NDK | Genera `jniLibs/<abi>/*.so` |
| Tareas periódicas | WorkManager | Actualización de listas |
| Ajustes | Jetpack DataStore | Simple y asíncrono |
| Pruebas | JUnit + Robolectric (Kotlin), `cargo test` (Rust), pruebas instrumentadas en emulador | |
| Verificación real | REA + `adb` sobre la TV de referencia | Ver [recursos/rea.md](recursos/rea.md) |
| CI | GitHub Actions | Build, tests y control de tamaño del APK (T1) |

## 10. Dispositivo de referencia

JVC "COL 4K SMART TV" (fabricante KTC), Android TV 11, SoC MediaTek, parche de seguridad
de noviembre 2023. Todas las mediciones de rendimiento (T7) se hacen aquí.

## 11. Lo que queda a criterio de quien implemente

- Nombres internos de clases y paquetes, siempre que respeten la estructura del §4.
- La curva de aceleración del cursor y los tiempos de debounce exactos.
- Diseño visual de la UI. Restricción: debe ser legible a 3 m de distancia (texto ≥ 18 sp) y
  sin marca Brave.
