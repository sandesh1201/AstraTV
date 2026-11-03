'use strict';

const crypto = require('node:crypto');

const TMDB_BASE = 'https://api.themoviedb.org/3';
const UPSTREAM_TIMEOUT_MS = 8000;

/** Read-only catalog endpoints the app uses. Anything else is rejected before reaching TMDB. */
const ALLOWED_PATHS = [
  /^\/trending\/(movie|tv|all)\/(day|week)$/,
  /^\/movie\/(now_playing|popular|upcoming|top_rated)$/,
  /^\/discover\/(movie|tv)$/,
  /^\/genre\/(movie|tv)\/list$/,
  /^\/search\/(multi|movie|tv)$/,
  /^\/(movie|tv)\/\d{1,9}$/,
  /^\/(movie|tv)\/\d{1,9}\/(watch\/providers|videos|credits|external_ids|release_dates|content_ratings)$/,
  /^\/tv\/\d{1,9}\/season\/\d{1,3}$/,
  /^\/person\/\d{1,9}$/,
  /^\/person\/\d{1,9}\/combined_credits$/,
];

const ALLOWED_PARAMS = new Set([
  'page', 'language', 'region', 'sort_by', 'query', 'year', 'include_adult',
  'primary_release_year', 'first_air_date_year',
  'with_original_language', 'with_genres', 'with_release_type', 'with_origin_country',
  'release_date.gte', 'release_date.lte', 'primary_release_date.gte', 'primary_release_date.lte',
  'first_air_date.gte', 'first_air_date.lte', 'air_date.gte', 'air_date.lte',
  'vote_average.gte', 'vote_count.gte', 'watch_region', 'with_watch_providers', 'append_to_response',
]);

const ALLOWED_APPENDS = new Set([
  'credits', 'videos', 'reviews', 'similar', 'release_dates', 'content_ratings', 'images', 'external_ids', 'watch/providers',
]);

function json(status, payload, extraHeaders) {
  return {
    status,
    headers: Object.assign({ 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }, extraHeaders || {}),
    body: JSON.stringify(payload),
  };
}

/** Which credential the server holds, without ever returning its value. */
function credential(env) {
  if (env.TMDB_READ_ACCESS_TOKEN && env.TMDB_READ_ACCESS_TOKEN.trim()) return { kind: 'bearer', value: env.TMDB_READ_ACCESS_TOKEN.trim() };
  if (env.TMDB_API_KEY && env.TMDB_API_KEY.trim()) return { kind: 'api_key', value: env.TMDB_API_KEY.trim() };
  return null;
}

