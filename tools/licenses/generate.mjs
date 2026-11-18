// Builds app/src/main/assets/third_party_licenses.txt: the notices of everything shipped inside
// the APK (rules L1). Run it after changing engine/Cargo.lock or the Android libraries:
//   node tools/licenses/generate.mjs
// Needs cargo (with the crates already fetched); it reads each crate's own license files.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../..');
const out = path.join(root, 'app/src/main/assets/third_party_licenses.txt');
const targets = ['aarch64-linux-android', 'armv7-linux-androideabi'];

// Every runtime dependency of the app module is Apache-2.0 (checked with
// `gradlew :app:dependencies --configuration releaseRuntimeClasspath`).
const androidLibraries = [
  ['AndroidX: Jetpack Compose, Compose for TV, Activity, Core, Lifecycle, DataStore, WorkManager, WebKit and their dependencies', 'The Android Open Source Project', 'https://developer.android.com/jetpack/androidx'],
  ['Kotlin standard library, kotlinx.coroutines, JetBrains annotations', 'JetBrains', 'https://github.com/JetBrains/kotlin'],
  ['Guava ListenableFuture', 'Google', 'https://github.com/google/guava'],
  ['Okio', 'Square', 'https://github.com/square/okio'],
  ['JSpecify annotations', 'The JSpecify authors', 'https://jspecify.dev'],
];

/** Crates linked into the engine library: normal dependencies of the Android targets, no proc-macros. */
function shippedCrates() {
  const crates = new Map();
  for (const target of targets) {
    const meta = JSON.parse(execFileSync('cargo', [
      'metadata', '--format-version', '1', '--offline', '--filter-platform', target,
      '--manifest-path', path.join(root, 'engine/Cargo.toml'),
    ], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }));
    const packages = new Map(meta.packages.map((p) => [p.id, p]));
    const nodes = new Map(meta.resolve.nodes.map((n) => [n.id, n]));
    const rootId = meta.resolve.root;
    const stack = [rootId];
    const seen = new Set();
    while (stack.length) {
      const id = stack.pop();
      if (seen.has(id)) continue;
      seen.add(id);
      const pkg = packages.get(id);
      if (pkg.targets.some((t) => t.kind.includes('proc-macro'))) continue;
      if (id !== rootId) crates.set(`${pkg.name} ${pkg.version}`, pkg);
      for (const dep of nodes.get(id).deps) {
        if (dep.dep_kinds.some((k) => k.kind === null)) stack.push(dep.pkg);
      }
    }
  }
  return [...crates.values()].sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));
}

const licenseFile = /^(licen[cs]e|copying|unlicense|notice)/i;

function licenseTexts(pkg) {
  const dir = path.dirname(pkg.manifest_path);
  const names = fs.readdirSync(dir).filter((n) => licenseFile.test(n) && fs.statSync(path.join(dir, n)).isFile());
  if (pkg.license_file && !names.includes(pkg.license_file)) names.push(pkg.license_file);
  return names.sort().map((name) => ({
    name,
    text: fs.readFileSync(path.join(dir, name), 'utf8').replace(/\r\n/g, '\n').trim(),
  }));
}

const normalize = (text) => text.replace(/\s+/g, ' ').trim();
const rule = (title, char = '=') => `${title}\n${char.repeat(title.length)}`;

const crates = shippedCrates();
const lines = [
  rule('Third-party software in tvshields'),
  '',
  'tvshields is open source under the Mozilla Public License 2.0 (MPL-2.0).',
  'Source code: https://github.com/merytech1/tvshields',
  '',
  'Filter lists and scriptlet resources are downloaded at runtime and are not part of the app.',
  'Their authors and licenses are listed on the About screen.',
  '',
  rule('Android libraries', '-'),
  '',
  'License: Apache-2.0 (full text in "Apache License 2.0" below).',
  '',
  ...androidLibraries.map(([name, author, url]) => `${name}\n  ${author}, ${url}`),
  '',
  rule('Rust crates (the blocking engine)', '-'),
  '',
  'adblock-rust is used unmodified, as published on crates.io.',
  '',
  ...crates.map((p) => `${p.name} ${p.version}\n  ${p.license ?? 'see license files'}${p.repository ? `, ${p.repository}` : ''}`),
  '',
];

// Full texts, each identical text printed once.
const printed = new Map();
let apache = null;
const texts = [];
const missing = [];
for (const pkg of crates) {
  const files = licenseTexts(pkg);
  if (!files.length) {
    // The crate ships no license file: its declared license applies, with the standard text.
    texts.push('', rule(`${pkg.name} ${pkg.version}`, '-'), '', `No license file in the published crate. License: ${pkg.license}.`);
    missing.push(pkg.name);
  }
  for (const { name, text } of files) {
    const label = `${pkg.name} ${pkg.version}: ${name}`;
    const key = normalize(text);
    if (!apache && /Apache License\s+Version 2\.0/.test(text) && text.length > 9000) apache = text;
    texts.push('', rule(label, '-'), '');
    texts.push(printed.has(key) ? `Same text as ${printed.get(key)}.` : text);
    if (!printed.has(key)) printed.set(key, label);
  }
}
if (!apache) throw new Error('No full Apache-2.0 text found among the crates');

lines.push(rule('Apache License 2.0', '-'), '', apache, '', rule('License texts of the Rust crates'), ...texts, '');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, lines.join('\n'));
if (missing.length) console.log(`Without license files: ${missing.join(', ')}`);
console.log(`${crates.length} crates, ${(fs.statSync(out).size / 1024).toFixed(0)} KB -> ${path.relative(root, out)}`);
