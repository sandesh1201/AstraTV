'use strict';

// Read-only check of official routes: Play Store listings for broadcaster apps and the
// official YouTube channels (channel ID, public upload feed) used for Catch-Up TV and Sun News.
// Usage: node tools/official-sources-audit.js

const UA = { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130 Safari/537.36', 'Accept-Language': 'en' } };

const APPS = process.argv.slice(2).filter((a) => a.startsWith('app:')).map((a) => a.slice(4));
const HANDLES = process.argv.slice(2).filter((a) => a.startsWith('@'));

async function play(id) {
  const r = await fetch(`https://play.google.com/store/apps/details?id=${id}&hl=en`, UA);
  const t = await r.text();
  const title = (t.match(/<title>([^<]*)/) || [])[1] || '-';
  console.log(`PLAY ${id} HTTP ${r.status} ${title}${/Android TV/i.test(t) ? ' · mentions Android TV' : ''}`);
}

async function channel(handle) {
  const r = await fetch(`https://www.youtube.com/${handle}`, UA);
  const t = await r.text();
  const id = (t.match(/<link rel="canonical" href="https:\/\/www\.youtube\.com\/channel\/(UC[\w-]{22})/) || [])[1];
  const name = (t.match(/<meta property="og:title" content="([^"]*)/) || [])[1] || '-';
  if (!id) { console.log(`YT ${handle} HTTP ${r.status} no channel`); return; }
  const feed = await fetch(`https://www.youtube.com/feeds/videos.xml?channel_id=${id}`, UA);
  const xml = feed.ok ? await feed.text() : '';
  const entries = [...xml.matchAll(/<entry>[\s\S]*?<\/entry>/g)].map((m) => m[0]);
  const first = entries[0] || '';
  const title = (first.match(/<title>([^<]*)/) || [])[1] || '';
  const published = (first.match(/<published>([^<]*)/) || [])[1] || '';
  console.log(`YT ${handle} ${id} "${name}" feed HTTP ${feed.status} entries ${entries.length} latest ${published.slice(0, 10)} "${title.slice(0, 70)}"`);
}

(async () => {
  for (const a of APPS) { try { await play(a); } catch (e) { console.log(`PLAY ${a} ERR ${e.message}`); } }
  for (const h of HANDLES) { try { await channel(h); } catch (e) { console.log(`YT ${h} ERR ${e.message}`); } }
})();
