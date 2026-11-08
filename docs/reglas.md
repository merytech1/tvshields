# Reglas y restricciones

Toda decisión de diseño o de código debe cumplir estas reglas. Cada regla tiene un
identificador para poder citarla en issues, commits y revisiones (p. ej. "cumple T1").

Al final está la **verificación de cruces**: los pares de reglas que podrían
contradecirse y cómo se resuelve cada uno. Si una regla nueva choca con otra,
se añade aquí antes de implementar nada.

## 1. Producto (P)

| ID | Regla |
|---|---|
| P1 | Navegador de **uso general**: el bloqueo debe funcionar en cualquier sitio, no estar optimizado para uno concreto. |
| P2 | Los **Shields están activos por defecto** al instalar. |
| P3 | Los Shields se pueden **apagar por sitio** (dominio), y esa preferencia se recuerda. |
| P4 | Los Shields cubren tres capacidades: **bloqueo de red** (1.1), **ocultación cosmética** (1.2) y **reemplazo de scripts** (1.3). |
| P5 | El buscador es **configurable**; por defecto DuckDuckGo. |
| P6 | Todo se puede usar **solo con el control remoto** (flechas, OK, Atrás). Sin ratón ni pantalla táctil. |
| P7 | Nombre y logo **propios**. Prohibido usar "Brave", su león o su identidad visual. |

## 2. Técnicas (T)

| ID | Regla |
|---|---|
| T1 | El APK pesa **como máximo 100 MB** (límite duro). Meta: ≤ 25 MB. |
| T2 | `minSdk 24` (Android 7.0). |
| T3 | Se compila para **`armeabi-v7a` y `arm64-v8a`**. Muchas TVs tienen CPU de 64 bits con Android de 32 bits. `x86_64` solo para el emulador, nunca en el APK de release. |
| T4 | Motor web: **Android System WebView**. No se empaqueta Chromium ni GeckoView. |
| T5 | Motor de bloqueo: **`adblock-rust` sin modificar**, como dependencia de crates.io. Si algún día se modifica, se publican esos cambios (exigencia MPL-2.0). |
| T6 | Las **listas de filtros no van dentro del APK**: se descargan de sus fuentes originales y se actualizan solas. |
| T7 | **Bajo consumo de RAM y CPU**: referencia TV con ~2 GB de RAM y MediaTek de gama media. El bloqueo no puede notarse al navegar. |
| T8 | El motor compilado se **guarda en caché** (`Engine::serialize`) para no reprocesar las listas en cada arranque. |
| T9 | Ninguna petición sale de la app salvo la navegación de la persona usuaria y la descarga de listas/recursos. **Cero telemetría ni analítica.** |
| T10 | La función de la WebView que se use y no exista en todas las versiones se **detecta en tiempo de ejecución** (`WebViewFeature.isFeatureSupported`) y tiene alternativa. |

## 3. Legales (L)

| ID | Regla |
|---|---|
| L1 | Se respeta la MPL-2.0 de `adblock-rust` y `adblock-resources`: avisos de licencia incluidos en el APK y en la pantalla "Acerca de". |
| L2 | Las listas se descargan en tiempo de ejecución (T6). Cada una se acredita con su nombre, autor y licencia en "Acerca de". |
| L3 | No se descompila ni copia código cerrado. Todo lo que tomamos de Brave es open source. REA se usa para **observar** sitios y probar nuestra app, no para extraer código ajeno. |
| L4 | El bloqueo actúa solo en el dispositivo de quien usa la app. No se distribuye contenido de terceros. |

## 4. Proceso (W)

| ID | Regla |
|---|---|
| W1 | **Una tarea a la vez**: se termina, se explica, se propone el mensaje de commit y se espera aprobación. |
| W2 | Commits en **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `refactor:`…). |
| W3 | **Sin `Co-Authored-By`** ni atribuciones a IA en commits ni PRs. |
| W4 | **`main` no se toca sin permiso explícito.** El trabajo va en ramas (`feat/…`, `docs/…`) y entra por PR. |
| W5 | Código, identificadores y nombres de archivo en **inglés**; documentación y comentarios en **español**. |
| W6 | Toda decisión de arquitectura nueva se documenta en `docs/` en el mismo PR que la introduce. |
| W7 | Cada tarea define su **criterio de "hecho"** verificable (ver [fases](fases.md)). |

