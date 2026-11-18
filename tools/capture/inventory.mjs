// Summarizes captures from requests.mjs: per site, how many requests go to ad, tracking and
// consent services (docs/testing.md, roadmap 8.2).
//
//   node tools/capture/inventory.mjs <capture.json>...
//
// The categories come from the hand-made table below, not from the filter lists, so the
// inventory does not depend on what Shields blocks. Third-party hosts not in the table are
// listed as "other" for review.

import fs from 'node:fs';
import path from 'node:path';

// Registrable domain (or host prefix / host + path prefix) -> category.
const CATEGORIES = {
  ads: [
    'doubleclick.net', 'googlesyndication.com', 'googletagservices.com', 'googleadservices.com',
    'adservice.google.com', 'amazon-adsystem.com', 'smartadserver.com', 'sascdn.com',
    'rubiconproject.com', 'richaudience.com', 'mathilde-ads.com', 'criteo.com', 'criteo.net',
    'adnxs.com', 'pubmatic.com', 'teads.tv', 'lijit.com', 'tradedoubler.com', 'adserv.mobi',
    'brightline.tv', 'ads-twitter.com', 'doubleverify.com', 'btloader.com', 'concert.io',
    'id5-sync.com', 'ad.gt', 'pbstck.com', 'imasdk.googleapis.com', 'openx.net', 'casalemedia.com',
    'adsrvr.org', 'taboola.com', 'outbrain.com', 'seedtag.com', 'yieldmo.com', '3lift.com',
    'sharethrough.com', 'media.net', 'moatads.com', 'adform.net', 'teads.com', 'springserve.com',
    'youtube.com/pagead', 'youtube.com/api/stats/ads', 'fundingchoicesmessages.google.com',
  ],
  tracking: [
    'google-analytics.com', 'googletagmanager.com', 'scorecardresearch.com', 'cxense.com',
    'permutive.app', 'permutive.com', 'parsely.com', 'demdex.net', 'adobedtm.com', 'omtrdc.net',
    'facebook.net', 'facebook.com/tr', 'browser-intake-datadoghq.com', 'crazyegg.com', 'sensic.net',
    'tiqcdn.com', 'mrf.io', 'sail-horizon.com', 'hotjar.com', 'chartbeat.com', 'chartbeat.net',
    'quantserve.com', 'clarity.ms', 'newrelic.com', 'nr-data.net', 'segment.io', 'mparticle.com',
    'braze.com', 'branch.io', 'amplitude.com', 'mixpanel.com', 'youtube.com/api/stats',
    'youtube.com/ptracking', 'youtube.com/generate_204', 'play.google.com/log',
    'analytics.google.com', 'segment.com', 'appsflyer.com', 'cloudflareinsights.com',
  ],
  consent: [
    'cookielaw.org', 'onetrust.com', 'privacy-center.org', 'privacymanager.io', 'ketchcdn.com',
    'ketchjs.com', 'cmp.prisa.com', 'consensu.org', 'cookiebot.com', 'sourcepoint.com',
    'quantcast.com', 'iubenda.com',
  ],
};

const TWO_LEVEL = new Set(['co.uk', 'com.co', 'com.br', 'com.mx', 'com.ar', 'co.jp', 'com.au', 'org.uk', 'com.es', 'net.co']);
const site = (host) => {
  const labels = host.split('.');
  const n = TWO_LEVEL.has(labels.slice(-2).join('.')) ? 3 : 2;
  return labels.slice(-n).join('.');
};

// Google serves ads and analytics from its main domain (any country): told apart by path.
const GOOGLE = /^(www\.)?google\.[a-z.]+$/;
const GOOGLE_PATHS = [['/pagead', 'ads'], ['/rmkt', 'ads'], ['/ccm', 'ads'], ['/ads/', 'ads'], ['/g/collect', 'tracking']];

function category(url) {
  if (GOOGLE.test(url.hostname)) {
    const hit = GOOGLE_PATHS.find(([prefix]) => url.pathname.startsWith(prefix));
    if (hit) return hit[1];
  }
  const hostPath = url.hostname + url.pathname;
  for (const [name, patterns] of Object.entries(CATEGORIES)) {
    for (const p of patterns) {
      if (p.includes('/')) {
        if (hostPath.startsWith(p) || hostPath.startsWith(`www.${p}`) || hostPath.startsWith(`m.${p}`)) return name;
      } else if (url.hostname === p || url.hostname.endsWith(`.${p}`)) {
        return name;
      }
    }
  }
  return null;
}

const rows = [];
for (const file of process.argv.slice(2)) {
  const { requests } = JSON.parse(fs.readFileSync(file, 'utf8'));
  const all = requests.map((r) => ({ ...r, u: new URL(r.url) })).filter((r) => r.u.protocol.startsWith('http'));
  // The page's own site is the one most requests are made from. The recording starts a moment
  // before the reload, so everything before the reloaded main document is left out.
  const from = new Map();
  for (const r of all) {
    if (!r.documentUrl?.startsWith('http')) continue;
    const s = site(new URL(r.documentUrl).hostname);
    from.set(s, (from.get(s) ?? 0) + 1);
  }
  const own = [...from.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
  const start = all.findIndex((r) => r.type === 'Document' && site(r.u.hostname) === own);
  const web = start < 0 ? all : all.slice(start);
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
