# Arquitectura y diseño

*[Read in English](../architecture.md)*

## 1. Intención

Un navegador de propósito general para Android TV cuya razón de existir son los **Shields**: navegar
sin anuncios ni rastreadores, igual que en Brave, en una TV con pocos recursos y usando solo el
mando.

**Éxito** = abrir sitios con muchos anuncios en la TV de referencia y no ver anuncios, ni huecos,
ni avisos de «desactiva tu bloqueador», sin que navegar se sienta más lento que con el navegador que
se usa hoy.

### Objetivos
- Shields 1.1, 1.2 y 1.3 equivalentes a los de Brave, con el mismo motor y las mismas listas.
- APK pequeño (≤ 100 MB, objetivo ≤ 25 MB) instalable en cualquier TV con Android 7+.
- Cómodo de usar con el mando de la TV.

### No objetivos
- Sincronización, monedero cripto, Brave Rewards, VPN, Tor, IA.
- Funcionar en Tizen, webOS o Roku.
- Bloqueo garantizado de anuncios integrados en el propio flujo de vídeo (véase [reglas C7](rules.md)).
- Extensiones de terceros.

### Antiobjetivos (fallo aunque «funcione»)
- La TV da tirones o navegar se siente lento por culpa del bloqueo.
- La app envía datos de navegación a cualquier servidor (T9).
- Usar la marca Brave (P7).

## 2. Enfoques evaluados

| | A. WebView + `adblock-rust` ✅ **elegido** | B. GeckoView + uBlock Origin | C. Fork de Chromium / brave-core |
|---|---|---|---|
| Idea | WebView del sistema; el motor de Brave filtra cada petición e inyecta CSS/scripts | Motor Firefox embebido con uBO como extensión | Crear el navegador de Brave para TV |
| Tamaño del APK | ~15–25 MB | ~80 MB | > 150 MB |
| RAM | Baja (reutiliza el WebView que el sistema ya tiene cargado) | Media-alta | Alta |
| Fidelidad a Brave | Mismo motor y mismas listas | Mismas capacidades, otro motor | Total |
| Código propio | Más (puente JNI, integración) | Menos | Enorme (parches de Chromium) |
| Licencia impuesta | Ninguna (la MPL es por archivo) | GPLv3 para todo el proyecto | BSD/MPL |
| Riesgo principal | Depende de la versión del WebView de la TV | Peso en TV de 2 GB | Build: > 100 GB de disco y horas |

**Por qué A:** es la única opción que cumple con holgura el requisito de ligereza (T1, T7) y usa el
motor real que hay detrás de los Shields de Brave. B queda como plan de respaldo si el WebView
resulta inviable. C se descarta por coste.

## 3. Visión general

```
┌──────────────────────────── APK de tvshields ─────────────────────────┐
│                                                                       │
│  ui/            Compose for TV: navegador, barra de direcciones,      │
│                 panel de Shields, ajustes, cursor virtual             │
│     │                                                                 │
│  browser/       TvWebView, ShieldsWebViewClient, pestañas             │
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
│                 (descargar → compilar → serializar → recargar)        │
│  search/        SearchEngines, resolución de texto → URL              │
│  assets/js/     scripts propios (cosmético genérico, filtros          │
│                 procedurales)                                         │
└───────────────────────────────────────────────────────────────────────┘
        ▲ almacenamiento interno: archivos .txt de listas, resources.json,
        │ engine.dat (serializado), ajustes (DataStore)
```

### 3.1 Patrón arquitectónico

Un **monolito modular**: un APK, un proceso, sin backend. Las únicas dependencias de red son
archivos estáticos (listas de filtros, `resources.json`) y la navegación del propio usuario.

Dentro de la app, **MVVM por capas**, como recomienda Google para Android:

```
UI          Pantallas Compose for TV. Renderizan estado, reenvían eventos del usuario.
  ↕ observa StateFlow
ViewModel   Estado de pantalla y acciones del usuario. Sin referencias a Views de Android ni al WebView.
  ↕
Dominio     ShieldsManager, SearchResolver, RequestTypeGuesser: reglas del proyecto, Kotlin puro.
  ↕
Datos       FilterListRepository, SettingsRepository (DataStore), AdblockEngine (JNI).
```

- **Una sola Activity.** Las pantallas son composables con Navigation Compose, no Activities
  separadas.
- **Paquetes por función** (`browser/`, `shields/`, `lists/`, `search/`, `settings/`), cada uno con
  sus capas dentro.
- **El motor nativo queda detrás de una interfaz Kotlin** (`AdblockEngine`). `NativeAdblockEngine`
  es la implementación JNI; las pruebas usan un fake, así la lógica de dominio se puede probar sin
  el `.so`.
- **Excepción: la ruta caliente del WebView.** `shouldInterceptRequest` llama directamente a
  `ShieldsManager`, sin pasar por un ViewModel, porque se ejecuta en hilos de fondo para cada
  petición. Los ViewModels solo reciben resultados agregados (por ejemplo, el contador de
  bloqueados).
- **Inyección de dependencias a mano** (un `AppContainer` creado en `Application`). Sin
  Hilt/Dagger, para mantener ligeros el APK y la compilación (T1, T7).
- **Sin Docker.** La app se ejecuta en Android y la compilación usa el toolchain local (roadmap 0.4)
  o los runners de GitHub Actions.

## 4. Estructura del repositorio

```
tvshields/
├── README.md
├── docs/
│   ├── rules.md
│   ├── architecture.md
│   ├── roadmap.md
│   ├── releases/              # notas de versión, un archivo por versión (vX.Y.Z.md)
│   └── resources/             # una página por recurso externo
├── engine/                    # crate de Rust: tvshields-engine
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs             # funciones JNI exportadas
│       └── engine.rs          # envoltorio de adblock::Engine
├── tools/
│   ├── resources/             # generador de resources.json (Node.js, solo CI)
│   ├── licenses/              # generador de third_party_licenses.txt (Node.js, a mano)
│   └── capture/               # grabación y comparación de peticiones en el WebView de la TV (Node.js, fase 8)
├── app/                       # módulo Android
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/js/
│       ├── assets/third_party_licenses.txt   # generado por tools/licenses
│       ├── jniLibs/           # generado por cargo-ndk (no versionado)
│       └── java/…/tvshields/
│           ├── ui/
│           ├── browser/
│           ├── shields/
│           ├── lists/
│           ├── search/
│           └── settings/
├── settings.gradle.kts
└── .github/                  # workflows: CI (build, tests, comprobación de tamaño), release firmada, recursos; actions: preparación compartida
```

