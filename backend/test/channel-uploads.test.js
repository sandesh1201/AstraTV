'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { handle, ALLOWED_CHANNELS } = require('../lib/channel-uploads');

const KEY = 'server-youtube-key-value';
const DD_TAMIL = 'UC5-_zkjKabYd7tsLucKsriw';
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

const episode = {
  id: 'bbbbbbbbbbb',
  snippet: {
    title: 'Serial Name | Episode 12 | DD Tamil', channelId: DD_TAMIL, channelTitle: 'DD Tamil', description: 'd'.repeat(800),
    publishedAt: '2026-11-02T14:30:00Z', liveBroadcastContent: 'none',
    thumbnails: { high: { url: 'https://i.ytimg.com/vi/bbbbbbbbbbb/hqdefault.jpg' }, default: { url: 'http://insecure/x.jpg' } },
  },
  contentDetails: { duration: 'PT22M10S' },
  status: { embeddable: true, privacyStatus: 'public' },
};

test('lists the uploads playlist of an allowlisted channel without exposing the key', async () => {
  const foreign = { ...episode, id: 'ccccccccccc', snippet: { ...episode.snippet, channelId: 'UCxxxxxxxxxxxxxxxxxxxxxx' } };
  const up = upstream([
    { status: 200, body: { items: [{ contentDetails: { videoId: 'bbbbbbbbbbb' } }, { contentDetails: { videoId: 'ccccccccccc' } }, { contentDetails: { videoId: 'bad' } }] } },
    { status: 200, body: { items: [episode, foreign] } },
  ]);
  const res = await handle(get(`/api/channel-uploads?channel=${DD_TAMIL}`), { YOUTUBE_API_KEY: KEY }, up.fetchImpl);
  assert.strictEqual(res.status, 200);
  const list = new URL(up.calls[0]);
  assert.strictEqual(list.pathname, '/youtube/v3/playlistItems');
  assert.strictEqual(list.searchParams.get('playlistId'), 'UU5-_zkjKabYd7tsLucKsriw');
  const videos = new URL(up.calls[1]);
  assert.strictEqual(videos.searchParams.get('id'), 'bbbbbbbbbbb,ccccccccccc');
  const body = JSON.parse(res.body);
  assert.strictEqual(body.items.length, 1, 'videos from other channels are dropped');
  assert.strictEqual(body.items[0].snippet.thumbnail, 'https://i.ytimg.com/vi/bbbbbbbbbbb/hqdefault.jpg');
  assert.strictEqual(body.items[0].snippet.description.length, 500);
  assert.strictEqual(body.items[0].snippet.publishedAt, '2026-11-02T14:30:00Z');
  assert.ok(!res.body.includes(KEY) && !JSON.stringify(res.headers).includes(KEY));
});

test('refuses channels outside the official allowlist and malformed ids', async () => {
  const up = upstream([]);
  const env = { YOUTUBE_API_KEY: KEY };
  assert.strictEqual((await handle(get('/api/channel-uploads?channel=UCBnxEdpoZwstJqC1yZpOjRB'), env, up.fetchImpl)).status, 403);
  assert.strictEqual((await handle(get('/api/channel-uploads?channel=PLabc'), env, up.fetchImpl)).status, 400);
  assert.strictEqual((await handle(get('/api/channel-uploads'), env, up.fetchImpl)).status, 400);
  assert.strictEqual((await handle({ method: 'POST', url: `/api/channel-uploads?channel=${DD_TAMIL}`, headers: {} }, env, up.fetchImpl)).status, 405);
  assert.strictEqual((await handle(get(`/api/channel-uploads?channel=${DD_TAMIL}`), { ...env, SAANDIO_CLIENT_TOKEN: 'client' }, up.fetchImpl)).status, 401);
  assert.strictEqual(up.calls.length, 0);
});

test('answers not_configured without a key and maps quota errors', async () => {
  const none = await handle(get(`/api/channel-uploads?channel=${DD_TAMIL}`), {}, upstream([]).fetchImpl);
  assert.strictEqual(none.status, 503);
  assert.strictEqual(JSON.parse(none.body).error, 'not_configured');
  const quota = await handle(get(`/api/channel-uploads?channel=${DD_TAMIL}`), { YOUTUBE_API_KEY: KEY }, upstream([{ status: 403, body: null }]).fetchImpl);
  assert.strictEqual(JSON.parse(quota.body).error, 'upstream_quota');
});

test('allowlist holds only well-formed channel ids', () => {
  for (const id of ALLOWED_CHANNELS.keys()) assert.match(id, /^UC[A-Za-z0-9_-]{22}$/);
});
