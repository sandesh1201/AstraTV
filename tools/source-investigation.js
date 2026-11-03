'use strict';

// Read-only investigation of the HD Movies and Catch-Up sources: repository metadata, site status,
// published APIs/feeds, robots and official apps. Fetches public pages only; no stream URLs are resolved.
// Usage: node tools/source-investigation.js
const UA = { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130 Safari/537.36', 'Accept-Language': 'en' }, redirect: 'follow' };
const REPOS = ['Gujal00/GujalKodiWork', 'knagaraju/GujalKodiWork', 'RupGautam/einthusan-cli', 'ram2141/einthusan', 'ram2141/repository.einthusan'];

async function get(url) {
  try {
    const r = await fetch(url, { ...UA, signal: AbortSignal.timeout(15000) });
    const text = await r.text();
    return { status: r.status, url: r.url, text };
  } catch (e) {
    return { status: 0, url, text: '', error: e.cause ? e.cause.code || e.cause.message : e.message };
  }
}

(async () => {
  console.log('== Repositories');
  for (const repo of REPOS) {
    const r = await get(`https://api.github.com/repos/${repo}`);
    if (r.status !== 200) { console.log(`${repo}: HTTP ${r.status}`); continue; }
    const j = JSON.parse(r.text);
    console.log(`${repo}: pushed ${j.pushed_at} · license ${j.license ? j.license.spdx_id : 'none'} · archived ${j.archived} · ${j.description || ''}`);
  }
  const tree = await get('https://api.github.com/repos/Gujal00/GujalKodiWork/git/trees/HEAD?recursive=1');
  if (tree.status === 200) {
    const paths = JSON.parse(tree.text).tree.map((t) => t.path).filter((p) => /einthusan/i.test(p) && /\.py$/.test(p));
    console.log(`GujalKodiWork einthusan files: ${paths.slice(0, 8).join(', ') || '(none)'}`);
  }

  console.log('\n== Einthusan');
  for (const url of ['https://einthusan.tv/', 'https://einthusan.tv/robots.txt', 'https://einthusan.tv/terms/', 'https://einthusan.tv/api/', 'https://www.einthusan.com/']) {
    const r = await get(url);
    const title = (r.text.match(/<title>([^<]*)<\/title>/i) || [])[1] || '';
    console.log(`${url} -> ${r.status} ${r.error || ''} ${r.url !== url ? `(final ${r.url})` : ''} ${title.trim().slice(0, 80)}`);
    if (url.endsWith('robots.txt') && r.status === 200) console.log(r.text.split('\n').slice(0, 12).map((l) => `   ${l}`).join('\n'));
    if (url === 'https://einthusan.tv/' && r.status === 200) {
      const hints = ['premium', 'android', 'play.google', 'apps.apple', 'api', 'partner', 'login'].filter((k) => r.text.toLowerCase().includes(k));
      console.log(`   page mentions: ${hints.join(', ')}`);
      const links = [...new Set([...r.text.matchAll(/href="(https:\/\/play\.google\.com[^"]+|https:\/\/apps\.apple\.com[^"]+)"/g)].map((m) => m[1]))];
      console.log(`   store links: ${links.join(' ') || '(none)'}`);
    }
  }
  for (const id of ['tv.einthusan', 'com.einthusan', 'com.einthusan.app', 'tv.einthusan.app', 'com.einthusan.android']) {
    const r = await get(`https://play.google.com/store/apps/details?id=${id}&hl=en`);
    console.log(`Play ${id}: ${r.status}`);
  }
  const search = await get('https://play.google.com/store/search?q=einthusan&c=apps&hl=en');
  const ids = [...new Set([...search.text.matchAll(/\/store\/apps\/details\?id=([\w.]+)/g)].map((m) => m[1]))];
  console.log(`Play search "einthusan": ${ids.slice(0, 10).join(', ')}`);

  console.log('\n== Catch-up sites');
  for (const base of ['https://www.tamiltvserial.com/', 'https://telugustream.com/']) {
    const home = await get(base);
    const title = (home.text.match(/<title>([^<]*)<\/title>/i) || [])[1] || '';
    console.log(`${base} -> ${home.status} ${home.error || ''} ${home.url !== base ? `(final ${home.url})` : ''} "${title.trim().slice(0, 90)}"`);
    if (home.status === 200) {
      const lower = home.text.toLowerCase();
      const networks = ['vijay', 'sun tv', 'zee', 'star maa', 'gemini', 'colors', 'etv', 'kalaignar', 'polimer'].filter((k) => lower.includes(k));
      console.log(`   channel names on page: ${networks.join(', ') || '(none)'}`);
      const hosts = [...new Set([...home.text.matchAll(/<iframe[^>]+src="https?:\/\/([^/"]+)/gi)].map((m) => m[1]))];
      console.log(`   embedded player hosts on home page: ${hosts.join(', ') || '(none)'}`);
      for (const k of ['dmca', 'copyright', 'disclaimer', 'we do not host', 'licen']) if (lower.includes(k)) console.log(`   mentions "${k}"`);
    }
    for (const path of ['robots.txt', 'feed/', 'wp-json/', 'terms', 'dmca']) {
      const r = await get(base + path);
      console.log(`   /${path} -> ${r.status}${r.error ? ` ${r.error}` : ''}`);
    }
  }
})();
