# Roadmap: fases y tareas

Cada tarea es una unidad de trabajo que se termina, explica, aprueba y confirma por separado
(reglas W1). El estado se actualiza en este archivo al cerrar cada tarea.

Leyenda: ⬜ pendiente · 🔄 en curso · ✅ hecha

## Fase 0 — Fundamentos

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 0.1 | Crear el repo, la descripción y los temas | Repo público con un commit inicial | ✅ |
| 0.2 | Documentación base: README, reglas, arquitectura, roadmap, recursos | Aprobada y fusionada en `main` | ✅ |
| 0.3 | Resolver las decisiones pendientes (licencia, nombre, motor de búsqueda) | Registradas en `rules.md`; `LICENSE` creado | ✅ |
| 0.4 | Toolchain local en Windows, instalado en `D:`: JDK 17+, Android SDK/NDK, Rust (`windows-gnu`) + targets de Android, `cargo-ndk`, `gh` | `cargo ndk --version` y `./gradlew --version` funcionan; versiones registradas en `docs/resources/toolchain.md` | ✅ |
| 0.5 | Conectar la TV por `adb` (depuración por red) | `adb devices` muestra la TV; REA lista sus paquetes | ✅ |

## Fase 1 — Motor nativo

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 1.1 | Crate `engine/` que envuelve `adblock::Engine` (crear, comprobar petición, cosmético, recursos, serializar) | `cargo test` cubre bloqueo, excepción, redirección, cosmético y serialización | ✅ |
| 1.2 | Exportar funciones JNI | Tests de Rust para la capa de conversión de tipos | ✅ |
| 1.3 | Compilar para `armeabi-v7a` y `arm64-v8a` con `cargo-ndk` | Archivos `.so` generados y su tamaño registrado (C3) | ✅ |

## Fase 2 — Navegador mínimo

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 2.0 | Maquetas de UI en Claude Design (shadcn/ui permitido): navegador, barra de direcciones, panel de Shields, ajustes, selector de buscador | Sistema de diseño en `docs/design.md` (hecho); prompts en `docs/mockup-prompts.md` (hecho); maquetas aprobadas, guardadas en `docs/mockups/` y referenciadas desde `design.md` | ✅ |
| 2.1 | Proyecto Android (Kotlin, Compose for TV, MVVM, minSdk 24, launcher Leanback + banner) | La app aparece en la pantalla de inicio de la TV y abre una pantalla | ✅ |
| 2.2 | WebView a pantalla completa + barra de direcciones | Navega a una URL escrita con el teclado de la TV | ✅ |
| 2.3 | Cursor virtual y desplazamiento con flechas | Se puede hacer clic en enlaces de cualquier página solo con el mando | ✅ |
| 2.4 | Atrás, recargar, inicio | Navegación básica totalmente usable con el mando | ✅ |
| 2.5 | Cargar el `.so` desde Kotlin (prueba de humo JNI) | Un test instrumentado llama al motor y obtiene un resultado | ✅ |

## Fase 3 — Shields: bloqueo de red (1.1)

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 3.1 | `FilterListRepository`: descargar el catálogo por defecto, almacenamiento, ETag | Test con un servidor falso: descarga y no vuelve a descargar si no ha cambiado | ✅ |
| 3.2 | Construcción del motor + `engine.dat` (T8) | El segundo arranque carga desde la caché; tiempo medido en la TV | ✅ |
| 3.3 | `RequestTypeGuesser` | Tests unitarios para cada regla de la arquitectura §5.1 | ✅ |
| 3.4 | `shouldInterceptRequest` → motor → bloquear/permitir | Página de prueba con anuncios conocidos: peticiones bloqueadas | ✅ |
| 3.5 | Primer arranque y caso sin conexión (C1) | Se comporta como se describe en C1 | ✅ |
| 3.6 | `ListUpdateWorker` cada 24 h con intercambio atómico | Test: actualizar sin interrumpir la navegación en curso | ✅ |

## Fase 4 — Shields: filtrado cosmético (1.2)

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 4.1 | Inyectar `hide_selectors` al inicio del documento (con fallback C4) | Los huecos de anuncios desaparecen en las páginas de prueba | ✅ |
| 4.2 | `cosmetic.js` + `WebMessageListener` + `hidden_class_id_selectors` | Los anuncios que aparecen después de la carga también se ocultan | ✅ |
| 4.3 | Filtros procedurales (`procedural_actions`) en nuestro propio JS | Las reglas `:has-text`, `:upward`, etc. de las listas funcionan en los casos de prueba | ✅ |
| 4.4 | Medir el impacto (T7) | Sin tirones apreciables al navegar en la TV de referencia | ✅ |

