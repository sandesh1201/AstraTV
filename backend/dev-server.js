'use strict';

// Local run of the same handlers Vercel deploys: `node dev-server.js` (reads TMDB_READ_ACCESS_TOKEN from the environment).
const http = require('node:http');
const tmdb = require('./api/tmdb');
const health = require('./api/health');

const port = Number(process.env.PORT || 8787);
http.createServer((req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (path === '/api/tmdb') return tmdb(req, res);
  if (path === '/api/health') return health(req, res);
  res.statusCode = 404;
  res.setHeader('Content-Type', 'application/json');
  res.end('{"error":"not_found"}');
}).listen(port, () => console.log(`Saandio Catalog API on http://localhost:${port}`));
