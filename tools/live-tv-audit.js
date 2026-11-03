'use strict';

// Read-only audit of the Live TV directory Saandio loads (iptv-org India + language feeds).
// Mirrors IndianTvProvider.load / PlaylistParser.channels so the inventory uses the app's own channel IDs.
// Usage: node tools/live-tv-audit.js [outDir]   (writes live-tv-baseline.json and sun-network-audit.json)

const fs = require('node:fs');
const path = require('node:path');

const DIRECTORY = 'https://iptv-org.github.io/iptv/';
const LANGUAGES = [['tel', 'Telugu'], ['hin', 'Hindi'], ['tam', 'Tamil'], ['kan', 'Kannada'], ['mal', 'Malayalam'], ['ben', 'Bengali'], ['mar', 'Marathi'], ['pan', 'Punjabi'], ['guj', 'Gujarati'], ['urd', 'Urdu'], ['ori', 'Odia'], ['asm', 'Assamese'], ['eng', 'English'], ['bho', 'Bhojpuri'], ['san', 'Sanskrit']];

const SUN = {
  Tamil: ['Sun TV', 'Sun TV HD', 'KTV', 'KTV HD', 'Sun Music', 'Sun Music HD', 'Sun News', 'Adithya TV', 'Chutti TV', 'Sun Life'],
  Telugu: ['Gemini TV', 'Gemini TV HD', 'Gemini Movies', 'Gemini Movies HD', 'Gemini Music', 'Gemini Music HD', 'Gemini Comedy', 'Kushi TV', 'Gemini Life'],
  Kannada: ['Udaya TV', 'Udaya TV HD', 'Udaya Movies', 'Udaya Music', 'Udaya Comedy', 'Chintu TV'],
  Malayalam: ['Surya TV', 'Surya TV HD', 'Surya Movies', 'Surya Music', 'Surya Comedy', 'Kochu TV'],
  Bengali: ['Sun Bangla', 'Sun Bangla HD'],
  Marathi: ['Sun Marathi', 'Sun Marathi HD'],
  Hindi: ['Sun Neo', 'Sun Neo HD'],
};

function attr(line, name) {
  const m = line.match(new RegExp(name + '="([^"]*)"'));
  return m ? m[1] : '';
}
function entries(text) {
  const out = [];
  let pending = null;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (line.startsWith('#EXTINF')) {
      const comma = line.lastIndexOf(',');
      pending = { name: line.slice(comma + 1).trim(), id: attr(line, 'tvg-id'), logo: attr(line, 'tvg-logo'), group: attr(line, 'group-title') };
    } else if (pending && line && !line.startsWith('#')) {
      pending.url = line;
      out.push(pending);
      pending = null;
    }
  }
  return out;
}
const norm = (s) => s.toLowerCase().replace(/\([^)]*\)/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
const host = (u) => { try { return new URL(u).host; } catch { return ''; } };

async function text(url) {
  const r = await fetch(url, { headers: { 'User-Agent': 'SaandioAudit/1.0' } });
  if (!r.ok) throw new Error(`${url} -> HTTP ${r.status}`);
  return r.text();
}

