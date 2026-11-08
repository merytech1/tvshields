# Recursos de reemplazo (scriptlets, redirects y SugarCoat)

Es la base de la función 1.3. Las listas solo **nombran** un recurso (`redirect=noopjs`,
`+js(set-constant, …)`). Para que el motor lo sirva o lo inyecte, necesita su contenido
cargado con `engine.use_resources(...)`.

## SugarCoat

SugarCoat es una técnica de investigación de Brave, publicada en el artículo *"SugarCoat:
Programmatically Generating Privacy-Preserving, Web-Compatible Resource Replacements for
Content Blocking"* (ACM CCS 2021). Sirve para scripts de rastreo que **no se pueden bloquear**
porque la página se rompe sin ellos. Analiza qué APIs sensibles toca el script (cookies,
almacenamiento, etc.) y genera automáticamente una **versión reescrita** que sigue funcionando
pero recibe datos falsos en esas APIs.

En la práctica llega como recursos `async-sugarcoat-<hash>.js` dentro de `brave/adblock-resources`,
y se activa con la lista `brave-sugarcoat.txt`. Para el motor es un `redirect=` más:

```
||google-analytics.com/analytics.js$script,important,domain=house.gov,redirect=async-sugarcoat-8a459c…
```

**Alcance real (2026-11-07):** `brave-sugarcoat.txt` tiene solo **2 reglas activas** (dell.com y
house.gov). El trabajo pesado contra los **detectores de bloqueadores** no lo hace SugarCoat, sino
los **scriptlets** de uBlock Origin (`set-constant`, `abort-on-property-read`, `json-prune`…),
escritos a mano por los mantenedores de las listas. Ambos se cargan igual en el motor.

## Fuentes

### 1. `brave/adblock-resources` (MPL-2.0)

- https://github.com/brave/adblock-resources
- `dist/resources.json`: **17 recursos** (2026-11-07), ya en el formato `Resource` que espera el motor
  (`name`, `aliases`, `kind`, `content` en base64). Incluye:
  - `async-sugarcoat-*.js` (SugarCoat),
  - arreglos de YouTube (`brave-youtube-*`, `brave-yt-sabr-fix.js`),
  - `de-amp.js`, `trusted_types.js`, `vaft-ublock-origin.js`, `video-swap-new-ublock-origin.js`, etc.
- **No** incluye los scriptlets estándar.

### 2. uBlock Origin (GPLv3)

- https://github.com/gorhill/uBlock
- `src/js/resources/`: scriptlets (`set-constant`, `abort-on-property-read`, `json-prune`,
  `no-setTimeout-if`…). Desde hace años son **módulos ES**.
- `src/web_accessible_resources/` + `redirect-resources.js`: redirects (`noopjs`, `noop.txt`,
  `1x1.gif`, `noopmp4-1s`, `google-ima`…).
- `adblock-rust` solo sabe leer el formato **antiguo** de scriptlets. Su documentación recomienda
  transformar el formato nuevo a `Resource` **con JavaScript**.

## Generador `tools/resources/`

Script de Node.js que se ejecuta **solo en CI** (fase 5.0):

1. Clona uBlock Origin y `adblock-resources` en **commits fijados**.
2. Carga los módulos de scriptlets de uBO y, para cada uno, genera un `Resource` de `kind: "template"`
   con sus alias y su código, más los recursos de `web_accessible_resources` con su MIME.
3. Une el resultado con `dist/resources.json` de Brave. Si un nombre se repite, gana Brave.
4. Valida el JSON cargándolo en `adblock-rust` (test en Rust).
5. Publica `resources.json` como asset de GitHub Releases, junto a `LICENSE-resources.txt`
   (GPLv3 + MPL-2.0) y los enlaces a los commits de origen.

Por qué así: los recursos de uBO son GPLv3. Publicados como **archivo aparte** que la app
descarga, no obligan a licenciar la app bajo GPL (reglas C11).

## Formato `Resource`

```json
{
  "name": "set-constant.js",
  "aliases": ["set-constant"],
  "kind": "template",
  "content": "<base64 del código con {{1}}, {{2}}…>"
}
```

`kind` es `"template"` para scriptlets con parámetros, o `{"mime": "application/javascript"}`
para redirects.