## 5. Shields: flujos

### 5.1 Bloqueo de red (1.1)

```
El WebView pide un recurso
   │
   ▼
ShieldsWebViewClient.shouldInterceptRequest(view, request)   [hilo de fondo]
   │
   ├─ ¿Shields desactivados para el dominio de la página? ──► null (continúa normal)
   │
   ├─ type = RequestTypeGuesser(request)        # "document", "script", "image", …
   ├─ source = URL del documento principal de este WebView
   │
   ▼  JNI
engine.check_network_request(Request::new(url, source, type, method))
   │
   ├─ redirect = Some(data:…)    ──► WebResourceResponse con ese contenido  (1.3)
   ├─ should_block()             ──► WebResourceResponse vacío (204) + Stats++
   └─ si no                      ──► null
```

**Tipo de petición.** El WebView no lo informa. `RequestTypeGuesser` lo infiere, en este orden:
1. `request.isForMainFrame` → `document`.
2. Primer tipo de la cabecera `Accept`: `text/css` → `stylesheet`, `image/…` → `image`,
   `text/html` → `sub_frame`. Solo cuenta el primer tipo: el `Accept` de una página también lista
   tipos de imagen.
3. Extensión de la ruta de la URL (nunca el host ni la query): `.js`/`.mjs` → `script`, `.css` →
   `stylesheet`, imágenes (`png`, `jpg`, `gif`, `webp`, `avif`, `svg`…) → `image`, `mp4`, `m4s`,
   `webm`, `m3u8`, `mpd`, `ts`, `mp3`… → `media`, `woff`/`woff2`/`ttf`/`otf`/`eot` → `font`.
4. Si no coincide nada → `other`.

Comprobado en la TV de referencia (2026-11-09): el WebView pasa `Accept` a
`shouldInterceptRequest`. Las hojas de estilo envían `text/css,*/*;q=0.1`, las imágenes
`image/jxl,image/avif,…`, los iframes `text/html,…`; los scripts, las fuentes y XHR/fetch envían
`*/*`, así que dependen de la extensión.

Limitación conocida: XHR/fetch no se puede distinguir con fiabilidad (`xmlhttprequest` → `other`).
Tampoco los scripts sin extensión `.js` (por ejemplo, `gtag/js?id=…`).

**URL de origen (`source`).** La URL del documento principal, registrada en
`onPageStarted`/`doUpdateVisitedHistory`. Los recursos de iframes se evalúan contra la página
principal (limitación aceptada).

**Decisiones en `ShieldsWebViewClient`** (roadmap 3.4):
- Subrecurso bloqueado → respuesta vacía `204 No Content`.
- Documento principal bloqueado (reglas `$document`, por ejemplo dominios de malware) → una página
  breve que dice que Shields lo bloqueó.
- `redirect=` → bloqueado hasta que llegan los recursos (roadmap 5.2). Las URL reescritas
  (`removeparam`) se ignoran: el WebView no puede cambiar la URL de un subrecurso.
- Motor aún cargando, URL no HTTP (`data:`, `blob:`) o error del motor → se permite.
- `adb shell setprop log.tag.tvshields.block DEBUG` registra cada URL bloqueada (se lee al crear el
  WebView; se usa en el roadmap 8.3).

Comprobado en la TV de referencia (2026-11-09, listas por defecto): adblock-tester.com obtiene
95/100 (27 peticiones bloqueadas); theverge.com carga con normalidad con 16 peticiones de anuncios
y rastreadores bloqueadas.

**Interruptor por sitio (reglas P3, C5; roadmap 6.2).** El panel de Shields desactiva Shields para
el sitio de la página actual. El sitio es el host, en minúsculas, sin `www.`. Desactivarlo en
`example.com` también cubre sus subdominios, pero no al revés. `SiteShieldsRepository` guarda la
lista en DataStore (`shields_off_sites`) y una copia en memoria:

- `ShieldsManager` y `CosmeticFilter` leen primero la copia en memoria, usando la URL de la página.
  El motor nunca ve las peticiones de una página de un sitio desactivado, y esa página no recibe
  CSS, ni script genérico o procedural, ni scriptlets.
- Un cambio se aplica al momento y se escribe en segundo plano. Las escrituras salen de una en una,
  en el orden en que se hicieron los cambios, así que desactivar y activar rápido un sitio termina
  como «activado» también en disco. El archivo se lee una vez al arrancar; después, la memoria es
  la verdad.
- La página se recarga tras el cambio. `CosmeticInjector.invalidate()` hace que el siguiente
  registro vuelva a preguntar por la misma URL.
- Al arrancar, la primera página puede cargar antes de que se haya leído DataStore (unos
  milisegundos). Esa página mantiene Shields activados.

Comprobado en la TV de referencia (2026-11-15): theverge.com mostró 13 bloqueados con Shields
activados. Tras desactivarlos y recargar, mostró 0. Tras reiniciar la app, el sitio seguía
desactivado con 0 bloqueados. Al volver a activarlos, mostró 17 bloqueados.

### 5.2 Filtrado cosmético (1.2)

Tres partes, como en Brave:

