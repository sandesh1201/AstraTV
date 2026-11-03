'use strict';

const { handle, respond } = require('../lib/tmdb-proxy');

module.exports = (req, res) => respond(req, res, (r) => handle(r, process.env, fetch));
