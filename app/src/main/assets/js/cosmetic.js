/*
 * Generic cosmetic filtering (architecture §5.2b).
 *
 * Collects the class and id values of the page and sends them to the app. The app asks the
 * engine for generic hide selectors and calls __tvshieldsApplyCosmeticCss with the result.
 * Injected at document start (and once more as a C4 fallback); the guard makes the second run
 * harmless.
 */
(function () {
  'use strict';

  // Same-origin iframes also receive the document-start script; only the top document filters.
  if (window.top !== window) return;
  if (window.__tvshieldsCosmeticLoaded) return;
  window.__tvshieldsCosmeticLoaded = true;

  var MAX_TOKENS = 500;
  var MAX_TOKEN_LENGTH = 128;
  var FLUSH_DELAY_MS = 100;
  var STYLE_ID = 'tvshields-cosmetic-generic';
  var applied = Object.create(null);

  function newState() {
    return {
      seen: Object.create(null),
      seenCount: 0,
      pending: Object.create(null),
      pendingCount: 0
    };
  }

  var classes = newState();
  var ids = newState();
  var timer = null;

  function queue(state, value) {
    if (typeof value !== 'string' || !value) return;
    if (value.length > MAX_TOKEN_LENGTH) value = value.slice(0, MAX_TOKEN_LENGTH);
    if (state.seen[value] || state.pending[value]) return;
    if (state.seenCount + state.pendingCount >= MAX_TOKENS) return;
    state.pending[value] = true;
    state.pendingCount++;
  }

  function collect(element) {
    if (!element || element.nodeType !== 1) return;
    var list = element.classList;
    if (list) {
      for (var i = 0; i < list.length; i++) queue(classes, list[i]);
    }
    var id = element.id;
    if (typeof id === 'string' && id) queue(ids, id);
  }

  function scan(root) {
    if (!root) return;
    collect(root);
    if (!root.querySelectorAll) return;
    var elements = root.querySelectorAll('[class],[id]');
    for (var i = 0; i < elements.length; i++) collect(elements[i]);
  }

  function schedule(delay) {
    if (timer !== null) return;
    timer = setTimeout(flush, delay === undefined ? FLUSH_DELAY_MS : delay);
  }

  function markSeen(state, tokens) {
    for (var i = 0; i < tokens.length; i++) state.seen[tokens[i]] = true;
    state.seenCount += state.pendingCount;
    state.pending = Object.create(null);
    state.pendingCount = 0;
  }

  function flush() {
    timer = null;
    var classTokens = Object.keys(classes.pending);
    var idTokens = Object.keys(ids.pending);
    if (!classTokens.length && !idTokens.length) return;

    try {
      window.tvshieldsCosmetic.postMessage(JSON.stringify({ classes: classTokens, ids: idTokens }));
    } catch (e) {
      // The channel is not ready or the page is unloading: keep the tokens for a later retry.
      schedule(1000);
      return;
    }
    markSeen(classes, classTokens);
    markSeen(ids, idTokens);
  }

  // Called from the app with the selectors the engine matched.
  window.__tvshieldsApplyCosmeticCss = function (css) {
    if (!css || applied[css]) return;
    applied[css] = true;
    var style = document.getElementById(STYLE_ID);
    if (!style) {
      var root = document.head || document.documentElement;
      if (!root) return;
      style = document.createElement('style');
      style.id = STYLE_ID;
      root.appendChild(style);
    }
    style.appendChild(document.createTextNode(css + '\n'));
  };

  function onMutations(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var mutation = mutations[i];
      if (mutation.type === 'attributes') {
        collect(mutation.target);
      } else {
        for (var j = 0; j < mutation.addedNodes.length; j++) {
          var node = mutation.addedNodes[j];
          if (node.nodeType === 1) scan(node);
        }
      }
    }
    schedule();
  }

  function start() {
    var root = document.documentElement;
    if (!root) {
      document.addEventListener('readystatechange', start, { once: true });
      return;
    }
    scan(root);
    new MutationObserver(onMutations).observe(root, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'id']
    });
    schedule();
  }

  start();
  document.addEventListener('DOMContentLoaded', function () {
    scan(document);
    schedule();
  });
})();