**a) Específico de URL**, al inicio de cada página:
```
engine.url_cosmetic_resources(url) → UrlSpecificResources {
    hide_selectors,       # → <style>sel1, sel2 { display:none !important }</style>
    procedural_actions,   # → JSON para nuestro script procedural (assets/js)
    exceptions,           # → se guarda para la parte (b)
    injected_script,      # → 1.3
    generichide,          # → si es true, desactiva la parte (b)
}
```
Se inyecta con `WebViewCompat.addDocumentStartJavaScript` (antes de que se ejecute cualquier código
de la página). El script se registra antes de que empiece la carga: desde la barra de direcciones e
Inicio, desde `shouldOverrideUrlLoading` para clics en enlaces, y desde el
`shouldInterceptRequest` del marco principal como última oportunidad para redirecciones. El
registro es una llamada al hilo de UI, así que el hilo de la petición espera a que termine antes de
devolver la respuesta; el script de la página anterior se elimina en cada navegación. Los iframes
del mismo origen reciben los selectores de la página principal (limitación aceptada, como en §5.1).

El mismo CSS se aplica otra vez en `onPageStarted` como red de seguridad (actualiza el mismo
elemento `<style>`). Así se cubren los WebViews sin `DOCUMENT_START_SCRIPT` (reglas C4) y los
registros que llegaron tarde; puede ejecutarse después del primer pintado de la página, así que los
anuncios pueden parpadear antes de ocultarse.

**b) Genérico**, a medida que la página cambia:
```
assets/js/cosmetic.js (solo el marco principal)
   escaneo inicial + MutationObserver: nodos nuevos y cambios de class/id
   │  postMessage (WebMessageListener, no addJavascriptInterface), agrupado ~100 ms
   ▼
CosmeticInjector → engine.hidden_class_id_selectors(classes, ids, exceptions)
   │  (hilo de fondo)
   ▼
añade los selectores devueltos a un elemento <style> aparte
```
El script se inyecta al inicio del documento solo cuando el `generichide` de la página es false y
`WEB_MESSAGE_LISTENER` está soportado. Sin `DOCUMENT_START_SCRIPT` se arranca desde `onPageStarted`
(reglas C4). Cada clase o id se envía como máximo una vez por documento. El agrupado y esos límites
mantienen la TV ágil (T7).

**Seguridad del canal JS → Kotlin.** Se usa `WebMessageListener` porque la app puede validar el
origen. Solo se aceptan mensajes de texto del marco principal cuyo origen coincida con la página
actual. El JSON puede contener como máximo 500 valores de clase y 500 de id de 128 caracteres como
máximo, en un mensaje de 16 KB como máximo; lo demás se descarta. El motor se ejecuta fuera del
hilo de UI, los selectores devueltos se deduplican y cada lote de CSS tiene un tope. El listener y
su ámbito de corrutinas se liberan al destruir el WebView.

**c) Procedural**, al inicio del documento y a medida que la página cambia:
```
engine.url_cosmetic_resources(url) → procedural_actions (JSON estructurado; el
                                     motor se compila con `css-validation` de adblock-rust)
                                    + hide_selectors con sintaxis procedural
   ▼
CosmeticFilter / ProceduralSelector (Kotlin): valida operadores y acciones,
   divide el texto procedural anidado dentro de un `css-selector` estructurado
   ▼
assets/js/procedural.js (solo el marco principal): escaneo inicial + MutationObserver
```
Operadores soportados: `has-text` (`contains`, `-abp-contains`), `has` (incluidas las formas
relativas `>`, `+` y `~`), `not`, `matches-attr`, `matches-css`, `matches-css-before`,
`matches-css-after`, `matches-path`, `min-text-length`, `upward` y `xpath`. Acciones soportadas:
ocultar por defecto, `remove`, `style`, `remove-attr` y `remove-class`. adblock-rust no puede
dividir `:has(...)` anidado dentro de otro `:has` (`:has(p:has-text(...))`): esos selectores quedan
en bruto y Kotlin los analiza. El texto procedural anidado que llega dentro de un `css-selector`
estructurado (un `:has` anidado con una acción) se divide recursivamente antes de llegar a la
página.

El script se ejecuta solo cuando la página tiene filtros procedurales. Se inyecta con el mismo
registro de inicio de documento que (a), y también desde `onPageStarted` como fallback de C4; su
punto de entrada ignora un segundo arranque. Cada pasada está acotada (reglas T7): como máximo 32
filtros, 32 operadores por filtro y profundidad 8; 2.500 nodos candidatos por pasada; 1.200 nodos
de mutación en cola y 6 ancestros por cambio; 150 ms de debounce; presupuestos de 20 ms iniciales y
8 ms por mutación, y el trabajo restante se reintenta. Un cambio `childList` vuelve a comprobar su
padre (esto detecta la sustitución de `textContent`), y los cambios de `characterData` o de
atributos vuelven a comprobar el objetivo y sus ancestros.

### 5.3 Reemplazo de scripts (1.3)

Dos mecanismos del motor:

| Mecanismo | Regla típica | Qué hacemos |
|---|---|---|
| **Redirección** | `||ads.example/ad.js$script,redirect=noopjs` | El motor devuelve el recurso falso (`BlockerResult.redirect`) y lo servimos como respuesta. |
| **Scriptlet** | `example.com##+js(set-constant, adBlockDetected, false)` | Llega en `injected_script` y se inyecta al inicio del documento. |

**Servir una redirección (roadmap 5.2).** El motor responde con una URL
`data:<mime>;base64,…`. `RedirectResource` la decodifica y `ShieldsWebViewClient` la devuelve desde
`shouldInterceptRequest` como un `200 OK` con ese tipo MIME (`utf-8` para tipos de texto) y
`Access-Control-Allow-Origin: *`, porque el sustituto reemplaza scripts y archivos que una página
puede cargar de otro origen (`crossorigin`, `fetch`). Una petición del marco principal nunca se
sustituye por un recurso: recibe la página de bloqueo. Una URL que no se puede decodificar, o una
regla cuyo recurso falta, cae al `204` vacío de un bloqueo normal. Con `log.tag.tvshields.block` en
`DEBUG`, las redirecciones servidas se registran como `Redirected (<mime>) <url>`.

Comprobado en la TV de referencia (2026-11-14, listas por defecto): theverge.com recibe los
sustitutos de uBO para `gpt.js` (dos veces) y `apstag.js`; dentro de la página, `googletag.apiReady`
es `true` y `apstag` existe aunque los scripts reales nunca llegaron a la red.

