// Compares a page load with Shields on against the app's block log (docs/testing.md, roadmap 8.3).
//
//   node tools/capture/compare.mjs <on.json> <blocked.log> <off.json> [<on.json> <blocked.log> <off.json>]...
//
// on.json / off.json: captures from requests.mjs with Shields on / off for the site (8.2).
// blocked.log: `adb logcat -d -v raw -s tvshields.block:D` for the Shields-on load, with
// `log.tag.tvshields.block` set to DEBUG before the app started.
// A request counts as blocked when its URL is in the log. The same URL can show up several
// times in a capture for one logged block (a blocked fetch fails CORS and may be retried), so
// URLs are matched as a set. An ad or tracking request that is not in the log reached its
// server ("leaked"). CORS preflights never pass through shouldInterceptRequest, so they are
// counted apart.

import fs from 'node:fs';
import { category, pageLoad, site } from './classify.mjs';

const LINE = /^(Blocked|Redirected \([^)]*\)) (\S+)/;

function blockLog(file) {
  const urls = [];
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = LINE.exec(line);
    // The WebView logs the URL with its fragment; DevTools reports it without.
    if (m) urls.push(m[2].replace(/#.*$/, ''));
  }
  return urls;
}

const count = (m, k) => m.set(k, (m.get(k) ?? 0) + 1);
const top = (m, n = 3) => [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, n).map(([d, k]) => `${d} (${k})`).join(', ');

function compare(onFile, logFile, offFile) {
  const on = pageLoad(JSON.parse(fs.readFileSync(onFile, 'utf8')));
  const off = pageLoad(JSON.parse(fs.readFileSync(offFile, 'utf8')));
  const logged = blockLog(logFile);
  const log = new Set(logged);
  const seen = new Set();
  const r = {
    site: on.own,
    requests: on.requests.length,
    logged: logged.length,
    off: { ads: 0, tracking: 0 },
    blocked: { ads: 0, tracking: 0, consent: 0, other: 0, own: 0 },
    leaked: { ads: 0, tracking: 0 },
    preflights: 0,
    leakedDomains: new Map(),
    ownBlocked: new Map(),
  };
  for (const q of off.requests) {
    const c = category(q.u);
    // Counted like inventory.mjs (preflights included), so it matches the 8.2 table.
    if (c === 'ads' || c === 'tracking') r.off[c]++;
  }
  for (const q of on.requests) {
    const c = category(q.u);
    if (q.type === 'Preflight') {
      if (log.has(q.url) || c === 'ads' || c === 'tracking') r.preflights++;
      continue;
    }
    if (log.has(q.url)) {
      seen.add(q.url);
      if (c) r.blocked[c]++;
      else if (site(q.u.hostname) === on.own) {
        r.blocked.own++;
        count(r.ownBlocked, q.u.hostname + q.u.pathname);
      } else r.blocked.other++;
    } else if (c === 'ads' || c === 'tracking') {
      r.leaked[c]++;
      count(r.leakedDomains, `${site(q.u.hostname)} ${c === 'ads' ? 'ad' : 'tr'}`);
    }
  }
  // Logged URLs the capture did not see (made outside the page's DevTools session).
  r.unmatched = [...log].filter((u) => !seen.has(u)).length;
  return r;
}

const args = process.argv.slice(2);
if (args.length === 0 || args.length % 3 !== 0) {
  console.error('usage: compare.mjs <on.json> <blocked.log> <off.json> [...]');
  process.exit(1);
}
const rows = [];
for (let i = 0; i < args.length; i += 3) rows.push(compare(args[i], args[i + 1], args[i + 2]));

const cell = (r, c) => `${r.blocked[c]} / ${r.leaked[c]}`;
console.log('| Site | Requests | Blocked (log) | Ads blocked / leaked | Tracking blocked / leaked | Ads off (8.2 load) | Leaked domains |');
console.log('|---|---|---|---|---|---|---|');
for (const r of rows) {
  console.log(`| ${r.site} | ${r.requests} | ${r.logged} | ${cell(r, 'ads')} | ${cell(r, 'tracking')} | ${r.off.ads} | ${top(r.leakedDomains) || '-'} |`);
}
for (const r of rows) {
  console.log(`\n## ${r.site}`);
  console.log(`blocked: ads ${r.blocked.ads}, tracking ${r.blocked.tracking}, consent ${r.blocked.consent}, other third party ${r.blocked.other}, own site ${r.blocked.own}; logged but not captured ${r.unmatched}; preflights to blocked or ad/tracking URLs ${r.preflights}`);
  console.log(`off (8.2 load): ads ${r.off.ads}, tracking ${r.off.tracking}`);
  if (r.leakedDomains.size) console.log(`leaked: ${top(r.leakedDomains, 20)}`);
  if (r.ownBlocked.size) console.log(`own site blocked: ${top(r.ownBlocked, 10)}`);
}
