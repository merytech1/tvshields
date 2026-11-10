/*
 * Procedural cosmetic filtering (architecture §5.2c, roadmap 4.3).
 *
 * Applies the `procedural_actions` returned by adblock-rust plus the raw selectors it cannot
 * split (nested `:has(...:has-text(...))`). Adapted from Brave's procedural_filters.ts and
 * content_cosmetic.ts (MPL-2.0). Runs in the top frame only; the MutationObserver is debounced
 * and every pass has a time budget and a node cap (rules T7).
 */
(function () {
  'use strict';

  if (window.top !== window) return;
  if (window.__tvshieldsProceduralLoaded) return;
  window.__tvshieldsProceduralLoaded = true;

  var MAX_FILTERS = 32;
  var MAX_OPERATORS = 32;
  var MAX_DEPTH = 8;
  var MAX_NODES = 2500;
  var MAX_ADDED_NODES = 1200;
  var MAX_ANCESTORS = 6;
  var INITIAL_BUDGET_MS = 20;
  var MUTATION_BUDGET_MS = 8;
  var RETRY_DELAY_MS = 100;
  var FLUSH_DELAY_MS = 150;
  var STYLE_MARK = '__tvshieldsStyle';
  var HIDE_MARK = '__tvshieldsHidden';
  var QUEUED_MARK = '__tvshieldsQueued';

  var filters = [];
  var observer = null;
  var pending = [];
  var pendingCount = 0;
  var timer = null;
  var started = false;

  function now() {
    return window.performance && window.performance.now
      ? window.performance.now()
      : Date.now();
  }

  function isElement(node) {
    return !!node && node.nodeType === 1;
  }

  function asArray(list, limit) {
    var out = [];
    if (!list) return out;
    var length = Math.min(list.length || 0, limit || MAX_NODES);
    for (var i = 0; i < length; i++) {
      if (isElement(list[i])) out.push(list[i]);
    }
    return out;
  }

  function matches(element, selector) {
    try {
      return typeof element.matches === 'function'
        ? element.matches(selector)
        : element.msMatchesSelector(selector);
    } catch (e) {
      return false;
    }
  }

  function textOf(element) {
    var text = element.textContent;
    return typeof text === 'string' ? text : '';
  }

  /* ---------- arguments ---------- */

  /** Literal or `/regex/flags` matcher; regular expressions are case-sensitive unless flagged. */
  function valueMatcher(arg, exact) {
    if (typeof arg !== 'string') return null;
    // A leading slash alone (or without a closing slash) is a literal, not a regex: lists use
    // `matches-path(/)` for the site root.
    var regexEnd = arg.charAt(0) === '/' ? arg.lastIndexOf('/') : -1;
    if (regexEnd > 0) {
      var pattern = arg.slice(1, regexEnd);
      var flags = arg.slice(regexEnd + 1).replace(/[gy]/g, '');
      var regex;
      try {
        regex = new RegExp(pattern, flags);
      } catch (e) {
        return null;
      }
      return function (value) {
        regex.lastIndex = 0;
        return regex.test(value);
      };
    }
    if (arg === '') {
      return function (value) { return value.trim() === ''; };
    }
    if (exact) {
      return function (value) { return value === arg; };
    }
    return function (value) { return value.indexOf(arg) !== -1; };
  }

  /** Splits `name`, `name=value` or `"name"="value"`; regex values are allowed. */
  function keyValueMatcher(arg) {
    var isQuoted = arg.charAt(0) === '"';
    var terminator = isQuoted ? '"=' : '=';
    var from = isQuoted ? 1 : 0;
    var separator = arg.indexOf(terminator, from);
    var key;
    var valueStart;

    if (separator === -1) {
      if (isQuoted) {
        if (arg.charAt(arg.length - 1) !== '"') return null;
        key = arg.slice(1, arg.length - 1);
      } else {
        key = arg;
      }
      valueStart = -1;
    } else {
      key = arg.slice(from, separator);
      valueStart = separator + terminator.length;
    }

    var keyMatcher = valueMatcher(key, true);
    if (!keyMatcher) return null;
    var valueMatcherFunc = null;
    if (valueStart !== -1) {
      var quoted = arg.charAt(valueStart) === '"';
      var end = arg.length;
      if (quoted) {
        if (arg.charAt(arg.length - 1) !== '"') return null;
        valueStart += 1;
        end -= 1;
      }
      var value = arg.slice(valueStart, end);
      valueMatcherFunc = valueMatcher(value, true);
      if (!valueMatcherFunc) return null;
    }
    return { key: keyMatcher, value: valueMatcherFunc };
  }

  /* ---------- operators ---------- */

  function nextSibling(element) {
    var node = element.nextElementSibling;
    return isElement(node) ? node : null;
  }

  function siblings(element) {
    var parent = element.parentNode;
    return parent ? asArray(parent.children) : [];
  }

  function makeCssSelector(arg) {
    var selector = String(arg);
    var trimmed = selector.trim();
    if (!trimmed) return null;

    if (trimmed.charAt(0) === '+') {
      var plus = trimmed.slice(1).trim();
      return function (element) {
        var sibling = nextSibling(element);
        return sibling && matches(sibling, plus) ? [sibling] : [];
      };
    }
    if (trimmed.charAt(0) === '~') {
      var tilde = trimmed.slice(1).trim();
      return function (element) {
        var out = [];
        var all = siblings(element);
        for (var i = 0; i < all.length; i++) {
          if (all[i] !== element && matches(all[i], tilde)) out.push(all[i]);
        }
        return out;
      };
    }
    if (trimmed.charAt(0) === '>') {
      var child = trimmed.slice(1).trim();
      return function (element) {
        var out = [];
        var all = asArray(element.children);
        for (var i = 0; i < all.length; i++) {
          if (matches(all[i], child)) out.push(all[i]);
        }
        return out;
      };
    }
    return function (element) {
      return matches(element, selector) ? [element] : [];
    };
  }

  function makeHasText(arg) {
    var test = valueMatcher(String(arg));
    if (!test) return null;
    return function (element) {
      return test(textOf(element)) ? [element] : [];
    };
  }

  /**
   * Elements selected by a `:has(...)` argument relative to [element]. Handles the relative
   * combinators that nested list rules use (`:has(> h4 > span:has-text(...))`).
   */
  function relativeCandidates(element, selector) {
    var trimmed = selector.trim();
    if (!trimmed) return [];
    var parent = element.parentNode;

    if (trimmed.charAt(0) === '>') {
      try {
        return asArray(element.querySelectorAll(':scope ' + trimmed));
      } catch (e) {
        return [];
      }
    }

    if (trimmed.charAt(0) === '+' || trimmed.charAt(0) === '~') {
      if (!parent) return [];
      var rest = trimmed.slice(1).trim();
      if (!rest) return [];
      var all;
      try {
        all = asArray(parent.querySelectorAll(':scope > ' + rest));
      } catch (e) {
        return [];
      }
      var next = element.nextElementSibling;
      var out = [];
      for (var i = 0; i < all.length; i++) {
        var anchor = all[i];
        while (anchor && anchor.parentNode !== parent) anchor = anchor.parentNode;
        if (!isElement(anchor)) continue;
        if (trimmed.charAt(0) === '+') {
          if (anchor === next) out.push(all[i]);
          continue;
        }
        var node = element;
        while ((node = node.nextElementSibling)) {
          if (node === anchor) {
            out.push(all[i]);
            break;
          }
        }
      }
      return out;
    }

    try {
      return asArray(element.querySelectorAll(trimmed));
    } catch (e) {
      return [];
    }
  }

  function makeHas(arg) {
    if (Array.isArray(arg)) {
      var inner = compileSelector(arg, 1);
      if (!inner) return null;
      // The common case is `:has(css-selector...)`: let the browser find the candidates
      // instead of walking every descendant in JS.
      if (inner[0].type === 'css-selector') {
        var css = inner[0].arg;
        var rest = inner.slice(1);
        return function (element) {
          var candidates = relativeCandidates(element, css);
          if (!candidates.length) return [];
          return rest.length === 0 || applySelector(rest, candidates).length ? [element] : [];
        };
      }
      return function (element) {
        var candidates;
        try {
          candidates = asArray(element.querySelectorAll(':scope *'));
        } catch (e) {
          return [];
        }
        return applySelector(inner, candidates).length ? [element] : [];
      };
    }
    var selector = String(arg);
    return function (element) {
      return relativeCandidates(element, selector).length ? [element] : [];
    };
  }

  function makeNot(arg) {
    if (Array.isArray(arg)) {
      var inner = compileSelector(arg, 1);
      if (!inner) return null;
      return function (element) {
        return applySelector(inner, [element]).length ? [] : [element];
      };
    }
    var selector = String(arg);
    return function (element) {
      return matches(element, selector) ? [] : [element];
    };
  }

  function makeMatchesAttr(arg) {
    var matcher = keyValueMatcher(String(arg));
    if (!matcher) return null;
    return function (element) {
      var names = element.getAttributeNames
        ? element.getAttributeNames()
        : asArray(element.attributes).map(function (attribute) { return attribute.name; });
      for (var i = 0; i < names.length; i++) {
        if (!matcher.key(names[i])) continue;
        var value = element.getAttribute(names[i]);
        if (value === null) continue;
        if (!matcher.value || matcher.value(value)) return [element];
      }
      return [];
    };
  }

  function makeMatchesCss(arg, pseudo) {
    var text = String(arg);
    var separator = text.indexOf(':');
    if (separator === -1) return null;
    var property = text.slice(0, separator).trim();
    var expected = text.slice(separator + 1).trim();
    if (!property) return null;
    var expectedMatcher = valueMatcher(expected, true);
    if (!expectedMatcher) return null;
    return function (element) {
      var view;
      try {
        view = window.getComputedStyle(element, pseudo || null);
      } catch (e) {
        return [];
      }
      if (!view) return [];
      var value = view.getPropertyValue(property);
      return expectedMatcher(value) ? [element] : [];
    };
  }

  function makeMatchesPath(arg) {
    var test = valueMatcher(String(arg));
    if (!test) return null;
    var path = window.location.pathname + window.location.search;
    return function (element) {
      return test(path) ? [element] : [];
    };
  }

  function makeMinTextLength(arg) {
    var minimum = parseInt(String(arg), 10);
    if (isNaN(minimum) || minimum < 0) return null;
    return function (element) {
      return textOf(element).trim().length >= minimum ? [element] : [];
    };
  }

  function makeUpward(arg) {
    if (Array.isArray(arg)) {
      var inner = compileSelector(arg, 1);
      if (!inner) return null;
      return function (element) {
        var node = element;
        while (isElement(node)) {
          if (applySelector(inner, [node]).length) return [node];
          node = node.parentNode;
        }
        return [];
      };
    }
    var text = String(arg).trim();
    if (/^\d+$/.test(text)) {
      var steps = parseInt(text, 10);
      if (steps < 1 || steps > 255) return null;
      return function (element) {
        var node = element;
        for (var i = 0; i < steps && node; i++) node = node.parentNode;
        return isElement(node) ? [node] : [];
      };
    }
    return function (element) {
      var node = element;
      while (isElement(node)) {
        if (matches(node, text)) return [node];
        node = node.parentNode;
      }
      return [];
    };
  }

  function xpathElements(expression, context) {
    var out = [];
    try {
      var result = window.document.evaluate(
        expression,
        context,
        null,
        window.XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
        null
      );
      for (var i = 0; i < result.snapshotLength && out.length < MAX_NODES; i++) {
        var node = result.snapshotItem(i);
        if (isElement(node)) out.push(node);
      }
    } catch (e) {
      // Invalid XPath: the filter simply matches nothing.
    }
    return out;
  }

  function makeXpath(arg) {
    var expression = String(arg);
    return function (element) {
      return xpathElements(expression, element);
    };
  }

  function makeOperator(type, arg) {
    switch (type) {
      case 'css-selector': return makeCssSelector(arg);
      case 'has-text':
      case 'contains':
      case '-abp-contains': return makeHasText(arg);
      case 'has': return makeHas(arg);
      case 'not': return makeNot(arg);
      case 'matches-attr': return makeMatchesAttr(arg);
      case 'matches-css': return makeMatchesCss(arg, null);
      case 'matches-css-before': return makeMatchesCss(arg, '::before');
      case 'matches-css-after': return makeMatchesCss(arg, '::after');
      case 'matches-path': return makeMatchesPath(arg);
      case 'min-text-length': return makeMinTextLength(arg);
      case 'upward': return makeUpward(arg);
      case 'xpath': return makeXpath(arg);
      default: return null;
    }
  }

  function compileSelector(selector, depth) {
    if (!Array.isArray(selector) || selector.length === 0) return null;
    if (selector.length > MAX_OPERATORS || depth > MAX_DEPTH) return null;
    var compiled = [];
    for (var i = 0; i < selector.length; i++) {
      var operator = selector[i];
      if (!operator || typeof operator.type !== 'string') return null;
      var func = makeOperator(operator.type, operator.arg);
      if (!func) return null;
      compiled.push({ type: operator.type, arg: operator.arg, func: func });
    }
    return compiled;
  }

  /* ---------- applying ---------- */

  function applySelector(compiled, initNodes) {
    var index = 0;
    var nodes;
    var globalOperators = { 'matches-path': true };

    if (initNodes) {
      nodes = asArray(initNodes);
    } else {
      var first = compiled[0];
      if (first.type === 'css-selector') {
        try {
          nodes = asArray(window.document.querySelectorAll(first.arg));
        } catch (e) {
          nodes = [];
        }
        index = 1;
      } else if (first.type === 'xpath') {
        nodes = xpathElements(first.arg, window.document.documentElement);
        index = 1;
      } else {
        nodes = asArray(window.document.querySelectorAll('*'));
      }
    }

    for (; index < compiled.length && nodes.length; index++) {
      var func = compiled[index].func;
      if (globalOperators[compiled[index].type]) {
        // The condition depends on the URL, not on the element: one check keeps or drops all.
        if (func(nodes[0]).length === 0) nodes = [];
        continue;
      }
      var next = [];
      var seen = window.Set ? new Set() : null;
      for (var i = 0; i < nodes.length && next.length < MAX_NODES; i++) {
        var found = func(nodes[i]);
        for (var j = 0; j < found.length && next.length < MAX_NODES; j++) {
          var node = found[j];
          if (!isElement(node)) continue;
          if (seen) {
            if (seen.has(node)) continue;
            seen.add(node);
          }
          next.push(node);
        }
      }
      nodes = next;
    }
    return nodes;
  }

  function hide(element) {
    if (element[HIDE_MARK]) return;
    element[HIDE_MARK] = true;
    try {
      element.style.setProperty('display', 'none', 'important');
    } catch (e) {
      element.style.display = 'none';
    }
  }

  function performAction(element, action) {
    if (!action || action.type === 'hide') {
      hide(element);
    } else if (action.type === 'remove') {
      if (element.parentNode) element.parentNode.removeChild(element);
    } else if (action.type === 'style') {
      if (element[STYLE_MARK] === action.arg) return;
      element[STYLE_MARK] = action.arg;
      try {
        element.setAttribute('style', (element.getAttribute('style') || '') + ';' + action.arg);
      } catch (e) {
        // A broken style must not stop the other filters.
      }
    } else if (action.type === 'remove-attr') {
      if (element.hasAttribute(action.arg)) element.removeAttribute(action.arg);
    } else if (action.type === 'remove-class') {
      var tokens = action.arg.split(/\s+/);
      for (var i = 0; i < tokens.length; i++) {
        if (tokens[i] && element.classList.contains(tokens[i])) {
          element.classList.remove(tokens[i]);
        }
      }
    }
  }

  function compileAction(action) {
    if (!action || typeof action !== 'object') return { type: 'hide' };
    var type = String(action.type || '');
    if (type === 'remove') return { type: type };
    var arg = typeof action.arg === 'string' ? action.arg.trim() : '';
    if (!arg) return null;
    if (type === 'style' || type === 'remove-attr' || type === 'remove-class') {
      return { type: type, arg: arg };
    }
    return null;
  }

  function compileFilter(filter) {
    if (!filter || typeof filter !== 'object') return null;
    var selector = compileSelector(filter.selector, 0);
    if (!selector) return null;
    var action = compileAction(filter.action);
    if (!action) return null;
    return { selector: selector, action: action };
  }

  function execute(added, startIndex) {
    var budget = added ? MUTATION_BUDGET_MS : INITIAL_BUDGET_MS;
    var deadline = now() + budget;
    for (var i = startIndex; i < filters.length; i++) {
      if (now() > deadline) {
        window.setTimeout(function () { execute(added, i); }, RETRY_DELAY_MS);
        return;
      }
      try {
        var matchesFound = applySelector(filters[i].selector, added);
        for (var j = 0; j < matchesFound.length; j++) {
          performAction(matchesFound[j], filters[i].action);
        }
      } catch (e) {
        // One bad filter must not break the rest of the page.
      }
    }
  }

  /* ---------- mutations ---------- */

  function queueElement(element) {
    if (!isElement(element) || element[QUEUED_MARK]) return;
    if (pendingCount >= MAX_ADDED_NODES) return;
    element[QUEUED_MARK] = true;
    pending.push(element);
    pendingCount++;
  }

  function queueSubtree(element) {
    queueElement(element);
    var children;
    try {
      children = element.querySelectorAll('*');
    } catch (e) {
      return;
    }
    for (var i = 0; i < children.length && pendingCount < MAX_ADDED_NODES; i++) {
      queueElement(children[i]);
    }
  }

  function queueWithAncestors(element) {
    queueElement(element);
    var node = element.parentNode;
    for (var i = 0; isElement(node) && i < MAX_ANCESTORS; i++) {
      queueElement(node);
      node = node.parentNode;
    }
  }

  function onMutations(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var mutation = mutations[i];
      if (mutation.type === 'childList') {
        // Replacing `textContent` removes a text node and adds another, which the observer
        // reports as childList on the parent. The parent must be checked again so `:has-text`
        // filters see the new text.
        if (isElement(mutation.target)) queueWithAncestors(mutation.target);
        for (var j = 0; j < mutation.addedNodes.length; j++) {
          var node = mutation.addedNodes[j];
          if (isElement(node)) queueSubtree(node);
        }
      } else if (mutation.type === 'characterData') {
        if (isElement(mutation.target.parentNode)) queueWithAncestors(mutation.target.parentNode);
      } else if (mutation.type === 'attributes') {
        if (isElement(mutation.target)) queueWithAncestors(mutation.target);
      }
    }
    if (pendingCount) schedule();
  }

  function schedule() {
    if (timer !== null) return;
    timer = window.setTimeout(flush, FLUSH_DELAY_MS);
  }

  function flush() {
    timer = null;
    if (!pendingCount) return;
    var added = pending;
    for (var i = 0; i < added.length; i++) added[i][QUEUED_MARK] = false;
    pending = [];
    pendingCount = 0;
    execute(added, 0);
  }

  function observe() {
    if (observer) return;
    observer = new window.MutationObserver(onMutations);
    observer.observe(window.document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      characterData: true
    });
  }

  function start() {
    if (!window.document.documentElement) {
      window.document.addEventListener('readystatechange', start, { once: true });
      return;
    }
    execute(null, 0);
    observe();
    if (window.document.readyState === 'loading') {
      window.document.addEventListener('DOMContentLoaded', function () {
        execute(null, 0);
        if (pendingCount) schedule();
      }, { once: true });
    }
  }

  /**
   * Entry point used by the app. [json] is the array built by CosmeticFilter: structured
   * `procedural_actions` plus parsed raw selectors.
   */
  window.__tvshieldsStartProcedural = function (json) {
    if (started) return;
    started = true;
    var parsed;
    try {
      parsed = JSON.parse(json);
    } catch (e) {
      return;
    }
    if (!Array.isArray(parsed)) return;
    for (var i = 0; i < parsed.length && filters.length < MAX_FILTERS; i++) {
      var filter = compileFilter(parsed[i]);
      if (filter) filters.push(filter);
    }
    if (filters.length) start();
  };
})();
