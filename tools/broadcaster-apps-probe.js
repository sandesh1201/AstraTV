'use strict';

// Checks Google Play listings for the official catch-up apps of the broadcasters that the Tamil and
// Telugu serial feeds name. Prints HTTP status, listing title and developer for each package id.
// Usage: node tools/broadcaster-apps-probe.js
const IDS = ['com.suntv.sunnxt', 'in.startv.hotstar', 'in.startv.hotstartvonly', 'com.graymatrix.did', 'com.zee5.androidtv',
  'com.etvwin.mobile', 'com.etv.win', 'com.ottplay.etvwin', 'com.sonyliv', 'com.sonyliv.tv', 'com.jio.media.ondemand', 'com.kalaignartv.app', 'com.aha.ott', 'ahaflix.tv'];
(async () => {
  for (const id of IDS) {
    try {
      const r = await fetch(`https://play.google.com/store/apps/details?id=${id}&hl=en`, { headers: { 'Accept-Language': 'en' }, signal: AbortSignal.timeout(15000) });
      const html = await r.text();
      const title = (html.match(/<title>([^<]*)<\/title>/) || [])[1] || '';
      const dev = (html.match(/"author"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"/) || [])[1] || '';
      const tv = /Android TV|for TV/i.test(html) ? ' · mentions TV' : '';
      console.log(`${id}: ${r.status} ${title.replace(' - Apps on Google Play', '')} · ${dev}${tv}`);
    } catch (e) { console.log(`${id}: error ${e.message}`); }
  }
})();
