# adblock-rust

- **Qué es:** el motor de los Shields de Brave, publicado como librería independiente.
- **Repositorio:** https://github.com/brave/adblock-rust
- **Crate:** [`adblock`](https://crates.io/crates/adblock), versión **0.13.5** (2026-10-28). Documentación: https://docs.rs/adblock
- **Licencia:** MPL-2.0. Es por archivo: podemos usarlo en una app con otra licencia. Si modificamos
  sus archivos, esos cambios se publican (reglas T5, L1).

## Qué hace

Bloqueo de red, filtrado cosmético, reemplazo de recursos, sintaxis *hosts* y las extensiones
de sintaxis de uBlock Origin. Se compila a código nativo, así que funciona en Android vía NDK.

## API que usamos

| Función | Para qué | Shields |
|---|---|---|
| `FilterSet::new(debug)` + `add_filter_list(texto, ParseOptions::default())` | Juntar las listas descargadas | — |
| `Engine::new_with_filter_set(set)` | Construir el motor | — |
| `engine.check_network_request(&Request::new(url, source_url, request_type, method))` → `BlockerResult` | Decidir sobre cada petición | 1.1 |
| `BlockerResult.should_block` / `.redirect` / `.rewritten_url` | Bloquear, servir recurso falso o reescribir la URL | 1.1, 1.3 |
| `engine.url_cosmetic_resources(url)` → `UrlSpecificResources` | Selectores a ocultar, procedurales, excepciones, scriptlets y `generichide` | 1.2, 1.3 |
| `engine.hidden_class_id_selectors(classes, ids, exceptions)` | Cosmético genérico sobre class/id nuevos | 1.2 |
| `engine.use_resources(resources)` | Cargar scriptlets y redirects | 1.3 |
| `engine.serialize()` / `engine.deserialize(bytes)` | Caché del motor compilado | T8 |
| `engine.enable_tags` / `disable_tags` | Activar reglas opcionales por etiqueta | futuro |

Ejemplo mínimo (del repo oficial):

```rust
use adblock::{Engine, lists::{FilterSet, ParseOptions}, request::Request};

let mut filter_set = FilterSet::new(false);
filter_set.add_filter_list(&rules, ParseOptions::default());
let engine = Engine::new_with_filter_set(filter_set);

let request = Request::new(
    "http://example.com/-advertisement-icon.",
    "http://example.com/helloworld",
    "image",
    "",
).unwrap();
let result = engine.check_network_request(&request);
```

## Features de Cargo

| Feature | Por defecto | Nuestra elección |
|---|---|---|
| `embedded-domain-resolver` | sí | **sí**: resolución de dominios incluida |
| `full-regex-handling` | sí | **sí** |
| `single-thread` | sí | **no**: el motor debe ser `Send + Sync` porque la WebView llama desde varios hilos (reglas C2) |
| `css-validation` | no | no (más tamaño) |
| `content-blocking` | no | no (solo es para iOS) |
| `resource-assembler` | no | no: el generador de recursos va en JS (ver [recursos-de-reemplazo.md](recursos-de-reemplazo.md)) |
| `debug-info` | no | solo en builds de depuración |

```toml
[dependencies]
adblock = { version = "0.13", default-features = false, features = ["embedded-domain-resolver", "full-regex-handling"] }
```

## Limitaciones y notas

- `procedural_actions` llega como JSON: el motor **no** ejecuta los filtros procedurales.
  Hay que implementarlos en JS dentro de la página (fase 4.3).
- `get_csp_directives` existe, pero no podemos inyectar cabeceras CSP en el documento principal
  de la WebView. Queda fuera de alcance.
- El formato de `serialize` cambia entre versiones del crate: `engine.dat` guarda la versión
  y se regenera si no coincide.
