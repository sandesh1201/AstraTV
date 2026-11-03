'use strict';

// Lists iptv-org database channels owned by Sun TV Network (read-only).
(async () => {
  const all = await (await fetch('https://iptv-org.github.io/api/channels.json')).json();
  for (const c of all) {
    const owners = (c.owners || []).join(',');
    if (/sun tv network/i.test(owners) || /^Sun[A-Z]/.test(c.id) && c.country === 'IN') {
      console.log(`${c.id}\t${c.name}\t${(c.alt_names || []).join('|')}\t${owners}\t${c.closed || ''}`);
    }
  }
})();
