'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { handle, health } = require('../lib/tmdb-proxy');

const TOKEN = 'server-secret-token-value';

function upstream(status, body, headers) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    return { status, text: async () => body, headers: { get: (k) => (headers || {})[k.toLowerCase()] || null } };
  };
  return { calls, fetchImpl };
}
const get = (url, headers) => ({ method: 'GET', url, headers: headers || {} });

test('forwards whitelisted discover requests with the bearer token on the server only', async () => {
  const up = upstream(200, '{"page":1,"results":[]}');
  const res = await handle(get('/api/tmdb?path=/discover/movie&with_original_language=te&page=2&api_key=attacker&foo=bar'), { TMDB_READ_ACCESS_TOKEN: TOKEN }, up.fetchImpl);
  assert.strictEqual(res.status, 200);
  assert.strictEqual(res.body, '{"page":1,"results":[]}');
  const sent = new URL(up.calls[0].url);
  assert.strictEqual(sent.origin + sent.pathname, 'https://api.themoviedb.org/3/discover/movie');
  assert.strictEqual(sent.searchParams.get('with_original_language'), 'te');
  assert.strictEqual(sent.searchParams.get('page'), '2');
  assert.strictEqual(sent.searchParams.get('include_adult'), 'false');
  assert.strictEqual(sent.searchParams.get('api_key'), null, 'client-supplied api_key is dropped');
  assert.strictEqual(sent.searchParams.get('foo'), null, 'unknown parameters are dropped');
  assert.strictEqual(up.calls[0].init.headers.Authorization, `Bearer ${TOKEN}`);
  assert.ok(!res.body.includes(TOKEN) && !JSON.stringify(res.headers).includes(TOKEN));
  assert.match(res.headers['Cache-Control'], /s-maxage=900/);
});

test('uses the v3 api key only when no read access token is set', async () => {
  const up = upstream(200, '{}');
  await handle(get('/api/tmdb?path=/genre/movie/list'), { TMDB_API_KEY: 'v3key' }, up.fetchImpl);
  assert.strictEqual(new URL(up.calls[0].url).searchParams.get('api_key'), 'v3key');
  assert.strictEqual(up.calls[0].init.headers.Authorization, undefined);
});

test('rejects endpoints outside the catalog whitelist without calling TMDB', async () => {
  const up = upstream(200, '{}');
  for (const path of ['/account', '/authentication/token/new', '/movie/12/rating', '/../account', '/list/1']) {
    const res = await handle(get(`/api/tmdb?path=${encodeURIComponent(path)}`), { TMDB_READ_ACCESS_TOKEN: TOKEN }, up.fetchImpl);
    assert.strictEqual(res.status, 400, path);
  }
  const bad = await handle(get('/api/tmdb?path=/movie/5&append_to_response=account_states'), { TMDB_READ_ACCESS_TOKEN: TOKEN }, up.fetchImpl);
  assert.strictEqual(bad.status, 400);
  assert.strictEqual(up.calls.length, 0);
});

test('reports a missing credential as a diagnosable 503', async () => {
  const res = await handle(get('/api/tmdb?path=/discover/movie'), {}, upstream(200, '{}').fetchImpl);
  assert.strictEqual(res.status, 503);
  assert.strictEqual(JSON.parse(res.body).error, 'not_configured');
});

test('maps upstream auth failures and rate limits without leaking details', async () => {
  const denied = await handle(get('/api/tmdb?path=/discover/tv'), { TMDB_READ_ACCESS_TOKEN: TOKEN }, upstream(401, '{"status_message":"Invalid API key"}').fetchImpl);
  assert.strictEqual(denied.status, 502);
  assert.strictEqual(JSON.parse(denied.body).error, 'upstream_auth');
  const busy = await handle(get('/api/tmdb?path=/discover/tv'), { TMDB_READ_ACCESS_TOKEN: TOKEN }, upstream(429, '', { 'retry-after': '7' }).fetchImpl);
  assert.strictEqual(busy.status, 503);
  assert.strictEqual(busy.headers['Retry-After'], '7');
});

test('network failures become 504 instead of hanging', async () => {
  const res = await handle(get('/api/tmdb?path=/discover/tv'), { TMDB_READ_ACCESS_TOKEN: TOKEN }, async () => { throw new Error('ECONNRESET'); });
  assert.strictEqual(res.status, 504);
});

test('answers 304 when the client already has the same body', async () => {
  const env = { TMDB_READ_ACCESS_TOKEN: TOKEN };
  const first = await handle(get('/api/tmdb?path=/tv/1/season/2'), env, upstream(200, '{"episodes":[]}').fetchImpl);
  const second = await handle(get('/api/tmdb?path=/tv/1/season/2', { 'if-none-match': first.headers.ETag }), env, upstream(200, '{"episodes":[]}').fetchImpl);
  assert.strictEqual(second.status, 304);
  assert.strictEqual(second.body, '');
});

test('optional client token is enforced when configured', async () => {
  const env = { TMDB_READ_ACCESS_TOKEN: TOKEN, SAANDIO_CLIENT_TOKEN: 'tv-build-1' };
  assert.strictEqual((await handle(get('/api/tmdb?path=/discover/movie'), env, upstream(200, '{}').fetchImpl)).status, 401);
  assert.strictEqual((await handle(get('/api/tmdb?path=/discover/movie', { 'x-saandio-client': 'tv-build-1' }), env, upstream(200, '{}').fetchImpl)).status, 200);
});

test('only GET and HEAD are accepted', async () => {
  assert.strictEqual((await handle({ method: 'POST', url: '/api/tmdb?path=/discover/movie', headers: {} }, { TMDB_READ_ACCESS_TOKEN: TOKEN }, upstream(200, '{}').fetchImpl)).status, 405);
});

test('health reports configuration without exposing values', () => {
  const res = health({ TMDB_READ_ACCESS_TOKEN: TOKEN, YOUTUBE_API_KEY: 'yt-secret' });
  const body = JSON.parse(res.body);
  assert.deepStrictEqual(body, { ok: true, service: 'saandio-catalog-api', tmdbConfigured: true, auth: 'bearer', trailerSearchConfigured: true, clientTokenRequired: false });
  assert.ok(!res.body.includes(TOKEN) && !res.body.includes('yt-secret'));
  assert.strictEqual(JSON.parse(health({}).body).tmdbConfigured, false);
});
