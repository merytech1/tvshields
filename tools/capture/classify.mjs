// Shared by inventory.mjs (8.2) and compare.mjs (8.3): sorts requests into ad, tracking and
// consent services with a hand-made table, independent of the filter lists.

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

/** Approximate registrable domain of a host. */
export const site = (host) => {
  const labels = host.split('.');
  const n = TWO_LEVEL.has(labels.slice(-2).join('.')) ? 3 : 2;
  return labels.slice(-n).join('.');
};

// Google serves ads and analytics from its main domain (any country): told apart by path.
const GOOGLE = /^(www\.)?google\.[a-z.]+$/;
const GOOGLE_PATHS = [['/pagead', 'ads'], ['/rmkt', 'ads'], ['/ccm', 'ads'], ['/ads/', 'ads'], ['/g/collect', 'tracking']];

/** 'ads', 'tracking', 'consent' or null for a URL object. */
export function category(url) {
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

/**
 * Loads a capture from requests.mjs and keeps the recorded page load: HTTP(S) requests from the
 * page's own site (the one most requests are made from) onwards from its main document.
 */
export function pageLoad(capture) {
  const all = capture.requests.map((r) => ({ ...r, u: new URL(r.url) })).filter((r) => r.u.protocol.startsWith('http'));
  const from = new Map();
  for (const r of all) {
    if (!r.documentUrl?.startsWith('http')) continue;
    const s = site(new URL(r.documentUrl).hostname);
    from.set(s, (from.get(s) ?? 0) + 1);
  }
  const own = [...from.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
  const start = all.findIndex((r) => r.type === 'Document' && site(r.u.hostname) === own);
  return { own, requests: start < 0 ? all : all.slice(start) };
}
