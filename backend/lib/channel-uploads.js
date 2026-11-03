'use strict';

const { json, sameSecret } = require('./tmdb-proxy');

const YOUTUBE_BASE = 'https://www.googleapis.com/youtube/v3';
const UPSTREAM_TIMEOUT_MS = 8000;
const MAX_RESULTS = 25;

/**
 * Official broadcaster YouTube channels whose public uploads feed the Catch-Up TV tab. Only these
 * IDs are served, so the endpoint cannot be used as a general YouTube proxy. Keep in sync with
 * CatchupChannels.java.
 */
const ALLOWED_CHANNELS = new Map([
  ['UC5-_zkjKabYd7tsLucKsriw', 'DD Tamil'],
  ['UCcsvAreQ1IxIjWlBSpNEOWg', 'DD Saptagiri'],
  ['UC6V4nVa0CQkoLZUlg9drmZA', 'DD Yadagiri'],
  ['UCbdMik2cV8pea1jcWdX_CYA', 'DD Chandana'],
  ['UC4--pPs1CVZvs60uuhfjdwQ', 'DD Malayalam'],
  ['UCSjPe5kinQtwcyHcFJyyMfw', 'Doordarshan National'],
  ['UCWxqSvqiH8PGpSNPSVtFwGw', 'Prasar Bharati Archives'],
]);

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
  if (status === 403) return json(503, { error: 'upstream_quota', message: 'Catch-up listing is over its daily quota or the server key was rejected.' }, { 'Retry-After': '3600' });
  if (status === 404) return json(404, { error: 'not_found', message: 'The broadcaster channel has no public uploads.' }, { 'Retry-After': '86400' });
  if (status === 429) return json(503, { error: 'upstream_rate_limited', message: 'Catch-up listing is busy. Try again shortly.' }, { 'Retry-After': '30' });
  return json(502, { error: 'upstream_error', message: `Catch-up listing returned HTTP ${status}.` }, { 'Retry-After': '60' });
}

function thumbnail(snippet) {
  const t = (snippet && snippet.thumbnails) || {};
  const best = t.maxres || t.standard || t.high || t.medium || t.default;
  return best && typeof best.url === 'string' && best.url.startsWith('https://') ? best.url : '';
}

/**
 * GET /api/channel-uploads?channel={UC…}
 * Lists the newest public uploads of an allowlisted official broadcaster channel through the
 * YouTube Data API (playlistItems.list on the channel's uploads playlist, then videos.list), and
 * returns snippet, duration, region restriction and embeddability so the app can classify
 * episodes and hand playback to YouTube. The YouTube key stays on the server.
 */
async function handle(req, env, fetchImpl) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return json(405, { error: 'method_not_allowed', message: 'Use GET.' }, { Allow: 'GET, HEAD' });
  if (env.SAANDIO_CLIENT_TOKEN && !sameSecret(req.headers['x-saandio-client'], env.SAANDIO_CLIENT_TOKEN)) {
    return json(401, { error: 'unknown_client', message: 'This client is not allowed to use the catalog service.' });
  }
  const channel = (new URL(req.url, 'http://localhost').searchParams.get('channel') || '').trim();
  if (!/^UC[A-Za-z0-9_-]{22}$/.test(channel)) return json(400, { error: 'bad_parameter', message: 'Parameter channel must be a YouTube channel id.' });
  if (!ALLOWED_CHANNELS.has(channel)) return json(403, { error: 'channel_not_allowed', message: 'Only official broadcaster channels are listed.' });
  const key = (env.YOUTUBE_API_KEY || '').trim();
  if (!key) return json(503, { error: 'not_configured', message: 'The catalog service has no YouTube Data API key configured.' }, { 'Retry-After': '300' });

  const playlist = new URL(`${YOUTUBE_BASE}/playlistItems`);
  playlist.searchParams.set('part', 'contentDetails');
  playlist.searchParams.set('playlistId', `UU${channel.slice(2)}`);
  playlist.searchParams.set('maxResults', String(MAX_RESULTS));
  playlist.searchParams.set('key', key);
  let listed;
  try {
    listed = await getJson(fetchImpl, playlist.toString());
  } catch (e) {
    console.error(`channel uploads failed: ${e && e.name === 'AbortError' ? 'timeout' : 'network error'}`);
    return json(504, { error: 'upstream_unavailable', message: 'Catch-up listing did not respond.' }, { 'Retry-After': '30' });
  }
  if (!listed.body) {
    console.error(`channel uploads returned HTTP ${listed.status}`);
    return upstreamError(listed.status);
  }
  const ids = (listed.body.items || [])
    .map((item) => item && item.contentDetails && item.contentDetails.videoId)
    .filter((id) => typeof id === 'string' && /^[A-Za-z0-9_-]{11}$/.test(id));
  const cache = { 'Cache-Control': 'public, max-age=1800, s-maxage=3600, stale-while-revalidate=86400' };
  if (!ids.length) return json(200, { channel, items: [] }, cache);

  const videos = new URL(`${YOUTUBE_BASE}/videos`);
  videos.searchParams.set('part', 'snippet,contentDetails,status');
  videos.searchParams.set('id', ids.join(','));
  videos.searchParams.set('key', key);
  let details;
  try {
    details = await getJson(fetchImpl, videos.toString());
  } catch (e) {
    console.error(`channel video details failed: ${e && e.name === 'AbortError' ? 'timeout' : 'network error'}`);
    return json(504, { error: 'upstream_unavailable', message: 'Catch-up listing did not respond.' }, { 'Retry-After': '30' });
  }
  if (!details.body) {
    console.error(`channel video details returned HTTP ${details.status}`);
    return upstreamError(details.status);
  }
  const items = (details.body.items || [])
    .filter((v) => v && v.snippet && v.snippet.channelId === channel)
    .map((v) => ({
      id: v.id,
      snippet: {
        title: v.snippet.title, channelId: v.snippet.channelId, channelTitle: v.snippet.channelTitle,
        description: (v.snippet.description || '').slice(0, 500), publishedAt: v.snippet.publishedAt,
        liveBroadcastContent: v.snippet.liveBroadcastContent, thumbnail: thumbnail(v.snippet),
      },
      contentDetails: v.contentDetails && { duration: v.contentDetails.duration, regionRestriction: v.contentDetails.regionRestriction },
      status: v.status && { embeddable: v.status.embeddable, privacyStatus: v.status.privacyStatus },
    }));
  return json(200, { channel, items }, cache);
}

module.exports = { handle, ALLOWED_CHANNELS, MAX_RESULTS };
