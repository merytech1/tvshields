// Builds resources.json from pinned uBlock Origin and Brave sources (roadmap 5.0, rules C11).
// Runs in CI. Usage: node generate.mjs [--cache <dir>] [--out <dir>]
//
// It imports uBO's scriptlet modules to read their functions, so it runs uBO code: only the
// commits pinned in sources.json, and only in CI or a disposable checkout.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';

import { checkDependencies, merge, redirectResources, scriptletResources } from './convert.mjs';

const here = import.meta.dirname;
const { values: options } = parseArgs({
  options: {
    cache: { type: 'string', default: path.join(here, '.cache') },
    out: { type: 'string', default: path.join(here, 'dist') },
  },
});
const sources = JSON.parse(fs.readFileSync(path.join(here, 'sources.json'), 'utf8'));

const git = (dir, ...args) => execFileSync('git', ['-C', dir, ...args], { encoding: 'utf8' }).trim();

/** Checks out exactly the pinned commit (shallow), reusing the cache when it already matches. */
function checkout(name, source) {
  const dir = path.join(options.cache, name);
  if (fs.existsSync(path.join(dir, '.git')) && git(dir, 'rev-parse', 'HEAD') === source.commit) return dir;
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  git(dir, 'init', '-q');
  git(dir, 'fetch', '-q', '--depth', '1', source.repository, source.commit);
  git(dir, 'checkout', '-q', '--detach', 'FETCH_HEAD');
  const head = git(dir, 'rev-parse', 'HEAD');
  if (head !== source.commit) throw new Error(`${name}: expected ${source.commit}, got ${head}`);
  return dir;
}

const ublockDir = checkout('ublock', sources.ublock);
const braveDir = checkout('brave', sources.brave);

const importFrom = (dir, file) => import(pathToFileURL(path.join(dir, file)).href);
const { builtinScriptlets } = await importFrom(ublockDir, 'src/js/resources/scriptlets.js');
const { default: redirects } = await importFrom(ublockDir, 'src/js/redirect-resources.js');
const warDir = path.join(ublockDir, 'src', 'web_accessible_resources');

const brave = JSON.parse(fs.readFileSync(path.join(braveDir, 'dist', 'resources.json'), 'utf8'))
  .map((resource) => ({ ...resource, aliases: resource.aliases ?? [] }));
const scriptlets = scriptletResources(builtinScriptlets);
const files = redirectResources(redirects, (name) => fs.readFileSync(path.join(warDir, name)));

// Brave first: on a name collision, Brave wins (docs/resources/replacement-resources.md).
const { resources, dropped } = merge(brave, scriptlets, files);
checkDependencies(resources);

fs.mkdirSync(options.out, { recursive: true });
fs.writeFileSync(path.join(options.out, 'resources.json'), JSON.stringify(resources));

const license = (dir, source) => fs.readFileSync(path.join(dir, source.licenseFile), 'utf8');
fs.writeFileSync(path.join(options.out, 'LICENSE-resources.txt'), `resources.json for tvshields

This file is not part of the tvshields app. It is built from:

- uBlock Origin ${sources.ublock.tag}, ${sources.ublock.license}
  ${sources.ublock.repository}/tree/${sources.ublock.commit}
  Scriptlets (src/js/resources/) and redirect resources (src/web_accessible_resources/).
- brave/adblock-resources, ${sources.brave.license}
  ${sources.brave.repository}/tree/${sources.brave.commit}
  dist/resources.json.

The source of every resource is available at the commits above.

================================================================================
uBlock Origin license
================================================================================

${license(ublockDir, sources.ublock)}
================================================================================
brave/adblock-resources license
================================================================================

${license(braveDir, sources.brave)}`);

console.log(`resources.json: ${resources.length} resources ` +
  `(Brave ${brave.length}, uBO scriptlets ${scriptlets.length}, uBO redirects ${files.length})`);
dropped.forEach((item) => console.log(`  dropped ${item} (name already in use)`));
