'use strict';

// Writes docs/live-tv-baseline.tsv from tools/out/live-tv-baseline.json: the channels the app actually
// holds (HTTPS streams; HTTP entries are dropped by PlaylistParser before they reach the app).
const fs = require('node:fs');
const path = require('node:path');

const b = require('./out/live-tv-baseline.json');
const rows = b.items.filter((c) => c.shownInApp)
  .map((c) => [c.id, c.name, c.language, c.category, c.logo ? 'logo' : '', c.streams.length, [...new Set(c.streams.map((s) => s.host))].join(' '), [...new Set(c.streams.map((s) => s.type))].join(' ')].join('\t'));
const header = `# Saandio Live TV baseline · captured ${b.capturedAt} from ${b.source} (${b.languageFeedsLoaded} language feeds)\n`
  + `# ${rows.length} channels held by the app. Stream hosts only; full URLs are not recorded. Health is per device and not included.\n`
  + 'id\tname\tlanguage\tcategory\tlogo\tstreams\thosts\ttypes\n';
fs.writeFileSync(path.join(__dirname, '..', 'docs', 'live-tv-baseline.tsv'), header + rows.join('\n') + '\n');
console.log(`${rows.length} rows`);