function sameSecret(a, b) {
  const x = Buffer.from(String(a || ''));
  const y = Buffer.from(String(b || ''));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

function cacheControl(path) {
  if (/^\/(discover|trending|movie\/(now_playing|popular|upcoming|top_rated))/.test(path)) {
    return 'public, max-age=300, s-maxage=900, stale-while-revalidate=86400';
  }
  if (/^\/genre\//.test(path)) return 'public, max-age=86400, s-maxage=86400';
  if (/^\/search\//.test(path)) return 'public, max-age=120, s-maxage=600';
  return 'public, max-age=3600, s-maxage=21600, stale-while-revalidate=86400';
}

/** Validates the request and builds the upstream TMDB URL, or returns an error response. */
function buildUpstream(requestUrl, cred) {
  const url = new URL(requestUrl, 'http://localhost');
  const path = url.searchParams.get('path') || '';
  if (!ALLOWED_PATHS.some((re) => re.test(path))) {
    return { error: json(400, { error: 'unsupported_path', message: 'This catalog endpoint is not available.' }) };
  }
  const upstream = new URL(TMDB_BASE + path);
  for (const [name, value] of url.searchParams) {
    if (name === 'path' || !ALLOWED_PARAMS.has(name)) continue;
    if (value.length > 200) return { error: json(400, { error: 'bad_parameter', message: `Parameter ${name} is too long.` }) };
    if (name === 'append_to_response' && !value.split(',').every((part) => ALLOWED_APPENDS.has(part))) {
      return { error: json(400, { error: 'bad_parameter', message: 'Unsupported append_to_response value.' }) };
    }
    upstream.searchParams.set(name, value);
  }
  upstream.searchParams.set('include_adult', 'false');
  if (cred.kind === 'api_key') upstream.searchParams.set('api_key', cred.value);
  return { path, upstream };
}

/**
 * Handles one GET /api/tmdb?path=/discover/movie&... request.
 * @param {{method:string,url:string,headers:Object<string,string>}} req lower-case header names
 * @param {Object<string,string>} env process.env
 * @param {typeof fetch} fetchImpl
 * @returns {Promise<{status:number,headers:Object<string,string>,body:string}>}
 */
async function handle(req, env, fetchImpl) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return json(405, { error: 'method_not_allowed', message: 'Use GET.' }, { Allow: 'GET, HEAD' });
  if (env.SAANDIO_CLIENT_TOKEN && !sameSecret(req.headers['x-saandio-client'], env.SAANDIO_CLIENT_TOKEN)) {
    return json(401, { error: 'unknown_client', message: 'This client is not allowed to use the catalog service.' });
  }
  const cred = credential(env);
  if (!cred) return json(503, { error: 'not_configured', message: 'The catalog service has no TMDB credential configured.' }, { 'Retry-After': '300' });
  const built = buildUpstream(req.url, cred);
  if (built.error) return built.error;

  const headers = { Accept: 'application/json', 'User-Agent': 'SaandioCatalogAPI/1.0' };
  if (cred.kind === 'bearer') headers.Authorization = `Bearer ${cred.value}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  let upstream;
  try {
    upstream = await fetchImpl(built.upstream.toString(), { headers, signal: controller.signal });
  } catch (e) {
    console.error(`tmdb ${built.path} failed: ${e && e.name === 'AbortError' ? 'timeout' : 'network error'}`);
    return json(504, { error: 'upstream_unavailable', message: 'The metadata provider did not respond.' }, { 'Retry-After': '30' });
  } finally {
    clearTimeout(timer);
  }

  if (upstream.status === 401 || upstream.status === 403) {
    console.error(`tmdb ${built.path} rejected the server credential (HTTP ${upstream.status})`);
    return json(502, { error: 'upstream_auth', message: 'The catalog service credential was rejected by the metadata provider.' });
  }
  if (upstream.status === 429) {
    return json(503, { error: 'upstream_rate_limited', message: 'The metadata provider is busy. Try again shortly.' }, { 'Retry-After': upstream.headers.get('retry-after') || '10' });
  }
  if (upstream.status === 404) return json(404, { error: 'not_found', message: 'No such title.' });
  if (upstream.status < 200 || upstream.status >= 300) {
    console.error(`tmdb ${built.path} returned HTTP ${upstream.status}`);
    return json(502, { error: 'upstream_error', message: `The metadata provider returned HTTP ${upstream.status}.` }, { 'Retry-After': '30' });
  }

  const body = await upstream.text();
  const etag = `W/"${crypto.createHash('sha1').update(body).digest('base64url').slice(0, 27)}"`;
  const out = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': cacheControl(built.path), ETag: etag };
  if (req.headers['if-none-match'] === etag) return { status: 304, headers: out, body: '' };
  return { status: 200, headers: out, body };
}

function health(env) {
  const cred = credential(env);
  return json(200, { ok: true, service: 'saandio-catalog-api', tmdbConfigured: !!cred, auth: cred ? cred.kind : null, clientTokenRequired: !!env.SAANDIO_CLIENT_TOKEN });
}

/** Adapts handle()/health() to Node's req/res (Vercel functions and the local dev server). */
async function respond(req, res, compute) {
  const headers = {};
  for (const [k, v] of Object.entries(req.headers || {})) headers[k.toLowerCase()] = Array.isArray(v) ? v[0] : v;
  const result = await compute({ method: req.method, url: req.url, headers });
  res.statusCode = result.status;
  for (const [k, v] of Object.entries(result.headers)) res.setHeader(k, v);
  res.end(req.method === 'HEAD' ? '' : result.body);
}

module.exports = { handle, health, respond, ALLOWED_PATHS };