**Inyectar scriptlets (roadmap 5.3).** `CosmeticFilter` conserva el `injected_script` del motor
(los scriptlets `+js()` de la página con sus funciones auxiliares) y `CosmeticInjector` lo pone el
primero en el mismo script de inicio de documento que el CSS cosmético (§5.2), para que se ejecute
antes que cualquier script de la página. El código se envuelve (`scriptletScript`) en una función
que:

- declara `const scriptletGlobals = {}`. Los scriptlets de uBO lo esperan de su propio envoltorio;
  sin él, `safeSelf()` lanza una excepción y todos los scriptlets fallan en silencio. Con vacío
  basta: solo leen el `bcSecret` opcional (logger), `logLevel` y `warOrigin`.
- pone una marca no enumerable de ejecución única en `window`, con un nombre aleatorio por proceso
  de la app. El fallback de `onPageStarted` (reglas C4) repite todo el script; la marca evita que
  los scriptlets se ejecuten dos veces en un documento.

Límites:

- Los scriptlets se ejecutan en el mundo principal de la página. En uBO, 22 de ellos se ejecutan en
  un mundo aislado. Aquí la página puede ver lo que definen, como en Brave en Android.
- Se ejecutan a tiempo solo cuando el script se registró antes de que empezara la carga (barra de
  direcciones, enlaces, atrás, recargar). Para una redirección del servidor a otro origen, solo se
  ejecuta el fallback, y eso es después de los primeros scripts de la página.
- Los marcos de otro origen no los reciben.

Comprobado en la TV de referencia (2026-11-15):

- `ScriptletInjectionTest` carga una página con el detector clásico de scripts cebo (`canRunAds`) en
  un WebView real, con el motor y el `resources.json` descargado. Solo con `||detector.test/ads.js^`
  aparece el aviso. Con `detector.test##+js(set, canRunAds, true)` no aparece, y los scriptlets se
  ejecutaron una vez.
- En coolmathgames.com (listas por defecto), `displayAdblockOverlay` queda fijado a `false` por
  `set-constant`.
- bild.de sigue mostrando su muro antiadblock. El muro reacciona al `html-load.com/sdk.js` bloqueado
  (AdShield) y a la petición `pv-data` de Sourcepoint, que las reglas actuales de las listas no
  deshacen.

Las compilaciones de depuración activan la depuración del WebView
(`WebView.setWebContentsDebuggingEnabled`), así que una página de la TV se puede inspeccionar desde
`chrome://inspect` o mediante el protocolo DevTools:
`adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>`. Las compilaciones de release no.

**De dónde salen los recursos** (las reglas solo los nombran; el motor necesita su contenido):

| Origen | Contenido | Licencia | Formato |
|---|---|---|---|
| `brave/adblock-resources` → `dist/resources.json` | 17 recursos específicos de Brave, incluidos los archivos `async-sugarcoat-*.js` (salida real de SugarCoat) y arreglos para YouTube | MPL-2.0 | Ya es un `Vec<Resource>` en JSON |
| uBlock Origin (`gorhill/uBlock`: `src/js/resources/`, `src/web_accessible_resources/`) | Todos los scriptlets y redirecciones estándar que usan las listas | GPLv3 | Módulos ES: **hay que transformarlos** a `Resource` con un script JS (como recomienda `adblock-rust`) |

Por eso el proyecto incluye un **generador de recursos** (`tools/resources/`, Node.js) que se
ejecuta en CI. Descarga ambas fuentes en versiones fijadas, transforma los recursos de uBO, los
fusiona y publica un único `resources.json` como asset de GitHub Releases. La app lo descarga junto
con las listas y lo carga con `engine.use_resources(...)`. Véase [reglas C11](rules.md).

La detección antiadblock se resuelve sobre todo con scriptlets de uBO. SugarCoat cubre unos pocos
scripts de rastreo que no se pueden bloquear sin romper la página. Véase
[resources/replacement-resources.md](../resources/replacement-resources.md) (en inglés).

### 5.4 Ciclo de vida de las listas

```
Arranque
  ├─ ¿existe un engine.dat compatible? ──sí──► Engine::deserialize (rápido)
  └─ no ──► descargar listas + recursos ──► FilterSet ──► Engine::new_with_filter_set
                                            └─► serializar → engine.dat
  después, en ambos casos: use_resources(resources.json) (no forma parte de engine.dat)

ListUpdateWorker (WorkManager, cada 24 h, requiere red)
  descargar con ETag/If-Modified-Since ──► si cambió: recompilar en segundo plano
  ──► intercambio atómico de instancia ──► serializar
```

`EngineRepository` carga el motor en un hilo de fondo al arrancar la app. `engine.dat` empieza con
una cabecera: un valor mágico, una versión de formato y una huella de las listas guardadas (sus URL,
tamaños y `ETag` / `Last-Modified`). La caché se usa solo si la cabecera coincide y adblock-rust
acepta los datos (rechaza datos de otra versión del crate). Si no, el motor se construye a partir de
las listas y la caché se vuelve a escribir.

**Recursos (roadmap 5.1).** `FilterListRepository` descarga `resources.json` desde la release
`resources` (`DefaultLists.RESOURCES_URL`, reglas C11) después de las listas, con el mismo
tratamiento de `ETag` / `If-Modified-Since`, en `filesDir/lists/resources.json`. Una respuesta 200
con los mismos bytes también cuenta como sin cambios, para que un servidor que nunca responde 304 no
provoque una reconstrucción. Los recursos no se guardan en `engine.dat`: `EngineRepository` los
carga en cada motor (desde la caché o recién construido) antes de compartirlo. Sin ellos el bloqueo
sigue funcionando: las reglas `redirect=` bloquean y los scriptlets se omiten. Un motor que arranca
sin recursos (una instalación que guardó sus listas antes de 5.1, o una descarga fallida) lanza una
actualización de inmediato en vez de esperar 24 h. Un `resources.json` nuevo cuenta como cambio y
reconstruye el motor, igual que una lista cambiada.

