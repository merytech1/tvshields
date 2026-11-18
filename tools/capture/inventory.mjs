// Summarizes captures from requests.mjs: per site, how many requests go to ad, tracking and
// consent services (docs/testing.md, roadmap 8.2).
//
//   node tools/capture/inventory.mjs <capture.json>...
//
// The categories come from the hand-made table in classify.mjs, not from the filter lists, so
// the inventory does not depend on what Shields blocks. Third-party hosts not in the table are
// listed as "other" for review.

import fs from 'node:fs';
import path from 'node:path';
import { category, pageLoad, site } from './classify.mjs';

const rows = [];
for (const file of process.argv.slice(2)) {
  // The recording starts a moment before the reload, so pageLoad drops what came before it.
  const { own, requests: web } = pageLoad(JSON.parse(fs.readFileSync(file, 'utf8')));
  const counts = { ads: 0, tracking: 0, consent: 0, other: 0 };
  const domains = { ads: new Map(), tracking: new Map(), consent: new Map(), other: new Map() };
  for (const r of web) {
    const third = site(r.u.hostname) !== own;
    const c = category(r.u) ?? (third ? 'other' : null);
    if (!c) continue;
    counts[c]++;
    const d = site(r.u.hostname);
    domains[c].set(d, (domains[c].get(d) ?? 0) + 1);
  }
  rows.push({ file: path.basename(file), site: own, total: web.length, counts, domains });
}

const top = (m, n = 3) => [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, n).map(([d, k]) => `${d} (${k})`).join(', ');
console.log('| Site | Requests | Ads | Tracking | Consent | Main ad domains |');
console.log('|---|---|---|---|---|---|');
for (const r of rows) {
  const cell = (c) => `${r.counts[c]} (${r.domains[c].size})`;
  console.log(`| ${r.site} | ${r.total} | ${cell('ads')} | ${cell('tracking')} | ${cell('consent')} | ${top(r.domains.ads) || '-'} |`);
}

for (const r of rows) {
  console.log(`\n##${r.file} (${r.site}): ${r.total} requests`);
  for (const c of ['ads', 'tracking', 'consent', 'other']) {
    const top = [...r.domains[c].entries()].sort((a, b) => b[1] - a[1]).map(([d, n]) => `${d} ${n}`).join(', ');
    console.log(`${c}: ${r.counts[c]}${top ? ` (${top})` : ''}`);
  }
}
