// Turns uBlock Origin and Brave resources into the `Resource` list adblock-rust loads
// (docs/resources/replacement-resources.md). Pure functions: generate.mjs does the I/O.

/** Permission bit for uBO's `requiresTrust` scriptlets; only trusted lists may use them. */
export const TRUSTED = 1;

const MIME_BY_EXTENSION = {
  css: 'text/css',
  gif: 'image/gif',
  html: 'text/html',
  js: 'application/javascript',
  json: 'application/json',
  mp3: 'audio/mp3',
  mp4: 'video/mp4',
  png: 'image/png',
  txt: 'text/plain',
  xml: 'text/xml',
};

/** MIME type of a redirect resource, from its file name. uBO's `empty` has no extension. */
export function mimeOf(fileName) {
  const dot = fileName.lastIndexOf('.');
  if (dot < 0) return 'text/plain';
  const mime = MIME_BY_EXTENSION[fileName.slice(dot + 1)];
  if (!mime) throw new Error(`Unknown MIME type for resource ${fileName}`);
  return mime;
}

const base64 = (data) => Buffer.from(data).toString('base64');

/**
 * uBO scriptlets (`builtinScriptlets`) in the function style adblock-rust understands: the content
 * is `function name(...) {...}`, the engine calls it with the filter's arguments, and the helper
 * functions are added through `dependencies`. Names ending in `.fn` are helpers only.
 */
export function scriptletResources(scriptlets) {
  return scriptlets.map((entry) => {
    const { name, fn } = entry;
    if (typeof fn !== 'function') throw new Error(`Scriptlet ${name} has no function`);
    const code = fn.toString();
    const helper = name.endsWith('.fn');
    // A scriptlet must start with `function name(`: adblock-rust calls it by that name, and
    // treats anything else as an old `{{1}}` template. A helper is pasted as is, so any
    // declaration that binds its name works (uBO also has `async function` and `class` helpers).
    const declaration = helper
      ? new RegExp(`^(?:(?:async\\s+)?function\\s*\\*?\\s*${fn.name}\\s*\\(|class\\s+${fn.name}\\b)`)
      : new RegExp(`^function\\s+${fn.name}\\s*\\(`);
    if (!declaration.test(code)) {
      throw new Error(`Scriptlet ${name} is not a ${helper ? 'named declaration' : 'plain function declaration'}`);
    }
    const resource = {
      name,
      aliases: entry.aliases ?? [],
      kind: helper ? { mime: 'fn/javascript' } : 'template',
      content: base64(code),
    };
    if (entry.dependencies?.length) resource.dependencies = [...entry.dependencies];
    if (entry.requiresTrust) resource.permission = TRUSTED;
    return resource;
  });
}

/**
 * uBO redirect resources (`redirect-resources.js`), read with [readFile]. Entries with `params`
 * need a URL built by uBO's extension pages, so they are skipped.
 */
export function redirectResources(redirects, readFile) {
  const out = [];
  for (const [name, details] of redirects) {
    if (details.params) continue;
    const resource = {
      name,
      aliases: [details.alias ?? []].flat(),
      kind: { mime: mimeOf(name) },
      content: base64(readFile(name)),
    };
    if (details.requiresTrust) resource.permission = TRUSTED;
    out.push(resource);
  }
  return out;
}

/**
 * Merges resource groups in priority order: a name taken by an earlier group drops the later
 * resource, and an alias taken by anything already added is removed (the engine rejects a
 * resource whose name or alias is already in use). Returns the list and what was dropped.
 */
export function merge(...groups) {
  const taken = new Set();
  const resources = [];
  const dropped = [];
  for (const group of groups) {
    for (const resource of group) {
      if (taken.has(resource.name)) {
        dropped.push(`resource ${resource.name}`);
        continue;
      }
      const aliases = resource.aliases.filter((alias) => {
        const free = alias !== resource.name && !taken.has(alias);
        if (!free && alias !== resource.name) dropped.push(`alias ${alias} of ${resource.name}`);
        return free;
      });
      taken.add(resource.name);
      aliases.forEach((alias) => taken.add(alias));
      resources.push({ ...resource, aliases });
    }
  }
  return { resources, dropped };
}

/** Every dependency must name a resource (not an alias), or the scriptlet would fail at injection. */
export function checkDependencies(resources) {
  const names = new Set(resources.map((r) => r.name));
  for (const resource of resources) {
    for (const dependency of resource.dependencies ?? []) {
      if (!names.has(dependency)) {
        throw new Error(`${resource.name} depends on missing resource ${dependency}`);
      }
    }
  }
}