Comprobado en la TV de referencia (2026-11-14): instalado encima de la versión anterior, el motor
arrancó desde las listas guardadas sin recursos, la actualización inmediata descargó
`resources.json` e intercambió un motor con los 217 recursos aceptados. El test instrumentado
`DownloadedResourcesTest` carga el archivo descargado en el dispositivo y comprueba `noopjs`,
`set-constant` (con sus funciones auxiliares), cada redirección `async-sugarcoat-*` y la restricción
trusted.

Cada lista se construye con su permiso: las listas de una entrada de catálogo de confianza pueden
inyectar los scriptlets `requiresTrust` de uBO
([resources/replacement-resources.md](../resources/replacement-resources.md)).

Medido en la TV de referencia (2026-11-09, listas por defecto, 9 MB de texto, build de depuración):

| Arranque | Motor listo en |
|---|---|
| Primero (descarga + compilación + guardado) | ~9,7 s |
| Compilación desde las listas guardadas | ~4,7 s |
| Desde `engine.dat` (10 MB) | ~0,76 s (2026-11-14: ~0,76 s con los 217 recursos, 1 MB) |

El motor se carga en paralelo con la UI y no retrasa la primera pantalla.

**Primer arranque (reglas C1).** `EngineLoader` sigue el estado del motor para la UI:

| Estado | Cuándo | Pantalla |
|---|---|---|
| `Loading` | Hay listas guardadas: cargar desde caché o compilar | Navegador (las peticiones pasan hasta que el motor está listo, < 1 s) |
| `Downloading` | Aún no hay listas guardadas | «Preparando Shields», progreso por entrada del catálogo («3 de 5 listas») |
| `NoLists` | La descarga no obtuvo ninguna lista | «Sin conexión»: *Reintentar* / *Continuar sin protección* |
| `Ready` | Motor cargado | Navegador |
| `Unprotected` | El usuario continuó sin listas, o el motor nativo falló | Navegador, sin bloqueo |

Si hay listas guardadas se comprueba de forma síncrona al arrancar la app, así que un arranque
normal nunca muestra la pantalla de primer arranque. Sin listas, la pregunta vuelve en el siguiente
arranque. Una descarga parcial (algunas fuentes fallaron) sigue construyendo un motor con lo que
llegó.

**Actualizaciones de listas (roadmap 3.6).** `ListUpdateWorker` se ejecuta cada 24 h con red
(WorkManager, trabajo periódico único, primera ejecución 24 h después del primer arranque). Llama a
`EngineLoader.refresh()`:
- `FilterListRepository.update()` envía `ETag` / `If-Modified-Since`. Si nada cambió, no ocurre
  nada más.
- Si una lista cambió (o no hay motor todavía, por ejemplo tras «Continuar sin protección»), se
  construye un motor nuevo en segundo plano y se reescribe `engine.dat`.
- El motor nuevo se intercambia de forma atómica (`StateFlow.getAndUpdate`). Cada petición lee el
  motor actual, así que usa el viejo o el nuevo, nunca ninguno. El motor viejo se cierra 10 s
  después, cuando cualquier petición que ya lo hubiera tomado ha terminado.

**Listas opcionales y actualizaciones forzadas (roadmap 6.3).** Ajustes → Listas de filtros activa
las listas opcionales (`OptionalLists`: tres listas de molestias, las tres excepciones de
incrustación y las 33 listas regionales del catálogo) y tiene un botón «Actualizar listas».
`FilterListRepository` pide al proveedor las listas activadas cada vez (`DefaultLists` + los ids
guardados en DataStore), así que la actualización, la compilación y la huella siguen a los
interruptores:
- Activar una lista la guarda al momento; 1,5 s después (varios cambios comparten uno) se ejecuta el
  mismo `EngineLoader.refresh()`. Descarga lo que falta y reconstruye el motor.
- `EngineRepository` recuerda la huella del motor en uso. Un refresco también reconstruye cuando la
  huella cambió sin ninguna descarga: una lista desactivada, o reactivada mientras su archivo sigue
  guardado. Los archivos de las listas desactivadas se quedan en `filesDir/lists/`.
- `refresh()` devuelve qué ocurrió (`ListRefresh`: actualizado, al día, algunas listas fallaron, sin
  conexión, error del motor) y Ajustes lo muestra. Una vez iniciado, un refresco termina aunque se
  cancele su llamador (cerrar Ajustes, que WorkManager detenga el trabajo): un motor construido y
  cacheado pero nunca intercambiado quedaría sin usar hasta el siguiente arranque.

**Acerca de y créditos (roadmap 6.4, reglas L1, L2, C6).** Ajustes → Acerca de muestra:
- la versión de la app, su licencia (MPL-2.0) y su código fuente;
- el motor (adblock-rust, MPL-2.0, sin modificar de crates.io) y los recursos dentro de
  `resources.json` (adblock-resources de Brave, MPL-2.0; scriptlets de uBlock Origin, GPL-3.0);
