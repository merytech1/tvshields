package io.github.merytech1.tvshields.shields

/**
 * JavaScript that installs the cosmetic `<style>` element at document start (architecture §5.2a).
 * It updates the element when it already exists, so the C4 fallback can run after document start
 * without duplicating it.
 */
internal fun cosmeticScript(css: String): String = """
    (function () {
      var id = 'tvshields-cosmetic';
      var style = document.getElementById(id);
      function install() {
        var root = document.head || document.documentElement;
        if (!root) return false;
        if (!style) {
          style = document.createElement('style');
          style.id = id;
          root.appendChild(style);
        }
        style.textContent = ${jsString(css)};
        return true;
      }
      if (!install()) document.addEventListener('readystatechange', install, { once: true });
    })();
""".trimIndent()

/** Calls the generic script's own `<style>` element with selectors the engine just matched. */
internal fun applyGenericCosmeticCss(css: String): String =
    "(function(){var f=window.__tvshieldsApplyCosmeticCss;if(f)f(${jsString(css)});})();"

/** CSS and selectors come from filter lists: escape them so they cannot end the JS string. */
internal fun jsString(value: String): String = buildString {
    append('"')
    for (c in value) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}
