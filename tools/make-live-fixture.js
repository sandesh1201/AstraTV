'use strict';

// Builds the Live TV regression fixture: a trimmed, real subset of the iptv-org feeds the app reads,
// plus golden values taken from tools/out/live-tv-baseline.json (the read-only audit snapshot).
// Usage: node tools/make-live-fixture.js
const fs = require('node:fs');
const path = require('node:path');

const DIRECTORY = 'https://iptv-org.github.io/iptv/';
const WANT = ['SunTV.in', 'SunSurya.in', 'SunGemini.in', 'DDNational.in', 'DDPodhigai.in', 'DDSaptagiri.in', 'DDChandana.in', 'DDMalayalam.in', 'ETVTelugu.in', 'AsianetNews.in', 'AssamTalks.in', 'AsomLive24.in'];
const FEEDS = ['countries/in', 'languages/tam', 'languages/tel', 'languages/kan', 'languages/mal', 'languages/hin', 'languages/asm'];
const OUT = path.join(__dirname, '..', 'app', 'src', 'test', 'resources', 'live');

function blocks(text) {
  const lines = text.split(/\r?\n/);
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].startsWith('#EXTINF')) continue;
    const block = [lines[i]];
    let j = i + 1;
    while (j < lines.length && !lines[j].startsWith('#EXTINF')) { if (lines[j].trim()) block.push(lines[j]); j++; }
    out.push(block);
  }
  return out;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  for (const feed of FEEDS) {
    const text = await (await fetch(`${DIRECTORY}${feed}.m3u`)).text();
    const kept = blocks(text).filter((b) => {
      const m = b[0].match(/tvg-id="([^"]*)"/);
      return m && WANT.includes(m[1].split('@')[0]);
    });
    fs.writeFileSync(path.join(OUT, `${feed.replace('/', '_')}.m3u`), ['#EXTM3U', ...kept.flat(), ''].join('\n'));
    console.log(`${feed}: ${kept.length} entries`);
  }
  const baseline = require('./out/live-tv-baseline.json');
  const golden = baseline.items.filter((c) => WANT.includes(c.tvgId))
    .map((c) => [c.id, c.name, c.language, c.category, c.shownInApp, (c.streams[0] || {}).host || ''].join('\t'));
  fs.writeFileSync(path.join(OUT, 'golden.tsv'), `# id\tname\tlanguage\tcategory\tshownInApp\tfirstStreamHost (from ${baseline.capturedAt})\n${golden.join('\n')}\n`);
  console.log(`golden: ${golden.length} rows`);
})();
