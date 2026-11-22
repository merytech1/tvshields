# tvshields

Un navegador ligero y de código abierto para **Android TV** que bloquea anuncios y rastreadores
al estilo de los *Shields* de Brave, pensado para usarse con el mando de la TV.

*[Read in English](README.md)*

> Descarga la última versión desde
> [GitHub Releases](https://github.com/merytech1/tvshields/releases/latest). Consulta el
> [plan de la próxima versión](#próxima-versión).

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

## Próxima versión

Estas fases continúan la numeración del [roadmap del proyecto](docs/es/roadmap.md).

| Fase | Tarea | Terminada cuando |
|---|---|---|
| 10 · Descargas y actualizaciones desde el TV | **10.1** Descargar archivos desde páginas web y mostrar el progreso. | Un archivo descargado con el mando aparece en la lista de descargas y se puede abrir desde el TV. |
| 10 · Descargas y actualizaciones desde el TV | **10.2** Añadir en Ajustes una consulta manual de actualizaciones en GitHub Releases. | Ajustes muestra la versión instalada y ofrece la APK oficial firmada si hay una versión más reciente. |
| 10 · Descargas y actualizaciones desde el TV | **10.3** Descargar la actualización y abrir el instalador de Android. | El usuario confirma la actualización en el TV y tvshields abre con la nueva versión y los ajustes anteriores. |
| 11 · Pestañas y ventanas | **11.1** Abrir, cambiar y cerrar pestañas con el mando. | Cada pestaña conserva su página y su estado de navegación al cambiar de pestaña. |
| 11 · Pestañas y ventanas | **11.2** Gestionar enlaces que solicitan una ventana nueva. | El enlace abre una vista de navegación aparte y el usuario puede volver a la página original. |
| 12 · Favoritos | **12.1** Añadir o quitar la página actual de favoritos. | El mando permite guardar o quitar una página y la lista de favoritos refleja el cambio. |
| 12 · Favoritos | **12.2** Volver a abrir páginas desde la lista de favoritos. | Los favoritos se conservan al volver a abrir la app y se pueden abrir con el mando. |
| 13 · Historial | **13.1** Registrar las páginas visitadas en el dispositivo y ordenarlas por visita más reciente. | El usuario puede volver a abrir una página reciente desde el historial. |
| 13 · Historial | **13.2** Borrar entradas individuales o todo el historial. | Las páginas borradas siguen fuera de la lista al volver a abrir la app. |
| 14 · Idiomas | **14.1** Traducir la interfaz a inglés, español, francés, portugués y alemán. | El navegador, las funciones nuevas y Ajustes muestran sus textos en los cinco idiomas. |
| 14 · Idiomas | **14.2** Añadir un selector de idioma en Ajustes. | El usuario puede cambiar de idioma con el mando y la selección se conserva al volver a abrir la app. |

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
