package io.github.merytech1.tvshields.shields

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser and validator for procedural cosmetic filters (architecture §5.2c, roadmap 4.3).
 *
 * adblock-rust parses most `#?#` rules into structured operators, but selectors such as
 * `:has(p:has-text(Sponsored))` cannot be split by it and arrive as raw text in `hide_selectors`.
 * [parse] turns those into the same JSON shape as `procedural_actions`:
 *
 *     {"selector":[{"type":"css-selector","arg":".x"},{"type":"has-text","arg":"Ad"}],
 *      "action":{"type":"remove"}}
 *
 * Invalid or oversized filters return null: they are dropped instead of reaching the page.
 */
internal object ProceduralSelector {

    /** How a raw selector starts a procedural operator, after the colon. */
    private val MARKERS = listOf(
        ":has-text(", ":contains(", ":-abp-contains(", ":xpath(", ":upward(",
        ":matches-attr(", ":matches-css(", ":matches-css-before(", ":matches-css-after(",
        ":matches-path(", ":min-text-length(", ":remove(", ":style(", ":remove-attr(",
        ":remove-class(",
    )

    /** Operators emitted by adblock-rust; `has`/`not` are added by the raw parser. */
    private val OPERATORS = setOf(
        "css-selector", "has-text", "xpath", "upward", "matches-attr", "matches-css",
        "matches-css-before", "matches-css-after", "matches-path", "min-text-length",
        "has", "not",
    )

    private val ACTIONS = setOf("remove", "style", "remove-attr", "remove-class")
    private val SAFE_NAME = Regex("[A-Za-z0-9_:.\\-]+")
    private val FORBIDDEN_STYLE = listOf("url(", "expression(", "javascript:", "@import", "<", ">", "\\")

    private const val MAX_LENGTH = 2048
    private const val MAX_ARG_LENGTH = 1024
    private const val MAX_DEPTH = 8
    private const val MAX_OPERATORS = 32

    /** True when [selector] contains syntax that the browser cannot evaluate as plain CSS. */
    fun looksProcedural(selector: String): Boolean {
        // Runs for every selector of every page (~1,000), so it only looks where a marker can
        // start: at each colon. Searching the whole selector per marker cost ~55 ms a page on the TV.
        var colon = selector.indexOf(':')
        while (colon >= 0) {
            if (MARKERS.any { selector.regionMatches(colon, it, 0, it.length, ignoreCase = true) }) return true
            colon = selector.indexOf(':', colon + 1)
        }
        return false
    }

