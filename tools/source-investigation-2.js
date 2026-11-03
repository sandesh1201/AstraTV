'use strict';

// Follow-up to source-investigation.js: quotes the Einthusan terms/intro passages about apps,
// premium and automated access, the one Play Store match, and the serial sites' disclaimers.
const UA = { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130 Safari/537.36', 'Accept-Language': 'en' }, redirect: 'follow' };
const text = (html) => html.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>/gi, ' ').replace(/<[^>]+>/g, ' ').replace(/&amp;/g, '&').replace(/&#8217;|&rsquo;/g, "'").replace(/\s+/g, ' ');
async function get(url) {
  try { const r = await fetch(url, { ...UA, signal: AbortSignal.timeout(15000) }); return { status: r.status, body: await r.text() }; }
  catch (e) { return { status: 0, body: '' }; }
}
function quotes(body, words, width = 220, max = 3) {
  const t = text(body), out = [];
  for (const w of words) {
    let i = -1, n = 0;
    while ((i = t.toLowerCase().indexOf(w, i + 1)) >= 0 && n++ < max) out.push(`[${w}] …${t.slice(Math.max(0, i - width / 2), i + width / 2)}…`);
  }
  return out;
}

(async () => {
  const intro = await get('https://einthusan.tv/intro/');
  console.log('== einthusan intro');
  quotes(intro.body, ['android', 'premium', 'api', 'smart tv', 'chromecast']).forEach((q) => console.log(q));
  const terms = await get('https://einthusan.tv/terms/');
  console.log('\n== einthusan terms');
  quotes(terms.body, ['automated', 'scrap', 'robot', 'third party', 'third-party', 'reverse', 'embed', 'licen', 'premium'], 260, 2).forEach((q) => console.log(q));
  for (const p of ['https://einthusan.tv/premium/', 'https://einthusan.tv/faq/', 'https://einthusan.tv/apps/', 'https://einthusan.tv/devices/']) {
    const r = await get(p);
    const title = (r.body.match(/<title>([^<]*)<\/title>/i) || [])[1] || '';
    console.log(`\n${p} -> ${r.status} ${title.trim()}`);
    if (/faq|premium|apps|devices/.test(p) && r.status === 200 && !/Not Found/i.test(title)) quotes(r.body, ['android', 'tv', 'app', 'hd'], 200, 2).forEach((q) => console.log(q));
  }
  const play = await get('https://play.google.com/store/apps/details?id=com.einthusanai.einthusan&hl=en');
  const name = (play.body.match(/<title>([^<]*)<\/title>/i) || [])[1] || '';
  const dev = (play.body.match(/"author"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"/) || [])[1] || '';
  console.log(`\n== Play com.einthusanai.einthusan -> ${play.status} "${name}" developer "${dev}"`);
  quotes(play.body, ['einthusan'], 200, 2).forEach((q) => console.log(q));

  for (const base of ['https://www.tamiltvserial.com/', 'https://telugustream.com/']) {
    const home = await get(base);
    console.log(`\n== ${base}`);
    quotes(home.body, ['disclaimer', 'copyright', 'we do not', 'not host'], 300, 1).forEach((q) => console.log(q));
    const feed = await get(base + 'feed/');
    const item = (feed.body.match(/<item>[\s\S]*?<\/item>/) || [''])[0];
    const title = (item.match(/<title>([^<]*)<\/title>/) || [])[1] || '';
    const cat = [...item.matchAll(/<category><!\[CDATA\[([^\]]*)\]\]><\/category>/g)].map((m) => m[1]);
    console.log(`latest feed item: "${title}" categories: ${cat.join(', ')}`);
    const post = (item.match(/<link>([^<]+)<\/link>/) || [])[1];
    if (post) {
      const page = await get(post);
      const hosts = [...new Set([...page.body.matchAll(/<iframe[^>]+src="https?:\/\/([^/"]+)/gi), ...page.body.matchAll(/href="https?:\/\/([^/"]+)\/[^"]*"[^>]*>\s*(?:Part|Link|Watch|Video)/gi)].map((m) => m[1]))];
      console.log(`latest post player/link hosts: ${hosts.join(', ') || '(none found in static HTML)'}`);
    }
  }
})();