## Fase 5 — Shields: reemplazo de scripts (1.3)

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 5.0 | Generador `tools/resources/`: uBO (transformado) + Brave → `resources.json`, versiones fijadas | El JSON resultante carga en `adblock-rust` sin errores (test de Rust) | ✅ |
| 5.1 | Descargar y cargar `resources.json` (`use_resources`) | El motor reconoce `noopjs`, `set-constant` y los recursos `async-sugarcoat-*` | ✅ |
| 5.2 | Servir `redirect` como respuesta en `shouldInterceptRequest` | Test con una regla `redirect=` | ✅ |
| 5.3 | Inyectar `injected_script` (scriptlets) al inicio del documento | Un sitio de prueba con detector de bloqueadores no muestra su aviso | ✅ |

## Fase 6 — UI de Shields

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 6.1 | Panel de Shields: estado, contador por página | El número de elementos bloqueados en la página actual es visible | ✅ |
| 6.2 | Interruptor por sitio (P3, C5) | Desactivar Shields para un dominio se recuerda y recarga sin bloquear | ✅ |
| 6.3 | Ajustes: listas opcionales, actualización forzada, apariencia (modo y acento, diseño §2) | Activar una lista la descarga y la aplica; los cambios de modo y acento se aplican al momento y persisten | ✅ |
| 6.4 | «Acerca de»: licencias y créditos (L1, L2) | Todas las licencias y listas son visibles | ✅ |

## Fase 7 — Búsqueda

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 7.1 | Resolver texto → URL o búsqueda | Tests unitarios para casos límite (`localhost`, IPs, `foo.bar`, frases) | ✅ |
| 7.2 | Selector de motor de búsqueda por defecto en Ajustes (Brave Search, DuckDuckGo, Startpage, Google) | Brave Search en una instalación nueva; un cambio persiste y se aplica a la siguiente búsqueda | ✅ |

## Fase 8 — Verificación en la TV real (con REA)

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 8.1 | Lista de sitios de prueba representativos (noticias, vídeo, streaming) | Documentada en [`docs/testing.md`](../testing.md) (en inglés) | ✅ |
| 8.2 | Observar cada sitio con REA (sin bloqueo) para inventariar las peticiones de anuncios | Inventario por sitio ([testing §3](../testing.md#3-ad-inventory-without-blocking-roadmap-82)) (en inglés) | ✅ |
| 8.3 | Comparar con tvshields en la TV (logs por `adb`) | Tabla de bloqueadas / fugadas por sitio ([testing §4](../testing.md#4-blocked-and-leaked-requests-roadmap-83)) (en inglés) | ✅ |
| 8.4 | Rendimiento: tiempo de arranque, RAM, tiempo por página | Mediciones registradas. Cumple T7 ([testing §5](../testing.md#5-performance-roadmap-84)) (en inglés) | ✅ |

## Fase 9 — Publicación

| # | Tarea | Terminada cuando | Estado |
|---|---|---|---|
| 9.1 | CI en GitHub Actions: Rust + APK + tests | En verde en cada PR ([toolchain: CI](../resources/toolchain.md#ci-github-actions)) (en inglés) | ✅ |
| 9.2 | Firma de la release (clave fuera del repo, en secrets de GitHub) | APK firmado y reproducible desde CI ([toolchain: firma de la release](../resources/toolchain.md#release-signing)) (en inglés) | ✅ |
| 9.3 | Comprobación de tamaño en CI (falla si > 100 MB; avisa si > 25 MB) | Verificado (T1) ([toolchain: CI](../resources/toolchain.md#ci-github-actions)) (en inglés) | ✅ |
| 9.4 | v0.1.0 en GitHub Releases (universal + por ABI) | Instalable en la TV de referencia desde el archivo publicado ([toolchain: publicación](../resources/toolchain.md#publishing-a-release)) (en inglés) | ✅ |
| 9.5 | Documentación en español del repositorio (reglas W5) | Versión en español del README y la documentación publicada y enlazada desde el README en inglés | ⬜ |
