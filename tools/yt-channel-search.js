'use strict';

// Finds official YouTube channel IDs by name from YouTube's search page (read-only, no key).
// Usage: node tools/yt-channel-search.js "DD Chandana" "DD Malayalam"
const UA = { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130 Safari/537.36', 'Accept-Language': 'en' } };

(async () => {
  for (const q of process.argv.slice(2)) {
    const r = await fetch(`https://www.youtube.com/results?search_query=${encodeURIComponent(q)}&sp=EgIQAg%253D%253D`, UA);
    const t = await r.text();
    const seen = new Set();
    const hits = [];
    for (const m of t.matchAll(/"channelRenderer":\{"channelId":"(UC[\w-]{22})","title":\{"simpleText":"([^"]+)"\}[\s\S]{0,4000}?"subscriberCountText":\{[^}]*"simpleText":"([^"]+)"/g)) {
      if (seen.has(m[1])) continue;
      seen.add(m[1]);
      const verified = t.slice(m.index, m.index + 6000).includes('BADGE_STYLE_TYPE_VERIFIED');
      hits.push(`${m[1]} "${m[2]}" ${m[3]}${verified ? ' ✓verified' : ''}`);
    }
    console.log(`${q}:\n  ${hits.slice(0, 4).join('\n  ') || '(none)'}`);
  }
})();
