# Reglas y restricciones

Toda decisión de diseño y toda línea de código deben cumplir estas reglas. Cada regla tiene un ID
para poder citarla en issues, commits y revisiones (por ejemplo, «cumple T1»).

Al final está la **comprobación de conflictos**: los pares de reglas que podrían contradecirse y
cómo se resuelve cada caso. Si una regla nueva choca con otra existente, se añade allí antes de
implementar nada.

## 1. Producto (P)

| ID | Regla |
|---|---|
| P1 | Navegador **de propósito general**: el bloqueo debe funcionar en cualquier sitio, no estar ajustado a uno concreto. |
| P2 | **Los Shields están activados por defecto** tras la instalación. |
| P3 | Los Shields se pueden **desactivar por sitio** (dominio), y esa preferencia se recuerda. |
| P4 | Los Shields cubren tres capacidades: **bloqueo de red** (1.1), **filtrado cosmético** (1.2) y **reemplazo de scripts** (1.3). |
| P5 | Motor de búsqueda por defecto: **Brave Search**. El usuario puede elegir otro predeterminado en Ajustes entre **DuckDuckGo, Startpage y Google**. |
| P6 | Todo se puede usar **solo con el mando de la TV** (flechas, OK, Atrás). Sin ratón ni pantalla táctil. |
| P7 | Nombre y logo **propios**: la app se llama **tvshields**. Está prohibido usar «Brave», su león o su identidad visual. Se permite listar Brave Search como opción de búsqueda (es solo una URL). |

## 2. Técnicas (T)

| ID | Regla |
|---|---|
| T1 | El APK pesa **como máximo 100 MB** (límite duro). Objetivo: ≤ 25 MB. |
| T2 | `minSdk 24` (Android 7.0). |
| T3 | Compilado solo para **`armeabi-v7a` y `arm64-v8a`**. Muchas TV tienen una CPU de 64 bits ejecutando Android de 32 bits (la TV de referencia entre ellas). `x86_64` no se compila: en este proyecto no hay emulador, las pruebas se hacen en la TV real. |
| T4 | Motor web: **Android System WebView**. No se incluye Chromium ni GeckoView. |
| T5 | Motor de bloqueo: **`adblock-rust` sin modificar**, como dependencia de crates.io. Si alguna vez se modifica, esos cambios se publican (requisito de MPL-2.0). |
| T6 | **Las listas de filtros no se incluyen en el APK**: se descargan de sus fuentes originales y se actualizan automáticamente. |
| T7 | **Poco uso de RAM y CPU**: TV de referencia con ~2 GB de RAM y un SoC MediaTek de gama media. El bloqueo no debe notarse al navegar. |
| T8 | El motor compilado se **cachea** (`Engine::serialize`) para no reprocesar las listas en cada arranque. |
| T9 | Ninguna petición sale de la app aparte de la navegación del propio usuario y las descargas de listas y recursos. **Cero telemetría o analítica.** |
| T10 | Cualquier función del WebView que no esté disponible en todas las versiones se **detecta en tiempo de ejecución** (`WebViewFeature.isFeatureSupported`) y tiene un fallback. |

## 3. Aspectos legales (L)

| ID | Regla |
|---|---|
| L1 | Se respeta la MPL-2.0 de `adblock-rust` y `adblock-resources`: los avisos de licencia se incluyen en el APK y en la pantalla «Acerca de». |
| L2 | Las listas se descargan en tiempo de ejecución (T6). Cada una se acredita con su nombre, autor y licencia en la pantalla «Acerca de». |
| L3 | No se descompila ni se copia código de código cerrado. Todo lo que se toma de Brave es código abierto. REA se usa para **observar** sitios y probar nuestra propia app, no para extraer código de terceros. |
| L4 | El bloqueo solo actúa en el dispositivo del propio usuario. No se redistribuye contenido de terceros. |

## 4. Proceso (W)

| ID | Regla |
|---|---|
| W1 | **Una tarea a la vez**: se termina, se explica, se propone un mensaje de commit y se espera la aprobación. |
| W2 | Los commits siguen **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `refactor:`…). |
| W3 | **Sin `Co-Authored-By`** ni atribución a IA en commits o PRs. |
| W4 | Se trabaja en ramas (`feat/…`, `fix/…`, `docs/…`, `chore/…`) creadas desde **`develop`** y se integran en `develop` mediante un PR (solo merge commit). **`main` está bloqueada** hasta que termine el proyecto; entonces se fusiona `develop` en `main` con permiso explícito. Lo imponen los rulesets de GitHub `protect-develop` y `lock-main`. |
| W5 | Código, identificadores, nombres de archivo, **comentarios de código** y **documentación del repositorio en inglés**. Los comentarios son breves y solo donde el código no se explica solo. La versión en español de la documentación se produce al final del proyecto (roadmap 9.5). |
| W6 | Toda decisión de arquitectura nueva se documenta en `docs/` en el mismo PR que la introduce. |
| W7 | Cada tarea define una **definición de terminada** verificable (véase el [roadmap](roadmap.md)). |

