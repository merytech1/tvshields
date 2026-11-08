# Fases y tareas

Cada tarea es una unidad de trabajo que se hace, se explica, se aprueba y se commitea
por separado (reglas W1). El estado se actualiza en este archivo al cerrar cada tarea.

Leyenda: ⬜ pendiente · 🔄 en curso · ✅ hecha

## Fase 0 — Fundamentos

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 0.1 | Crear repo, descripción y topics | Repo público con commit inicial | ✅ |
| 0.2 | Documentación base: README, reglas, arquitectura, fases, recursos | Aprobada y en `main` | 🔄 |
| 0.3 | Resolver decisiones pendientes (licencia, nombre, buscador) | Anotadas en `reglas.md` y `LICENSE` creado | ⬜ |
| 0.4 | Toolchain local: JDK 17, Android SDK/NDK, Rust + targets Android, `cargo-ndk` | `cargo ndk --version` y `./gradlew --version` funcionan; versiones anotadas en `docs/recursos/toolchain.md` | ⬜ |
| 0.5 | Conectar la TV por `adb` (depuración por red) | `adb devices` muestra la TV; REA lista sus paquetes | ⬜ |

## Fase 1 — Motor nativo

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 1.1 | Crate `engine/` que envuelve `adblock::Engine` (crear, comprobar petición, cosmético, recursos, serializar) | `cargo test` cubre bloqueo, excepción, redirect, cosmético y serialización | ⬜ |
| 1.2 | Exportar funciones JNI | Tests de Rust para la capa de conversión de tipos | ⬜ |
| 1.3 | Compilar para `armeabi-v7a`, `arm64-v8a`, `x86_64` con `cargo-ndk` | `.so` generados y tamaño anotado (C3) | ⬜ |

## Fase 2 — Navegador mínimo

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 2.1 | Proyecto Android (Kotlin, Compose for TV, minSdk 24, Leanback launcher + banner) | La app aparece en el inicio de la TV y abre una pantalla | ⬜ |
| 2.2 | WebView a pantalla completa + barra de direcciones | Se navega a una URL escrita con el teclado de la TV | ⬜ |
| 2.3 | Cursor virtual y scroll con flechas | Se puede hacer clic en enlaces de cualquier página solo con el control | ⬜ |
| 2.4 | Botón Atrás, recargar, inicio | Navegación básica completa con el control | ⬜ |
| 2.5 | Cargar el `.so` desde Kotlin (smoke test JNI) | Test instrumentado llama al motor y obtiene un resultado | ⬜ |

## Fase 3 — Shields: bloqueo de red (1.1)

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 3.1 | `FilterListRepository`: descarga del catálogo por defecto, almacenamiento, ETag | Test con servidor falso: descarga, no repite si no cambió | ⬜ |
| 3.2 | Construcción del motor + `engine.dat` (T8) | El segundo arranque carga desde caché; tiempo medido en la TV | ⬜ |
| 3.3 | `RequestTypeGuesser` | Tests unitarios por cada regla de §5.1 | ⬜ |
| 3.4 | `shouldInterceptRequest` → motor → bloquear/permitir | Página de prueba con anuncios conocidos: peticiones bloqueadas | ⬜ |
| 3.5 | Primer arranque y caso sin conexión (C1) | Se comporta como describe C1 | ⬜ |
| 3.6 | `ListUpdateWorker` cada 24 h con intercambio atómico | Test: actualización sin cortar navegación en curso | ⬜ |

## Fase 4 — Shields: ocultación cosmética (1.2)

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 4.1 | Inyección de `hide_selectors` al inicio del documento (con alternativa C4) | Los huecos de anuncios desaparecen en las páginas de prueba | ⬜ |
| 4.2 | `cosmetic.js` + `WebMessageListener` + `hidden_class_id_selectors` | Anuncios que aparecen tras cargar también se ocultan | ⬜ |
| 4.3 | Filtros procedurales (`procedural_actions`) en JS propio | Reglas `:has-text`, `:upward`, etc. de las listas funcionan en casos de prueba | ⬜ |
| 4.4 | Medir impacto (T7) | Navegación sin tirones apreciables en la TV de referencia | ⬜ |

## Fase 5 — Shields: reemplazo de scripts (1.3)

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 5.0 | Generador `tools/resources/`: uBO (transformado) + Brave → `resources.json`, versiones fijadas | El JSON resultante se carga en `adblock-rust` sin errores (test en Rust) | ⬜ |
| 5.1 | Descargar y cargar `resources.json` (`use_resources`) | El motor reconoce `noopjs`, `set-constant` y los `async-sugarcoat-*` | ⬜ |
| 5.2 | Servir `redirect` como respuesta en `shouldInterceptRequest` | Test con regla `redirect=` | ⬜ |
| 5.3 | Inyectar `injected_script` (scriptlets) al inicio del documento | Sitio de prueba con detector de bloqueadores no muestra el aviso | ⬜ |

## Fase 6 — Interfaz de Shields

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 6.1 | Panel de Shields: estado, contador por página | Se ve el número de bloqueos de la página actual | ⬜ |
| 6.2 | Interruptor por sitio (P3, C5) | Apagar Shields en un dominio lo recuerda y recarga sin bloqueo | ⬜ |
| 6.3 | Ajustes: listas opcionales, forzar actualización | Activar una lista la descarga y la aplica | ⬜ |
| 6.4 | "Acerca de": licencias y créditos (L1, L2) | Todas las licencias y listas visibles | ⬜ |

## Fase 7 — Búsqueda

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 7.1 | Resolución texto → URL o búsqueda | Tests unitarios de casos límite (`localhost`, IPs, `foo.bar`, frases) | ⬜ |
| 7.2 | Selector de buscador en Ajustes | El cambio persiste y se aplica | ⬜ |

## Fase 8 — Verificación en la TV real (con REA)

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 8.1 | Lista de sitios de prueba representativos (noticias, video, streaming) | Documentada en `docs/pruebas.md` | ⬜ |
| 8.2 | Observar cada sitio con REA (sin bloqueo) para inventariar las peticiones de anuncios | Inventario por sitio | ⬜ |
| 8.3 | Comparar con tvshields en la TV (logs por `adb`) | Tabla de bloqueado / escapado por sitio | ⬜ |
| 8.4 | Rendimiento: arranque, RAM, tiempo por página | Medidas anotadas. Cumple T7 | ⬜ |

## Fase 9 — Publicación

| # | Tarea | Hecho cuando | Estado |
|---|---|---|---|
| 9.1 | CI en GitHub Actions: Rust + APK + tests | Verde en cada PR | ⬜ |
| 9.2 | Firma de release (clave fuera del repo, en secretos de GitHub) | APK firmado reproducible desde CI | ⬜ |
| 9.3 | Control de tamaño en CI (falla si > 100 MB; aviso si > 25 MB) | Comprobado (T1) | ⬜ |
| 9.4 | Release v0.1.0 en GitHub Releases (universal + por ABI) | Instalable en la TV de referencia desde el archivo publicado | ⬜ |
