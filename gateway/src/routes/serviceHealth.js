'use strict';

const { Router } = require('express');

// Each backend exposes Spring Boot's /actuator/health; the gateway reports its own status directly.
const SERVICES = [
  { name: 'auth-service', envUrl: 'AUTH_SERVICE_URL', fallback: 'http://localhost:8081' },
  { name: 'cluster-sync', envUrl: 'CLUSTER_SYNC_SERVICE_URL', fallback: 'http://localhost:8082' },
  { name: 'topology-service', envUrl: 'TOPOLOGY_SERVICE_URL', fallback: 'http://localhost:8083' },
  { name: 'simulation-service', envUrl: 'SIMULATION_SERVICE_URL', fallback: 'http://localhost:8084' },
  { name: 'cost-risk-service', envUrl: 'COST_RISK_SERVICE_URL', fallback: 'http://localhost:8085' },
];

const TIMEOUT_MS = 2000;

async function probe(service, fetchImpl) {
  const base = process.env[service.envUrl] || service.fallback;
  const started = Date.now();
  try {
    const res = await fetchImpl(`${base}/actuator/health`, { signal: AbortSignal.timeout(TIMEOUT_MS) });
    let status = res.ok ? 'UP' : 'DOWN';
    try {
      const body = await res.json();
      if (body && typeof body.status === 'string') status = body.status;
    } catch {
      /* non-JSON body: keep the HTTP-derived status */
    }
    return { name: service.name, status, latencyMs: Date.now() - started };
  } catch (err) {
    return {
      name: service.name,
      status: 'DOWN',
      latencyMs: Date.now() - started,
      error: err.name === 'TimeoutError' ? `no response within ${TIMEOUT_MS}ms` : err.message,
    };
  }
}

/**
 * GET /api/v1/health/services
 * Probes every backend in parallel and returns one entry per service, so the dashboard can show
 * service health without each browser hitting internal ports.
 */
function createRouter(fetchImpl = globalThis.fetch) {
  const router = Router();
  router.get('/', async (_req, res) => {
    const results = await Promise.all(SERVICES.map((s) => probe(s, fetchImpl)));
    res.json({
      checkedAt: new Date().toISOString(),
      services: [{ name: 'gateway', status: 'UP', latencyMs: 0 }, ...results],
    });
  });
  return router;
}

module.exports = createRouter;
module.exports.SERVICES = SERVICES;