async function main() {
  const outDir = process.argv[2] || 'docs';
  const india = entries(await text(DIRECTORY + 'countries/in.m3u'));
  const ids = new Set(india.filter((e) => e.id).map((e) => e.id.split('@')[0]));
  const channels = new Map();
  let feeds = 0;
  for (const [code, language] of LANGUAGES) {
    let list;
    try { list = entries(await text(`${DIRECTORY}languages/${code}.m3u`)); feeds++; } catch { continue; }
    for (const e of list) {
      const raw = e.id.split('@')[0];
      if (!ids.has(raw)) continue;
      const key = `${raw}~${language}`;
      if (!channels.has(key)) channels.set(key, { id: key, tvgId: raw, name: e.name.replace(/\s*\([^)]*\)/g, ''), language, category: e.group || 'General', logo: !!e.logo, streams: [] });
      channels.get(key).streams.push({ host: host(e.url), https: e.url.startsWith('https://'), type: /\.m3u8/i.test(e.url) ? 'hls' : /\.mpd/i.test(e.url) ? 'dash' : 'other' });
    }
  }
  const known = new Set([...channels.values()].map((c) => c.tvgId));
  for (const e of india) {
    const raw = e.id.split('@')[0];
    if (known.has(raw)) continue;
    const key = raw || e.name;
    if (!channels.has(key)) channels.set(key, { id: key, tvgId: raw, name: e.name.replace(/\s*\([^)]*\)/g, ''), language: 'Other Indian languages', category: e.group || 'General', logo: !!e.logo, streams: [] });
    channels.get(key).streams.push({ host: host(e.url), https: e.url.startsWith('https://'), type: /\.m3u8/i.test(e.url) ? 'hls' : 'other' });
  }
  const list = [...channels.values()].sort((a, b) => a.language.localeCompare(b.language) || a.name.localeCompare(b.name));
  const shown = list.filter((c) => c.streams.some((s) => s.https));
  const baseline = {
    capturedAt: new Date().toISOString(),
    source: DIRECTORY,
    languageFeedsLoaded: `${feeds}/${LANGUAGES.length}`,
    channels: list.length,
    shownInApp: shown.length,
    note: 'Stream hosts only; full URLs are not recorded. Health is stored per device (catalog_v2.db, table health) and is not available on the build machine.',
    items: list.map((c) => ({ ...c, shownInApp: c.streams.some((s) => s.https) })),
  };

  let blocklist = [];
  try {
    const csv = await text('https://raw.githubusercontent.com/iptv-org/database/master/data/blocklist.csv');
    blocklist = csv.split(/\r?\n/).slice(1).filter(Boolean).map((l) => { const [channel, reason, ref] = l.split(','); return { channel, reason, ref }; });
  } catch (e) { blocklist = [{ error: String(e.message) }]; }
  let dbChannels = [];
  try {
    dbChannels = JSON.parse(await text('https://iptv-org.github.io/api/channels.json'));
  } catch { /* optional */ }

  const sun = [];
  for (const [language, names] of Object.entries(SUN)) {
    for (const name of names) {
      const n = norm(name);
      const inApp = list.filter((c) => norm(c.name) === n);
      const db = dbChannels.filter((c) => norm(c.name) === n && c.country === 'IN');
      const blocked = blocklist.filter((b) => b.channel && db.some((d) => d.id === b.channel));
      sun.push({
        name, language,
        inSaandio: inApp.map((c) => ({ id: c.id, streams: c.streams.length, hosts: [...new Set(c.streams.map((s) => s.host))] })),
        iptvOrgDatabaseIds: db.map((d) => d.id),
        logoInDatabase: db.some((d) => d.logo),
        blocklisted: blocked.map((b) => `${b.channel} (${b.reason}${b.ref ? ' ' + b.ref : ''})`),
      });
    }
  }
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'live-tv-baseline.json'), JSON.stringify(baseline, null, 2));
  fs.writeFileSync(path.join(outDir, 'sun-network-audit.json'), JSON.stringify({ capturedAt: baseline.capturedAt, channels: sun }, null, 2));
  console.log(`baseline: ${list.length} channels (${shown.length} shown), feeds ${feeds}/${LANGUAGES.length}`);
  console.log(`sun: ${sun.filter((s) => s.inSaandio.length).length}/${sun.length} present in Saandio, ${sun.filter((s) => s.blocklisted.length).length} blocklisted by iptv-org`);
  for (const s of sun) console.log(`${s.language.padEnd(9)} ${s.name.padEnd(18)} app:${s.inSaandio.map((c) => c.id).join(';') || '-'} db:${s.iptvOrgDatabaseIds.join(';') || '-'} block:${s.blocklisted.join(';') || '-'}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
