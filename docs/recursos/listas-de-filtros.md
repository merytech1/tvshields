# Listas de filtros

Las reglas que dicen qué bloquear. Usamos el **mismo catálogo que Brave**, publicado en
[`brave/adblock-resources/filter_lists/list_catalog.json`](https://github.com/brave/adblock-resources/blob/master/filter_lists/list_catalog.json) (MPL-2.0).

## Cómo las usamos

- Se descargan **en tiempo de ejecución** desde sus URLs originales. Nunca se empaquetan en el APK (reglas T6, L2, C9).
- Se actualizan cada 24 h con `ETag` / `If-Modified-Since`.
- En "Acerca de" se muestra cada lista con su autor y su licencia.
- El catálogo indica qué listas activa Brave por defecto (`default_enabled`). Copiamos esa selección.

## Activas por defecto

Entradas del catálogo con `default_enabled: true` (consultado el 2026-11-07):

| Entrada del catálogo | Fuentes principales | Licencia de las fuentes |
|---|---|---|
| Brave Default Adblock Filters | uBlock Origin (`filters*.txt`, `badware`, `unbreak`, `quick-fixes`…), EasyList, URLhaus (malware), y las listas de Brave `brave-specific`, `brave-android-specific`, `brave-social`, `brave-unbreak` y **`brave-sugarcoat.txt`** (las reglas que activan SugarCoat) | GPLv3 (uBO) / GPLv3 o CC BY-SA 3.0 (EasyList) / MPL-2.0 (Brave) / la de URLhaus |
| Brave Default Privacy Filters | EasyPrivacy, uBlock Origin `privacy.txt` | GPLv3 o CC BY-SA 3.0 / GPLv3 |
| Brave First Party Adblock Filters | `brave/adblock-lists` `brave-firstparty*.txt` | MPL-2.0 |
| Cookie notice blocker | Fanboy Cookiemonster, uBO `annoyances-cookies.txt` | CC BY 3.0 / GPLv3 |
| Mobile app promo blocker | Fanboy mobile notifications | CC BY 3.0 |

**Excluida:** "Brave iOS-Specific Filters", que es solo para iOS.

> Al implementar la fase 3.1 se toma la lista exacta de URLs del catálogo vigente y se fija en el código
> con fecha. Las fuentes de cada entrada pueden cambiar.

## Opcionales (en Ajustes)

"Annoying distractions blocker", "Newsletter popup blocker", "AI suggestions blocker", las
excepciones de *embeds* (Facebook, X, LinkedIn) y las **listas regionales**.

Para Colombia, el catálogo ofrece dos listas en español (`langs: ["es"]`):

| Entrada | Fuente |
|---|---|
| Spanish website ad blocker | EasyList Spanish (`easylistspanish.txt`) |
| Spanish and Portuguese website ad blocker | AdGuard Spanish/Portuguese (`filters.adtidy.org/…/9.txt`) |

Propuesta: activar por defecto las regionales que coincidan con el idioma de la TV, como hace Brave
con el idioma del navegador.

## Sintaxis (referencia rápida)

| Regla | Significado |
|---|---|
| `||ads.example.com^` | Bloquear todo ese dominio |
| `||cdn.example/ad.js$script,third-party` | Bloquear ese script si es de terceros |
| `@@||example.com/ads.js` | Excepción: no bloquear |
| `example.com##.banner-ad` | Ocultar el elemento con ese selector en ese sitio (1.2) |
| `##.ad-slot` | Ocultar en todos los sitios (cosmético genérico) |
| `example.com##+js(set-constant, x, false)` | Inyectar un scriptlet (1.3) |
| `||x.com/a.js$script,redirect=noopjs` | Sustituir por un recurso falso (1.3) |

Documentación de la sintaxis: https://github.com/gorhill/uBlock/wiki/Static-filter-syntax
