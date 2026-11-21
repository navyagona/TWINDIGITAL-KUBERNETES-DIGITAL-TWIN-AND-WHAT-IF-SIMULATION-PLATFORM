'use strict';

const express = require('express');
const request = require('supertest');
const createServiceHealthRouter = require('../routes/serviceHealth');

function appWith(fetchImpl) {
  const app = express();
  app.use('/api/v1/health/services', createServiceHealthRouter(fetchImpl));
  return app;
}

describe('GET /api/v1/health/services', () => {
  it('reports each backend from its actuator status and marks unreachable ones DOWN', async () => {
    const fetchImpl = jest.fn(async (url) => {
      if (url.includes('8082')) throw new Error('connect ECONNREFUSED');
      if (url.includes('8085')) return { ok: false, json: async () => ({ status: 'OUT_OF_SERVICE' }) };
      return { ok: true, json: async () => ({ status: 'UP' }) };
    });

    const res = await request(appWith(fetchImpl)).get('/api/v1/health/services');

    expect(res.status).toBe(200);
    const byName = Object.fromEntries(res.body.services.map((s) => [s.name, s]));
    expect(Object.keys(byName)).toEqual([
      'gateway', 'auth-service', 'cluster-sync', 'topology-service', 'simulation-service', 'cost-risk-service',
    ]);
    expect(byName.gateway.status).toBe('UP');
    expect(byName['auth-service'].status).toBe('UP');
    expect(byName['cluster-sync'].status).toBe('DOWN');
    expect(byName['cluster-sync'].error).toContain('ECONNREFUSED');
    expect(byName['cost-risk-service'].status).toBe('OUT_OF_SERVICE');
    expect(fetchImpl).toHaveBeenCalledWith('http://localhost:8081/actuator/health', expect.any(Object));
  });

  it('falls back to the HTTP status when the body is not JSON', async () => {
    const fetchImpl = jest.fn(async () => ({ ok: true, json: async () => { throw new Error('not json'); } }));

    const res = await request(appWith(fetchImpl)).get('/api/v1/health/services');

    expect(res.body.services.every((s) => s.status === 'UP')).toBe(true);
  });
});
