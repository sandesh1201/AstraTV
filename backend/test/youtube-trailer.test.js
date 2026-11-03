'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { handle } = require('../lib/youtube-trailer');

const KEY = 'server-youtube-key-value';
const get = (url, headers) => ({ method: 'GET', url, headers: headers || {} });

function upstream(responses) {
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(url);
    const r = responses[calls.length - 1];
    return { status: r.status, json: async () => r.body };
  };
  return { calls, fetchImpl };
}

const video = {
  id: 'aaaaaaaaaaa',
  snippet: { title: 'Pushpa 2 - Official Trailer', channelTitle: 'Mythri Movie Makers', description: 'x'.repeat(900), liveBroadcastContent: 'none', thumbnails: {} },
  contentDetails: { duration: 'PT2M31S' },
  status: { embeddable: true, privacyStatus: 'public', license: 'youtube' },
};

test('searches embeddable videos and returns their details without the server key', async () => {
  const up = upstream([
    { status: 200, body: { items: [{ id: { videoId: 'aaaaaaaaaaa' } }, { id: { channelId: 'x' } }, { id: { videoId: 'bad' } }] } },
    { status: 200, body: { items: [video] } },
  ]);
  const res = await handle(get('/api/trailer?q=Pushpa%202%202024%20Telugu%20official%20trailer'), { YOUTUBE_API_KEY: KEY }, up.fetchImpl);
  assert.strictEqual(res.status, 200);
  const search = new URL(up.calls[0]);
  assert.strictEqual(search.pathname, '/youtube/v3/search');
  assert.strictEqual(search.searchParams.get('videoEmbeddable'), 'true');
  assert.strictEqual(search.searchParams.get('type'), 'video');
  assert.strictEqual(search.searchParams.get('q'), 'Pushpa 2 2024 Telugu official trailer');
  const videos = new URL(up.calls[1]);
  assert.strictEqual(videos.searchParams.get('id'), 'aaaaaaaaaaa', 'only valid video IDs are looked up');
  assert.strictEqual(videos.searchParams.get('part'), 'snippet,contentDetails,status');
  const body = JSON.parse(res.body);
  assert.strictEqual(body.items[0].status.embeddable, true);
  assert.strictEqual(body.items[0].contentDetails.duration, 'PT2M31S');
  assert.strictEqual(body.items[0].snippet.description.length, 500);
  assert.ok(!res.body.includes(KEY) && !JSON.stringify(res.headers).includes(KEY));
  assert.match(res.headers['Cache-Control'], /s-maxage=86400/);
});

test('answers 503 not_configured without a key and never calls YouTube', async () => {
  const up = upstream([]);
  const res = await handle(get('/api/trailer?q=x'), {}, up.fetchImpl);
  assert.strictEqual(res.status, 503);
  assert.strictEqual(JSON.parse(res.body).error, 'not_configured');
  assert.strictEqual(up.calls.length, 0);
});

test('rejects missing or oversized queries, other methods and unknown clients', async () => {
  const up = upstream([]);
  assert.strictEqual((await handle(get('/api/trailer'), { YOUTUBE_API_KEY: KEY }, up.fetchImpl)).status, 400);
  assert.strictEqual((await handle(get('/api/trailer?q=' + 'a'.repeat(200)), { YOUTUBE_API_KEY: KEY }, up.fetchImpl)).status, 400);
  assert.strictEqual((await handle({ method: 'POST', url: '/api/trailer?q=x', headers: {} }, { YOUTUBE_API_KEY: KEY }, up.fetchImpl)).status, 405);
  const env = { YOUTUBE_API_KEY: KEY, SAANDIO_CLIENT_TOKEN: 'client' };
  assert.strictEqual((await handle(get('/api/trailer?q=x'), env, up.fetchImpl)).status, 401);
  assert.strictEqual(up.calls.length, 0);
});

test('maps quota exhaustion and empty results', async () => {
  const quota = await handle(get('/api/trailer?q=x'), { YOUTUBE_API_KEY: KEY }, upstream([{ status: 403, body: null }]).fetchImpl);
  assert.strictEqual(quota.status, 503);
  assert.strictEqual(JSON.parse(quota.body).error, 'upstream_quota');
  const up = upstream([{ status: 200, body: { items: [] } }]);
  const empty = await handle(get('/api/trailer?q=x'), { YOUTUBE_API_KEY: KEY }, up.fetchImpl);
  assert.deepStrictEqual(JSON.parse(empty.body), { items: [] });
  assert.strictEqual(up.calls.length, 1, 'no videos.list call when search finds nothing');
});