- cada lista del catálogo, por defecto y opcional, con el autor y la licencia de cada fuente y si la
  lista está activada. El catálogo de Brave no tiene autores ni licencias: `ListCredits` mapea cada
  prefijo de URL de origen a ellos (véase [filter-lists.md](../resources/filter-lists.md#credits)).
  Un test falla si una fuente no tiene crédito;
- la lista de servidores que contactan las actualizaciones de listas (C6): los hosts de las listas
  activadas y de `resources.json`. Cambian con los interruptores de Listas de filtros;
- las bibliotecas dentro del APK y «Licencias de terceros», que abre
  `assets/third_party_licenses.txt`.

`tools/licenses/generate.mjs` escribe ese archivo. Lista las bibliotecas de Android (todas
Apache-2.0) y los crates de Rust enlazados en el motor (dependencias normales de los dos targets de
Android, sin proc-macros), seguidos de los archivos de licencia de cada crate. Los textos idénticos
se imprimen una vez. Ejecuta `node tools/licenses/generate.mjs` después de cambiar
`engine/Cargo.lock` o las dependencias de la app. `AboutTest` falla cuando un crate del archivo ya
no coincide con `Cargo.lock`, o la versión de adblock-rust acreditada difiere de la compilada.

Comprobado en la TV de referencia (2026-11-18): se llega con las flechas a todas las filas de Acerca
de. El texto de la licencia se abre desde su fila, avanza casi una pantalla por Arriba/Abajo, y
Atrás vuelve a la fila.

Comprobado en la TV de referencia (2026-11-17): al activar «Spanish website ad blocker» se descargó
EasyList Spanish y se intercambió un motor nuevo (3,3 s). Una petición a `adzonestatic.com`, que
solo bloquea esa lista, quedó bloqueada; al desactivarla, el motor se reconstruyó sin descarga y la
misma petición pasó. «Actualizar listas» descargó 2 listas cambiadas de 31.

Comprobado en la TV de referencia (2026-11-09): con theverge.com abierto y desplazándose, una
actualización descargó 2 listas cambiadas, reconstruyó el motor en 4,7 s y lo intercambió sin
errores; el siguiente arranque cargó el motor nuevo desde la caché (603 ms). WorkManager no ejecuta
trabajo periódico antes de su hora, ni con `cmd jobscheduler run -f`; para probarlo, encola
temporalmente un `ListUpdateWorker` de una sola vez.

Para probar la pantalla sin conexión en la TV sin cortarle la red, pon un archivo donde va la
carpeta de listas: `adb shell pm clear <app>`, luego `run-as <app> mkdir files` y
`run-as <app> touch files/lists`. Ninguna lista se puede guardar, así que la descarga falla. Un
`http_proxy` global no funciona: las descargas de listas lo ignoran.

**Listas por defecto** (las que Brave activa por defecto, tomadas de su `list_catalog.json`): Brave
Default Adblock Filters (uBO + EasyList + listas de Brave, incluidas las de SugarCoat), Brave
Default Privacy Filters (EasyPrivacy + privacidad de uBO), Brave First Party Filters, Cookie notice
blocker, Mobile app promo blocker. Las listas regionales, de «molestias» y de incrustación son
opcionales en Ajustes (arriba). Detalles en [resources/filter-lists.md](../resources/filter-lists.md).

### 5.5 Coste en la TV de referencia (T7)

**Cómo medir.** `adb shell setprop log.tag.tvshields.perf DEBUG`, luego reinicia la app. `ShieldsPerf`
registra una línea por página bajo la etiqueta `tvshields.perf`: tiempo de carga y, para cada tipo
de trabajo del motor (peticiones, cosmético específico de URL, cosmético genérico), la cuenta, el
total, el máximo y el tiempo en el hilo de UI. Está desactivado por defecto y entonces solo ejecuta
el código medido.

**Resultados** (roadmap 4.4, build de depuración en la TV de referencia, theverge.com, elpais.com y
es.wikipedia.org; desplazamiento = 150 pulsaciones de `DPAD_DOWN`, leído con `dumpsys gfxinfo`):

El desplazamiento da tirones igual con y sin Shields. Tomas seguidas, en el mismo estado de la TV,
dan la misma proporción de fotogramas con tirón; el tirón viene del WebView de esta TV (1,8 GB de
RAM, 4 × Cortex-A55), no de Shields. Durante el desplazamiento, Shields solo comprueba unas pocas
peticiones tardías y lotes genéricos, en hilos de fondo.

| Sitio | Fotogramas con tirón, Shields activados / desactivados (2026-11-12) | Lo mismo, 2026-11-13 (TV intercambiada llena) |
|---|---|---|
| theverge.com | 61 % / 52 % | 81 % / 83 % |
| elpais.com | 33 % / 32 % | 81 % / 80 % |
| es.wikipedia.org | 43 % / 42 % | 90 % / 87 % |

Trabajo del motor mientras carga una página, antes y después de los cambios de 4.4 (tiempo total;
hilo de UI entre paréntesis):

| Sitio | Peticiones, antes | Peticiones, después | Cosmético, antes | Cosmético, después |
|---|---|---|---|---|
| theverge.com | 147 comprobaciones, 2.366 ms (máx. 174) | 147 comprobaciones, 1.316 ms (máx. 93) | 495 ms (270 en UI) | 428 ms (37 en UI) |
| elpais.com | 60 comprobaciones, 1.156 ms (máx. 263) | 67 comprobaciones, 444 ms (máx. 96) | 413 ms (288 en UI) | 323 ms (29 en UI) |
| es.wikipedia.org | 24 comprobaciones, 634 ms (máx. 234) | 34 comprobaciones, 457 ms (máx. 128) | 9 ms (9 en UI) | 10 ms (1 en UI) |

Las comprobaciones de peticiones se ejecutan en los hilos de red del WebView, en paralelo con las
descargas. Memoria de la app (PSS) en theverge.com: 230 MB con Shields, 218 MB sin. Los tiempos de
carga de página varían más de un segundo entre ejecuciones idénticas, así que ejecuciones sueltas no
muestran un coste de Shields; el build de release se mide con más muestras en
[testing §5](../testing.md#5-performance-roadmap-84) (roadmap 8.4).

**Cambios que salieron de las mediciones:**
- `ProceduralSelector.looksProcedural` comprueba cada `:` una vez en lugar de buscar todos los
  operadores en todos los selectores (eso costaba ~55 ms por página).
- `CosmeticFilter` guarda los recursos de las últimas 4 URL (se descartan al intercambiar el motor),
  y `CosmeticInjector` reutiliza el script de la página: una página preguntaba hasta 4 veces, en
  parte en el hilo de UI. El script también se salta reescribir su `<style>` cuando el fallback trae
  el mismo CSS.
- La barra de direcciones pregunta al motor en un hilo de fondo antes de `loadUrl`
  (`CosmeticInjector.prepareInBackground`); el registro en el hilo de UI encuentra luego la
  respuesta en caché.
- El motor se compila con `opt-level = 3` ([resources/rust-on-android.md](../resources/rust-on-android.md)).
- Las primeras llamadas al motor de un proceso son lentas (~45 ms por petición y ~190 ms para
  recursos cosméticos: código y datos fríos). `AppContainer` hace una de cada en segundo plano en
  cuanto el motor está cargado.

Se deja como está: un clic en un enlace o una redirección del servidor a una URL aún no cacheada
sigue preguntando al motor en el hilo de UI (`shouldOverrideUrlLoading`, ~30 ms en estas
ejecuciones), mientras la página anterior sigue en pantalla. La primera consulta cosmética de un
sitio nuevo puede tardar ~300 ms en el hilo de la petición; eso retrasa el documento, no la UI.

## 6. Mando de la TV

- **Cursor virtual**: una capa encima del WebView dibuja un puntero. Las flechas lo mueven (con
  aceleración) y OK genera un `MotionEvent` táctil en esa posición. En los bordes, el cursor
  desplaza la página.
  - Las teclas llegan al cursor por `TvWebView.dispatchKeyEvent`: el WebView consume las flechas
    antes que cualquier `OnKeyListener`.
  - Cada pulsación mueve el cursor un paso fijo (16 px), así los toques cortos apuntan con
    precisión; las teclas mantenidas aceleran. Una pulsación contra un borde desplaza 160 px.
  - El desplazamiento envía `MotionEvent`s de rueda del ratón (`ACTION_SCROLL`), que también
    desplazan paneles internos.
  - Pulsar arriba cuando la página ya no puede desplazarse más lleva a la barra de direcciones;
    abajo vuelve a la página.
  - El cursor se queda a una distancia del tamaño de un puntero de los bordes derecho e inferior,
    para que nunca se oculte fuera de pantalla.
  - OK va a la página como un toque al soltar. Un OK mantenido (el mando lo repite) va a la barra
    de direcciones. La duración de la pulsación por sí sola no basta:
    `adb input keyevent --longpress` informa una duración cero.
- **Botones de la barra superior**: atrás, recargar e inicio están a la izquierda de la barra de
  direcciones. Los botones desactivados (sin historial, o nada que recargar en la página de inicio)
  no reciben foco. Izquierda y Derecha se mueven entre los botones y la barra de direcciones
  mientras no se está editando. Inicio carga `about:blank` y dibuja la página de inicio encima, así
  Atrás vuelve a la página que se dejó. La tecla Atrás también funciona con la barra enfocada. Solo
  sin historial sale de la app.
- **Teclados de página**: cuando el WebView pierde el foco, se oculta el teclado que abrió un campo
  de la página, para que no se quede sobre la barra superior.
- **Foco en la barra de direcciones**: el foco solo muestra la URL (solo lectura, sin sesión de
  entrada de texto). OK empieza a editar y abre el teclado. Si no, el teclado del sistema reaparece
  cada vez que la barra recibe el foco desde la página. Al empezar a editar, la URL se vuelve a
  seleccionar tras un breve retraso, porque Gboard mueve el cursor al final al iniciar la sesión y
  escribir añadiría texto a la URL.
  - La lógica de movimiento (`CursorController`) es Kotlin puro con tests unitarios.
- **Atajos**: Atrás = página anterior (o salir del modo actual), Menú/OK mantenido = barra de
  direcciones y panel de Shields.
- **Teclado en pantalla**: el IME del sistema de la TV, para la barra de direcciones.
- Toda nuestra UI (Compose for TV) es navegable por foco.

## 7. Búsqueda

Brave Search es el motor por defecto (P5). En Ajustes → Motor de búsqueda el usuario puede elegir
otro motor predeterminado (roadmap 7.2). La elección se guarda en DataStore (`search_engine`, el
nombre del enum del proveedor; un valor desconocido cae a Brave Search) y se aplica a la siguiente
búsqueda, sin reiniciar. Cada proveedor es un nombre, un dominio y una plantilla de URL:

| Proveedor | Plantilla | Los resultados vienen de | |
|---|---|---|---|
| Brave Search | `https://search.brave.com/search?q=%s` | Índice propio | **por defecto** |
| DuckDuckGo | `https://duckduckgo.com/?q=%s` | Sobre todo Bing | seleccionable |
| Startpage | `https://www.startpage.com/do/search?q=%s` | Google, sin rastreo | seleccionable |
| Google | `https://www.google.com/search?q=%s` | Google | seleccionable |

`search/` resuelve lo que se escribe en la barra de direcciones (`SearchResolver`, roadmap 7.1):

| Lo escrito | Resultado | Ejemplos |
|---|---|---|
| Una URL `http://` o `https://` | Se abre tal cual | `http://example.com/a?b=1` |
| Un dominio público: etiquetas válidas y un dominio de nivel superior real, puerto opcional (1–65535), ruta, query o fragmento | `https://` + texto | `example.com`, `foo.bar`, `españa.com`, `пример.рф`, `example.com:8443/x` |
| Una dirección local | `http://` + texto (los dispositivos locales rara vez tienen TLS) | `localhost:3000`, `192.168.1.13`, `[::1]:8080`, `printer.local`, `nas.home.arpa` |
| Cualquier otra cosa | La plantilla del proveedor elegido | `file.txt`, `node.js`, `1.5`, `999.1.1.1`, `AC/DC`, `router`, `user@example.com`, texto con espacios |
| Texto que empieza por `?` | Siempre se busca (sin el `?`) | `? example.com` |

- Otros esquemas (`javascript:`, `file:`, `intent:`) se buscan, nunca se abren.
- Un dominio de nivel superior es real si está en la lista de la IANA, incluida como
  `app/src/main/resources/…/search/tlds.txt` (versión 2026111800, unas 1.440 entradas). Su primera
  línea da la versión. Para actualizarla, sustituye el archivo por
  `https://data.iana.org/TLD/tlds-alpha-by-domain.txt`. Los dominios de nivel superior
  internacionalizados se comparan en su forma ASCII (`рф` = `xn--p1ai`).
- Una sola palabra con una ruta (`AC/DC`) se busca: para abrir un nombre de intranet, escribe
  `http://router`.

Sin sugerencias en vivo (reglas C10).

## 8. Datos almacenados

| Dato | Dónde |
|---|---|
| Ajustes generales, proveedor de búsqueda elegido, sitios con Shields desactivados | Jetpack DataStore, un archivo `settings`: `ui_mode`, `accent`, `enabled_lists`, `search_engine` (`SettingsRepository`) y `shields_off_sites` (`SiteShieldsRepository`). Cada repositorio lo lee una vez al arrancar y después escribe sus cambios en orden |
| Listas descargadas, `resources.json` | `filesDir/lists/` |
| Motor serializado | `filesDir/engine.dat` (incluye la versión del crate; se regenera si cambia) |
| Contadores de bloqueo | En memoria por pestaña (`PageShields` en el estado del navegador). `ShieldsWebViewClient` los reinicia en cada petición del marco principal y cuenta cada bloqueo y redirección; una página restaurada de la caché de ida y vuelta, sin petición, empieza a cero. El total acumulado va a DataStore (aún no se guarda). |

Sin historial sincronizado ni cuentas.

## 9. Tecnologías

| Pieza | Elección | Por qué |
|---|---|---|
| Lenguaje de la app | Kotlin | Estándar en Android |
| UI | Jetpack Compose for TV (`androidx.tv`) | Componentes que entienden el foco para el mando |
| Web | Android System WebView + `androidx.webkit` | Ligero (T4). Ofrece `addDocumentStartJavaScript` y `WebMessageListener` |
| Motor de bloqueo | `adblock` (crates.io), versión 0.13.x | El motor de Brave (T5) |
| Features del crate | `default-features = false` + `embedded-domain-resolver`, `full-regex-handling` (es decir, los valores por defecto **menos** `single-thread`) | Hilos (C2). `resource-assembler` no hace falta: los recursos llegan como JSON |
| Puente | crate `jni` (JNI escrito a mano) | Llamadas por petición: JNI directo es más rápido que JNA/UniFFI |
| Compilación nativa | `cargo-ndk` + NDK de Android | Produce `jniLibs/<abi>/*.so` |
| Tareas periódicas | WorkManager | Actualizaciones de listas |
| Ajustes | Jetpack DataStore | Sencillo y asíncrono |
| Tests | JUnit + Robolectric (Kotlin), `cargo test` (Rust), tests instrumentados en la TV de referencia | |
| Verificación en dispositivo real | REA + `adb` en la TV de referencia | Sitios de prueba en [testing.md](../testing.md); herramientas en [resources/rea.md](../resources/rea.md) |
| CI | GitHub Actions | Compilación, tests y comprobación del tamaño del APK (T1) |

## 10. Dispositivo de referencia

JVC «COL 4K SMART TV» (fabricada por KTC), Android TV 11, SoC MediaTek, parche de seguridad de
noviembre de 2023. Todas las mediciones de rendimiento (T7) se toman en ella.

Leído por `adb` el 2026-11-08 (roadmap 0.5):

| Propiedad | Valor |
|---|---|
| Producto / dispositivo | `KTC_JVC_4K_COL_21V2` / `zhongshan` |
| Android | 11 (API 30), build `RTM7.230903.080`, parche de seguridad 2023-11-01 |
| SoC | MediaTek `m7632`, 4 × Cortex-A55 |
| ABI | **solo `armeabi-v7a`** (kernel y espacio de usuario de 32 bits; sin `arm64-v8a`) |
| RAM | 1,8 GB total, ~730 MB disponibles en reposo |
| Almacenamiento libre | ~770 MB en `/data` (partición de 4 GB) |
| Resolución de UI | 1920×1080 a 320 dpi (`xhdpi`) |
| WebView | `com.google.android.webview` 156.0.8078.47 (actualizado por Play Store; 157.0.8103.38 el 2026-11-18) |
| Features | `leanback`, `leanback_only`, `type.television`; sin pantalla táctil |

Consecuencias: la compilación `armeabi-v7a` es la que se prueba en esta TV; el poco almacenamiento
libre hace que el objetivo de tamaño del APK (T1) y el tamaño de las listas cacheadas importen.

Rareza: el WebView pinta su fondo sobre el contenido de Compose que se dibujó *antes* que él, incluso
fuera de sus límites. La UI que está junto al WebView (la barra superior) se dibuja después
(`zIndex`).

Conexión: activa *Opciones de desarrollador → Depuración USB* en la TV, luego
`adb connect <TV IP>:5555` y acepta el aviso de autorización en pantalla.

## 11. Dejado al implementador

- Nombres internos de clases y paquetes, siempre que sigan la estructura de §4.
- La curva de aceleración del cursor y los tiempos exactos de debounce.
- Los detalles de implementación de la UI, una vez aprobadas las maquetas (§12).

## 12. Diseño de UI

La UI es **nuestra**: el WebView solo renderiza páginas web, y todo lo que lo rodea (barra de
direcciones, panel de Shields, ajustes, cursor) está diseñado y construido por el proyecto. La UI de
Brave no se copia (P7) y Brave no tiene UI de TV; su *organización* (un icono de escudo junto a la
barra de direcciones que abre un panel con el contador y el interruptor) es una referencia.

**Sistema de diseño:** [design.md](../design.md) define la filosofía, los temas (oscuro y claro), el
acento púrpura, la tipografía, el espaciado, el foco, el movimiento, los iconos, los componentes y
los textos. Es la fuente de verdad para cada maqueta y pantalla.

**Proceso** (roadmap 2.0):
1. Las maquetas se hacen en **Claude Design** antes de cualquier código de UI, usando los tokens de
   [design.md](../design.md). Allí se puede usar **shadcn/ui** como base de componentes para las
   maquetas.
2. Las maquetas aprobadas (pantallas) se añaden a [design.md](../design.md).
3. La UI se implementa después en **Compose for TV** siguiendo esas maquetas. shadcn/ui es una
   biblioteca web (React): es solo una referencia visual y nunca se incluye en el APK.

**Restricciones de TV que deben cumplir las maquetas:**
- Legibles a 3 m: texto ≥ 18 sp.
- Un indicador de foco siempre visible; todos los elementos alcanzables con las flechas.
- ~5 % de margen de overscan en los bordes.
- Diseño a 1920×1080 (la TV renderiza la UI a 1080p y la escala a 4K).
- Sin marca de Brave (P7).
