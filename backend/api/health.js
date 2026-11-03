'use strict';

const { health, respond } = require('../lib/tmdb-proxy');

module.exports = (req, res) => respond(req, res, async () => health(process.env));