    /** Parses a raw selector; null when it has no procedural operator or is malformed. */
    fun parse(raw: String): JSONObject? {
        if (raw.length !in 1..MAX_LENGTH) return null
        return try {
            val sequence = Parser(raw).parseSequence(depth = 0, stopAtClose = false) ?: return null
            sequence.toFilter()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates one entry of `procedural_actions`. The engine is a trusted native library, but
     * the data arrives as JSON; bounds keep a malformed entry from reaching the page.
     */
    fun validate(raw: String): JSONObject? {
        if (raw.length > MAX_LENGTH) return null
        return try {
            val filter = JSONObject(raw)
            val selector = filter.optJSONArray("selector") ?: return null
            val normalized = normalizeSelector(selector, depth = 1) ?: return null

            val result = JSONObject().put(
                "selector",
                JSONArray().apply { normalized.forEach(::put) },
            )
            if (filter.has("action") && !filter.isNull("action")) {
                val action = normalizeAction(filter.optJSONObject("action") ?: return null) ?: return null
                result.put("action", action)
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates one operator array. When a `css-selector` argument still contains procedural
     * syntax (adblock-rust keeps `:has(p:has-text(...))` as text inside an action filter), the
     * argument is split into operators, so the page script can evaluate it.
     */
    private fun normalizeSelector(selector: JSONArray, depth: Int): List<JSONObject>? {
        if (depth > MAX_DEPTH || selector.length() !in 1..MAX_OPERATORS) return null
        val normalized = ArrayList<JSONObject>(selector.length())
        for (i in 0 until selector.length()) {
            val operator = selector.optJSONObject(i) ?: return null
            val type = operator.optString("type").lowercase()
            if (type !in OPERATORS) return null
            if (type == "css-selector") {
                val arg = operator.opt("arg") as? String ?: return null
                if (arg.length > MAX_ARG_LENGTH) return null
                if (looksProcedural(arg)) {
                    val sequence = Parser(arg).parseSequence(depth + 1, stopAtClose = false)
                        ?: return null
                    // A nested action would conflict with the filter's own action.
                    if (sequence.action != null || sequence.operators.isEmpty()) return null
                    normalized += sequence.operators
                } else {
                    if (arg.isBlank()) return null
                    normalized += JSONObject().put("type", type).put("arg", arg)
                }
            } else {
                val arg = normalizeArg(type, operator.opt("arg"), depth + 1) ?: return null
                normalized += JSONObject().put("type", type).put("arg", arg)
            }
            if (normalized.size > MAX_OPERATORS) return null
        }
        return normalized
    }

    private fun normalizeArg(type: String, value: Any?, depth: Int): Any? {
        if (depth > MAX_DEPTH) return null
        if (value is String) {
            if (value.length > MAX_ARG_LENGTH) return null
            return when (type) {
                "has-text" -> value
                "xpath", "upward", "matches-attr", "matches-css",
                "matches-css-before", "matches-css-after", "matches-path", "min-text-length" ->
                    value.takeIf(String::isNotBlank)
                else -> null
            }
        }
        if (value is JSONArray && (type == "has" || type == "not")) {
            val inner = normalizeSelector(value, depth + 1) ?: return null
            return JSONArray().apply { inner.forEach(::put) }
        }
        return null
    }

    private fun normalizeAction(action: JSONObject): JSONObject? {
        val type = action.optString("type").lowercase()
        if (type !in ACTIONS) return null
        if (type == "remove") return JSONObject().put("type", type)
        val arg = action.optString("arg").trim()
        if (arg.isEmpty() || arg.length > MAX_ARG_LENGTH) return null
        return when (type) {
            "style" -> if (isSafeStyle(arg)) JSONObject().put("type", type).put("arg", arg) else null
            else -> if (SAFE_NAME.matches(arg)) JSONObject().put("type", type).put("arg", arg) else null
        }
    }

    private fun isSafeStyle(style: String): Boolean {
        if (style.isBlank() || style.length > MAX_ARG_LENGTH) return false
        val lower = style.lowercase()
        return FORBIDDEN_STYLE.none(lower::contains)
    }

    private data class Sequence(val operators: List<JSONObject>, val action: JSONObject?) {
        /** True when nothing procedural was found: the text is still a plain CSS selector. */
        fun isPlainCss(): Boolean =
            action == null && operators.size == 1 &&
                operators[0].optString("type") == "css-selector"

        fun toFilter(): JSONObject? {
            if (operators.isEmpty() || operators.size > MAX_OPERATORS) return null
            // A raw selector that turned out to be plain CSS is left to the stylesheet.
            if (isPlainCss()) return null
            val selector = JSONArray()
            operators.forEach(selector::put)
            return JSONObject().put("selector", selector).apply {
                action?.let { put("action", it) }
            }
        }
    }

    /**
     * Scans a CSS selector and splits out the procedural pseudo-classes. Parentheses, brackets,
     * quotes and regular-expression literals are respected, so arguments survive untouched.
     */
    private class Parser(private val text: String) {
        private var index = 0

        fun parseSequence(depth: Int, stopAtClose: Boolean): Sequence? {
            if (depth > MAX_DEPTH) return null
            val operators = ArrayList<JSONObject>()
            val css = StringBuilder()
            var action: JSONObject? = null

            fun flushCss() {
                val selector = css.toString().trim()
                css.setLength(0)
                if (selector.isNotEmpty()) {
                    operators += JSONObject().put("type", "css-selector").put("arg", selector)
                }
            }

            while (index < text.length) {
                val c = text[index]
                if (stopAtClose && c == ')') {
                    flushCss()
                    return Sequence(operators, action)
                }
                when (c) {
                    '\\' -> {
                        css.append(c)
                        if (index + 1 < text.length) css.append(text[index + 1])
                        index += 2
                    }
                    '"', '\'' -> {
                        val start = index
                        copyString(c)
                        css.append(text, start, index)
                    }
                    '[', '(' -> {
                        val start = index
                        copyBalanced(c)
                        css.append(text, start, index)
                    }
                    ':' -> {
                        val start = index
                        val name = readPseudoName()
                        if (name == null) {
                            css.append(':')
                            continue
                        }
                        val isCall = index < text.length && text[index] == '('
                        if (!isCall) {
                            // Actions are always `:name(...)`; `:style` alone is not a CSS selector.
                            if (name in ACTIONS) return null
                            css.append(':').append(name)
                            continue
                        }
                        if (name !in PROCEDURAL_NAMES) {
                            css.append(':').append(name)
                            continue
                        }
                        if (name == "has" || name == "not") {
                            index++ // '('
                            val inner = parseSequence(depth + 1, stopAtClose = true) ?: return null
                            if (index >= text.length || text[index] != ')') return null
                            index++
                            if (inner.operators.isEmpty() || inner.isPlainCss()) {
                                css.append(text, start, index)
                            } else {
                                flushCss()
                                val arg = JSONArray()
                                inner.operators.forEach(arg::put)
                                operators += JSONObject().put("type", name).put("arg", arg)
                            }
                            continue
                        }

                        val arg = readArgument(regexAllowed = name != "xpath" && name != "upward")
                            ?: return null
                        if (name in ACTIONS) {
                            flushCss()
                            action = normalizeAction(
                                JSONObject().put("type", name).put("arg", arg.trim()),
                            ) ?: return null
                            return Sequence(operators, action)
                        }
                        flushCss()
                        val type = when (name) {
                            "contains", "-abp-contains" -> "has-text"
                            else -> name
                        }
                        operators += JSONObject().put("type", type).put("arg", arg)
                    }
                    else -> {
                        css.append(c)
                        index++
                    }
                }
            }

            // A nested sequence must stop at its closing parenthesis, never at the end.
            if (stopAtClose) return null
            flushCss()
            return Sequence(operators, action)
        }

        /** Reads `name` after a colon; null when the colon is not followed by an identifier. */
        private fun readPseudoName(): String? {
            index++ // ':'
            val start = index
            while (index < text.length) {
                val c = text[index]
                if (c.isLetterOrDigit() || c == '-' || c == '_') index++ else break
            }
            return if (index == start) null else text.substring(start, index).lowercase()
        }

        /** Copies a quoted string, including both quotes, and leaves [index] after it. */
        private fun copyString(quote: Char) {
            index++ // opening quote
            while (index < text.length) {
                val c = text[index]
                index++
                when {
                    c == '\\' -> if (index < text.length) index++
                    c == quote -> return
                }
            }
        }

        /** Copies a bracketed or parenthesised block, including both delimiters. */
        private fun copyBalanced(open: Char) {
            val close = if (open == '[') ']' else ')'
            var depth = 0
            while (index < text.length) {
                val c = text[index]
                when {
                    c == '\\' -> index += 2
                    c == '"' || c == '\'' -> copyString(c)
                    c == open -> {
                        depth++
                        index++
                    }
                    c == close -> {
                        depth--
                        index++
                        if (depth == 0) return
                    }
                    else -> index++
                }
            }
        }

        /** Reads the argument of a procedural pseudo-class, leaving [index] after its `)`. */
        private fun readArgument(regexAllowed: Boolean): String? {
            if (index >= text.length || text[index] != '(') return null
            index++
            val start = index
            var depth = 1
            var previous = '('
            while (index < text.length) {
                val c = text[index]
                when {
                    c == '\\' -> index += 2
                    c == '"' || c == '\'' -> {
                        copyString(c)
                        previous = 'x'
                    }
                    regexAllowed && c == '/' && canStartRegex(previous) -> {
                        if (!skipRegex()) return null
                        previous = '/'
                    }
                    c == '(' -> {
                        depth++
                        index++
                        previous = '('
                    }
                    c == ')' -> {
                        depth--
                        if (depth == 0) {
                            val arg = text.substring(start, index)
                            index++
                            return arg
                        }
                        index++
                        previous = ')'
                    }
                    else -> {
                        if (!c.isWhitespace()) previous = c
                        index++
                    }
                }
            }
            return null
        }

        private fun canStartRegex(previous: Char): Boolean =
            previous == '(' || previous == ':' || previous == ',' || previous == '='

        /** Skips a `/regex/flags` literal. */
        private fun skipRegex(): Boolean {
            index++ // opening '/'
            while (index < text.length) {
                val c = text[index]
                index++
                when {
                    c == '\\' -> if (index < text.length) index++
                    c == '/' -> {
                        while (index < text.length && text[index].isLetter()) index++
                        return true
                    }
                }
            }
            return false
        }
    }

    private val PROCEDURAL_NAMES = setOf(
        "has-text", "contains", "-abp-contains", "xpath", "upward", "matches-attr",
        "matches-css", "matches-css-before", "matches-css-after", "matches-path",
        "min-text-length", "has", "not", "remove", "style", "remove-attr", "remove-class",
    )
}