## 5. Verificación de cruces

| # | Reglas | Posible choque | Resolución |
|---|---|---|---|
| C1 | T6 × P2 | Si las listas se descargan y no vienen en el APK, en el **primer arranque sin internet** los Shields no tienen reglas. | El primer arranque descarga las listas antes de abrir la primera página (con pantalla de progreso). Sin conexión, se muestra el aviso "Shields sin listas", y la página no carga hasta que haya listas o la persona elija continuar sin protección. |
| C2 | T7 × WebView | `adblock-rust` es más rápido con el feature `single-thread`, pero `shouldInterceptRequest` se llama **desde varios hilos**. | Se compila **sin** `single-thread` (motor `Send + Sync`), y una sola instancia se comparte entre hilos. Si las mediciones de la fase 8 muestran contención, se evalúa un pool de lectores. |
| C3 | T1 × T3 | Dos ABIs implican dos `.so` del motor dentro del APK. | Estimado: 3–6 MB por ABI, lejos de 100 MB. Además, en Releases se publica un APK por ABI además del universal. Se mide en cada release (fase 9). |
| C4 | T2 × T10 | Con `minSdk 24`, la WebView puede ser antigua y no soportar `DOCUMENT_START_SCRIPT` ni `WEB_MESSAGE_LISTENER`. | Se detecta en tiempo de ejecución. Sin `DOCUMENT_START_SCRIPT`, se inyecta en `onPageStarted` (menos fiable para 1.3) y se avisa en Ajustes que conviene actualizar "Android System WebView". |
| C5 | P3 × T5 | El motor no sabe de "sitios con Shields apagados". | El interruptor por sitio se resuelve en Kotlin **antes** de llamar al motor; el motor no se modifica. |
| C6 | T9 × T6 | Actualizar listas implica peticiones automáticas. | Están permitidas por T9 de forma explícita: solo a las URLs del catálogo de listas, y se muestran en "Acerca de". Nada más sale sin acción de la persona usuaria. |
| C7 | P1 × T4 | Algunos anuncios (p. ej. ciertos de YouTube) llegan **dentro del mismo flujo de video** y no se pueden cortar por red. | Fuera de alcance garantizado: se cubre lo que cubran los scriptlets de las listas (1.3). Se documenta como limitación conocida, no como bug. |
| C8 | P6 × T4 | La WebView está hecha para táctil/ratón; muchas páginas no se pueden usar con flechas. | Cursor virtual movido con las flechas que genera eventos táctiles sobre la WebView, más un modo de navegación por foco. Ver [arquitectura §6](arquitectura.md#6-control-remoto). |
| C9 | L2 × C1 | Empaquetar una lista "de emergencia" para el primer arranque reabriría el problema de licencias (GPL / CC BY-SA). | No se empaqueta ninguna lista; C1 se resuelve con la descarga inicial. |
| C10 | T9 × P5 | El buscador recibe lo que se escribe en la barra. | Solo cuando la persona busca (acción explícita). No hay sugerencias en vivo por defecto. |
| C11 | L2 × T6 × 1.3 | Los scriptlets de uBlock Origin (GPLv3) son imprescindibles para 1.3, pero meterlos en el APK obligaría a licenciar la app bajo GPL. | No van en el APK. Un generador en CI arma `resources.json` (uBO + Brave) y lo publica como archivo independiente en GitHub Releases, con su licencia GPLv3 y enlace al código fuente de uBO. La app lo descarga en tiempo de ejecución, igual que las listas (T6). |

## Decisiones pendientes

Corresponden a la responsable del proyecto:

1. **Licencia de tvshields.** Opciones compatibles con lo que usamos: MPL-2.0 (la misma de Brave), MIT o Apache-2.0.
2. **Nombre visible de la app** en la TV. `tvshields` es el nombre del repo.
3. **Confirmar DuckDuckGo** como buscador por defecto (P5).
