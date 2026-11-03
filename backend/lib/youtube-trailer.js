'use strict';

const { json, sameSecret } = require('./tmdb-proxy');

const YOUTUBE_BASE = 'https://www.googleapis.com/youtube/v3';
const UPSTREAM_TIMEOUT_MS = 8000;
const MAX_QUERY = 160;
const MAX_RESULTS = 8;

async function getJson(fetchImpl, url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  try {
    const r = await fetchImpl(url, { headers: { Accept: 'application/json', 'User-Agent': 'SaandioCatalogAPI/1.0' }, signal: controller.signal });
    return { status: r.status, body: r.status >= 200 && r.status < 300 ? await r.json() : null };
  } finally {
    clearTimeout(timer);
  }
}

function upstreamError(status) {
  if (status === 403) return json(503, { error: 'upstream_quota', message: 'Trailer search is over its daily quota or the server key was rejected.' }, { 'Retry-After': '3600' });
  if (status === 429) return json(503, { error: 'upstream_rate_limited', message: 'Trailer search is busy. Try again shortly.' }, { 'Retry-After': '30' });
  return json(502, { error: 'upstream_error', message: `Trailer search returned HTTP ${status}.` }, { 'Retry-After': '60' });
}

/**
 * GET /api/trailer?q={title} {year} {language} official trailer
 * Runs YouTube Data API search.list (embeddable, syndicated videos only) and returns the matching
 * videos.list items (snippet, contentDetails, status) so the app can check embeddability, privacy,
 * region blocks and duration before choosing a trailer. The YouTube key stays on the server.
 */
async function handle(req, env, fetchImpl) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return json(405, { error: 'method_not_allowed', message: 'Use GET.' }, { Allow: 'GET, HEAD' });
  if (env.SAANDIO_CLIENT_TOKEN && !sameSecret(req.headers['x-saandio-client'], env.SAANDIO_CLIENT_TOKEN)) {
    return json(401, { error: 'unknown_client', message: 'This client is not allowed to use the catalog service.' });
  }
  const key = (env.YOUTUBE_API_KEY || '').trim();
  if (!key) return json(503, { error: 'not_configured', message: 'The catalog service has no YouTube Data API key configured.' }, { 'Retry-After': '300' });
  const q = (new URL(req.url, 'http://localhost').searchParams.get('q') || '').trim();
  if (!q) return json(400, { error: 'bad_parameter', message: 'Parameter q is required.' });
  if (q.length > MAX_QUERY) return json(400, { error: 'bad_parameter', message: 'Parameter q is too long.' });

  const search = new URL(`${YOUTUBE_BASE}/search`);
  search.searchParams.set('part', 'snippet');
  search.searchParams.set('type', 'video');
  search.searchParams.set('videoEmbeddable', 'true');
  search.searchParams.set('videoSyndicated', 'true');
  search.searchParams.set('safeSearch', 'strict');
  search.searchParams.set('maxResults', String(MAX_RESULTS));
  search.searchParams.set('q', q);
  search.searchParams.set('key', key);
  let found;
  try {
    found = await getJson(fetchImpl, search.toString());
  } catch (e) {
    console.error(`trailer search failed: ${e && e.name === 'AbortError' ? 'timeout' : 'network error'}`);
    return json(504, { error: 'upstream_unavailable', message: 'Trailer search did not respond.' }, { 'Retry-After': '30' });
  }
  if (!found.body) {
    console.error(`trailer search returned HTTP ${found.status}`);
    return upstreamError(found.status);
  }
  const ids = (found.body.items || [])
    .map((item) => item && item.id && item.id.videoId)
    .filter((id) => typeof id === 'string' && /^[A-Za-z0-9_-]{11}$/.test(id));
  const cache = { 'Cache-Control': 'public, max-age=3600, s-maxage=86400, stale-while-revalidate=604800' };
  if (!ids.length) return json(200, { items: [] }, cache);

  const videos = new URL(`${YOUTUBE_BASE}/videos`);
  videos.searchParams.set('part', 'snippet,contentDetails,status');
  videos.searchParams.set('id', ids.join(','));
  videos.searchParams.set('key', key);
  let details;
  try {
    details = await getJson(fetchImpl, videos.toString());
  } catch (e) {
    console.error(`trailer details failed: ${e && e.name === 'AbortError' ? 'timeout' : 'network error'}`);
    return json(504, { error: 'upstream_unavailable', message: 'Trailer search did not respond.' }, { 'Retry-After': '30' });
  }
  if (!details.body) {
    console.error(`trailer details returned HTTP ${details.status}`);
    return upstreamError(details.status);
  }
  const items = (details.body.items || []).map((v) => ({
    id: v.id,
    snippet: v.snippet && {
      title: v.snippet.title, channelTitle: v.snippet.channelTitle, description: (v.snippet.description || '').slice(0, 500),
      liveBroadcastContent: v.snippet.liveBroadcastContent,
    },
    contentDetails: v.contentDetails && { duration: v.contentDetails.duration, regionRestriction: v.contentDetails.regionRestriction },
    status: v.status && { embeddable: v.status.embeddable, privacyStatus: v.status.privacyStatus },
  }));
  return json(200, { items }, cache);
}

module.exports = { handle, MAX_QUERY };
