# tvshields

Navegador ligero y open source para **Android TV** que bloquea anuncios y rastreadores
al estilo de los *Shields* de Brave, pensado para manejarse con el control remoto.

> **Estado:** en diseño. Todavía no hay APK. Ver [fases y tareas](docs/fases.md).

## Qué hace

| Función | Qué significa para quien lo usa |
|---|---|
| **Bloqueo de red** | Las peticiones a servidores de anuncios y rastreo se cortan antes de descargarse, usando listas de filtros (EasyList, EasyPrivacy, uBlock Origin, Brave). |
| **Ocultación cosmética** | Los huecos, banners vacíos y pantallas negras que deja un anuncio bloqueado desaparecen; el contenido se reacomoda. |
| **Reemplazo de scripts** | Los scripts que detectan bloqueadores o que rastrean se neutralizan con *scriptlets* y recursos falsos inofensivos (incluidos los generados con *SugarCoat*, la técnica de Brave). |
| **Shields por sitio** | Activos por defecto; se pueden apagar solo para una página si algo se rompe. Contador de elementos bloqueados. |
| **Búsqueda** | Buscador configurable desde la barra de direcciones. |
| **Control remoto** | Todo se maneja con las flechas, OK y Atrás. |

## Dónde funciona

Cualquier televisor o caja con **Android 7.0 o superior**: Android TV, Google TV, Amazon Fire TV,
cajas Android. **No** funciona en Samsung (Tizen), LG (webOS) ni Roku.

## Tecnologías

| Capa | Tecnología |
|---|---|
| App | Kotlin, Android SDK (minSdk 24), Jetpack Compose for TV |
| Motor web | Android System WebView (Chromium del sistema) + `androidx.webkit` |
| Motor de bloqueo | [`adblock-rust`](https://github.com/brave/adblock-rust) de Brave (MPL-2.0), compilado a librería nativa |
| Puente | Rust ↔ Kotlin vía JNI (crate `jni`), compilado con `cargo-ndk` |
| Recursos de reemplazo | [`brave/adblock-resources`](https://github.com/brave/adblock-resources) (MPL-2.0, incluye SugarCoat) + scriptlets de [uBlock Origin](https://github.com/gorhill/uBlock) (GPLv3, descargados aparte) |
| Listas de filtros | Catálogo de Brave: uBlock Origin, EasyList, EasyPrivacy, Brave, Fanboy (descargadas en tiempo de ejecución) |
| Pruebas en dispositivo | [REA](https://github.com/morluto/rea) + `adb` |

Detalle en [arquitectura](docs/arquitectura.md).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/reglas.md](docs/reglas.md) | Reglas y restricciones del proyecto, y verificación de que no se contradicen |
| [docs/arquitectura.md](docs/arquitectura.md) | Enfoques evaluados, arquitectura, módulos, flujos y diseño |
| [docs/fases.md](docs/fases.md) | Fases y tareas, con criterio de "hecho" |
| [docs/recursos/](docs/recursos/) | Documentación específica de cada recurso externo que usamos |

## Aviso

tvshields es un proyecto independiente. No está afiliado a Brave Software ni usa su marca.
Reutiliza componentes open source de Brave bajo sus licencias.

## Licencia

Pendiente de decidir (ver [reglas](docs/reglas.md#decisiones-pendientes)).
