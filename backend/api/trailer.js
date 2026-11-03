'use strict';

const { respond } = require('../lib/tmdb-proxy');
const { handle } = require('../lib/youtube-trailer');

module.exports = (req, res) => respond(req, res, (r) => handle(r, process.env, fetch));