## 5. Comprobación de conflictos

| # | Reglas | Conflicto potencial | Resolución |
|---|---|---|---|
| C1 | T6 × P2 | Si las listas se descargan en vez de incluirse, los Shields no tienen reglas en un **primer arranque sin internet**. | El primer arranque descarga las listas antes de abrir la primera página, tras una pantalla de progreso. Sin conexión se muestra un aviso de «los Shields no tienen listas» y las páginas no cargan hasta que haya listas o el usuario elija continuar sin protección. |
| C2 | T7 × WebView | `adblock-rust` es más rápido con la función `single-thread`, pero `shouldInterceptRequest` se llama **desde varios hilos**. | Se compila **sin** `single-thread` (el motor es `Send + Sync`) y se comparte una sola instancia entre hilos. Si las mediciones de la fase 8 muestran contención, se evalúa un pool de lectores. |
| C3 | T1 × T3 | Dos ABIs implican dos archivos `.so` del motor dentro del APK. | Medidos en 2,3 MB (`armeabi-v7a`) y 3,4 MB (`arm64-v8a`) el 2026-11-13 (`opt-level = 3`, roadmap 4.4), muy por debajo de 100 MB. Las releases publican además un APK por ABI junto al universal. Se mide en cada release (fase 9). |
| C4 | T2 × T10 | Con `minSdk 24`, el WebView puede ser antiguo y carecer de `DOCUMENT_START_SCRIPT` o `WEB_MESSAGE_LISTENER`. | Se detecta en tiempo de ejecución. Sin `DOCUMENT_START_SCRIPT`, la inyección ocurre en `onPageStarted` (menos fiable para 1.3), y Ajustes sugiere actualizar «Android System WebView». |
| C5 | P3 × T5 | El motor no sabe nada de «sitios con Shields desactivados». | El interruptor por sitio se resuelve en Kotlin **antes** de llamar al motor; el motor no se modifica. |
| C6 | T9 × T6 | Actualizar las listas implica peticiones automáticas. | T9 lo permite explícitamente: solo a las URL del catálogo de listas y al recurso `resources.json` de la release (C11), y se muestran en la pantalla «Acerca de». Nada más sale sin una acción del usuario. |
| C7 | P1 × T4 | Algunos anuncios (por ejemplo, algunos de YouTube) llegan **dentro del propio flujo de vídeo** y no se pueden cortar a nivel de red. | No está garantizado en el alcance: se cubre solo en la medida en que lo cubran los scriptlets de las listas (1.3). Documentado como limitación conocida, no como error. |
| C8 | P6 × T4 | El WebView está pensado para tacto/ratón; muchas páginas no se pueden usar con las flechas. | Un cursor virtual que se mueve con las flechas y genera eventos táctiles en el WebView, más un modo de navegación por foco. Véase la [arquitectura §6](../architecture.md#6-tv-remote) (en inglés). |
| C9 | L2 × C1 | Incluir una lista «de emergencia» para el primer arranque reabriría el problema de licencias (GPL / CC BY-SA). | No se incluye ninguna lista; C1 se resuelve con la descarga inicial. |
| C10 | T9 × P5 | El motor de búsqueda recibe lo que se escribe en la barra de direcciones. | Solo cuando el usuario busca (acción explícita). Sin sugerencias en vivo por defecto. |
| C11 | L2 × T6 × 1.3 | Los scriptlets de uBlock Origin (GPLv3) son esenciales para 1.3, pero incluirlos en el APK obligaría a licenciar la app bajo GPL. | No están en el APK. Un generador de CI construye `resources.json` (uBO + Brave) y lo publica como archivo independiente en GitHub Releases, con su licencia GPLv3 y un enlace al código de uBO. La app lo descarga en tiempo de ejecución, como las listas (T6). |

## Decisiones

| Fecha | Decisión |
|---|---|
| 2026-11-07 | Licencia: **MPL-2.0**, la misma que los componentes de Brave (`LICENSE`). |
| 2026-11-07 | Nombre visible de la app: **tvshields** (P7). |
| 2026-11-07 | Búsqueda: **Brave Search** por defecto; DuckDuckGo, Startpage y Google seleccionables en Ajustes (P5). |
| 2026-11-08 | Se descarta `x86_64` (T3): no se usa emulador y la TV de referencia solo ejecuta `armeabi-v7a`. |
| 2026-11-08 | UI: personalidad sobria y fiable, con la soberanía como idea de marca; el espectador elige el modo (oscuro por defecto, o claro) y el acento (púrpura por defecto, o esmeralda), con un solo acento en pantalla a la vez; marca y logo púrpura; fuente del sistema Roboto. Véase [design.md](../design.md) (en inglés). |
