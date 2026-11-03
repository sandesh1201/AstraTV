'use strict';

// Read-only probe of the TamilTVSerial and TeluguStream public RSS feeds: robots rules, feed
// shape, item fields and pagination. Prints metadata only; no episode page or video link is opened.
// Usage: node tools/catchup-feed-probe.js
const UA = { headers: { 'User-Agent': 'SaandioCatchupProbe/1.0 (+metadata only)' }, redirect: 'follow' };
async function get(url) {
  try { const r = await fetch(url, { ...UA, signal: AbortSignal.timeout(15000) }); return { status: r.status, body: await r.text(), etag: r.headers.get('etag'), lm: r.headers.get('last-modified'), type: r.headers.get('content-type') }; }
  catch (e) { return { status: 0, body: '', error: e.message }; }
}
const tag = (xml, name) => { const m = xml.match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)<\\/${name}>`)); return m ? m[1].replace(/<!\[CDATA\[|\]\]>/g, '').trim() : ''; };

(async () => {
  for (const base of ['https://www.tamiltvserial.com/', 'https://telugustream.com/']) {
    console.log(`\n==== ${base}`);
    const robots = await get(base + 'robots.txt');
    console.log(`robots.txt ${robots.status}:\n${robots.body.split('\n').filter((l) => l.trim()).slice(0, 15).map((l) => '  ' + l).join('\n')}`);
    for (const path of ['feed/', 'feed/?paged=2', 'feed/?paged=6']) {
      const r = await get(base + path);
      const items = r.body.match(/<item>[\s\S]*?<\/item>/g) || [];
      console.log(`\n/${path} -> ${r.status} ${r.type || ''} items=${items.length} etag=${r.etag ? 'yes' : 'no'} last-modified=${r.lm || 'no'}`);
      for (const it of items.slice(0, path === 'feed/' ? 6 : 2)) {
        const cats = [...it.matchAll(/<category>(?:<!\[CDATA\[)?([^\]<]*)(?:\]\]>)?<\/category>/g)].map((m) => m[1]);
        const content = tag(it, 'content:encoded');
        const imgs = (content.match(/<img[^>]+src="https?:\/\/([^/"]+)/g) || []).length;
        console.log(`  - "${tag(it, 'title')}" | pub ${tag(it, 'pubDate')} | guid ${tag(it, 'guid').slice(0, 60)} | cats [${cats.join(', ')}] | imgs ${imgs} | media ${/<media:|<enclosure/.test(it)}`);
      }
    }
  }
})();
