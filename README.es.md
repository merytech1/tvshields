# tvshields

Un navegador ligero y de código abierto para **Android TV** que bloquea anuncios y rastreadores
al estilo de los *Shields* de Brave, pensado para usarse con el mando de la TV.

*[Read in English](README.md)*

> Descarga la última versión desde
> [GitHub Releases](https://github.com/merytech1/tvshields/releases/latest). Consulta el
> [roadmap](docs/es/roadmap.md) para ver lo que viene después.

## Qué hace

| Función | Qué significa para el usuario |
|---|---|
| **Bloqueo de red** | Las peticiones a servidores de anuncios y rastreo se cortan antes de descargarse, usando listas de filtros (EasyList, EasyPrivacy, uBlock Origin, Brave). |
| **Filtrado cosmético** | Desaparecen los huecos, banners vacíos y pantallas negras que dejan los anuncios bloqueados, y el contenido se recoloca. |
| **Reemplazo de scripts** | Los scripts que detectan bloqueadores o rastrean al usuario se neutralizan con *scriptlets* y recursos falsos inofensivos (incluidos los generados con *SugarCoat*, la técnica de Brave). |
| **Shields por sitio** | Activados por defecto. Se pueden desactivar para un sitio concreto si algo se rompe. Muestra un contador de elementos bloqueados. |
| **Búsqueda** | Brave Search por defecto; en Ajustes se puede elegir DuckDuckGo, Startpage o Google como motor predeterminado. |
| **Mando de la TV** | Todo funciona con las flechas, OK y Atrás. |

## Dónde funciona

Cualquier TV o dispositivo con **Android 7.0 o posterior**: Android TV, Google TV, Amazon Fire TV y
TV boxes. **No** funciona en Samsung (Tizen), LG (webOS) ni Roku.

## Descarga e instalación

La última versión está en [GitHub Releases](https://github.com/merytech1/tvshields/releases/latest):
un APK universal y otros más pequeños para dispositivos de 32 y 64 bits; si no estás seguro, usa el
universal. Instálalo con la app **Downloader** usando esta URL, que siempre descarga la última
versión:

```
https://github.com/merytech1/tvshields/releases/latest/download/tvshields.apk
```

Desde una memoria USB con un explorador de archivos, instala el APK que hayas descargado; o usa
`adb install <archivo>.apk`. El dispositivo debe permitir instalar apps de esa fuente, y el primer
arranque necesita internet para descargar las listas de filtros. Las sumas de comprobación, el
certificado y más detalle están en las [notas de la v0.1.1](docs/releases/v0.1.1.md) (en inglés).

## Tecnologías

| Capa | Tecnología |
|---|---|
| App | Kotlin, Android SDK (minSdk 24), Jetpack Compose for TV |
| Motor web | Android System WebView (Chromium del sistema) + `androidx.webkit` |
| Motor de bloqueo | [`adblock-rust`](https://github.com/brave/adblock-rust) de Brave (MPL-2.0), compilado como biblioteca nativa |
| Puente | Rust ↔ Kotlin mediante JNI (crate `jni`), compilado con `cargo-ndk` |
| Recursos de reemplazo | [`brave/adblock-resources`](https://github.com/brave/adblock-resources) (MPL-2.0, incluye SugarCoat) + scriptlets de [uBlock Origin](https://github.com/gorhill/uBlock) (GPLv3, se descargan aparte) |
| Listas de filtros | Catálogo de Brave: uBlock Origin, EasyList, EasyPrivacy, Brave, Fanboy (se descargan en tiempo de ejecución) |
| Pruebas en el dispositivo | [REA](https://github.com/morluto/rea) + `adb` |

Detalles en la [arquitectura](docs/es/architecture.md).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/es/rules.md](docs/es/rules.md) | Reglas y restricciones del proyecto, y la comprobación de que no entran en conflicto |
| [docs/es/roadmap.md](docs/es/roadmap.md) | Fases y tareas, cada una con su definición de terminada |
| [docs/es/architecture.md](docs/es/architecture.md) | Enfoques evaluados, arquitectura, módulos, flujos y diseño |
| [docs/design.md](docs/design.md) (en inglés) | Sistema de diseño: filosofía, temas, color, tipografía, foco y componentes |
| [docs/testing.md](docs/testing.md) (en inglés) | Sitios de prueba y cómo se comprueban los Shields en la TV de referencia |
| [docs/resources/](docs/resources/README.md) (en inglés) | Documentación específica de cada recurso externo que se usa |

## Aviso legal

tvshields es un proyecto independiente. No está afiliado a Brave Software ni usa sus marcas.
Reutiliza componentes de código abierto de Brave bajo sus licencias.

## Licencia

[MPL-2.0](LICENSE).
