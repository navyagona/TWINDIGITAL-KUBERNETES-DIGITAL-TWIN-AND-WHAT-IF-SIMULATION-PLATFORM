import { Activity, Server, GitBranch, DollarSign, AlertTriangle, RefreshCw, CheckCircle2, type LucideIcon } from 'lucide-react';
import { useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import useAuthStore, { isViewerRole } from '../store/authStore';
import { usePolling } from '../hooks/usePolling';
import StatusPill from '../components/StatusPill';
import { healthTone } from '../components/statusTone';

interface SyncStatus {
  status: 'HEALTHY' | 'STALE' | 'NO_CLUSTER' | string;
  lagSeconds?: number;
  connected?: boolean;
  message?: string;
}

interface ServiceHealth {
  name: string;
  status: string;
  latencyMs: number;
  error?: string;
}

interface SimulationSummary {
  id?: string;
  simulationId?: string;
  riskLevel?: string;
  target?: string;
  changeSet?: { target?: string; kind?: string };
}

interface StatCardProps {
  title: string;
  value: string;
  sub?: string;
  icon: LucideIcon;
  badge?: string;
  badgeClass?: string;
}

function StatCard({ title, value, sub, icon: Icon, badgeClass, badge }: StatCardProps) {
  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="card-title">{title}</div>
          <div className="card-value">{value}</div>
          {sub && <div className="card-sub">{sub}</div>}
        </div>
        <div style={{ padding: '0.5rem', background: 'var(--color-surface-2)',
                      borderRadius: 'var(--radius-sm)', color: 'var(--color-accent)' }}>
          <Icon size={18} />
        </div>
      </div>
      {badge && <div className={`badge ${badgeClass}`} style={{ marginTop: '0.75rem' }}>{badge}</div>}
    </div>
  );
}

function syncBadge(status: SyncStatus | null): { label: string; cls: string } {
  if (!status) return { label: 'Unknown', cls: 'badge-yellow' };
  if (status.status === 'NO_CLUSTER') return { label: 'No cluster', cls: 'badge-yellow' };
  if (status.status === 'STALE') return { label: 'Stale', cls: 'badge-yellow' };
  return { label: 'Synced', cls: 'badge-blue' };
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const isViewer = isViewerRole(user);

  const [loading, setLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [nodeCount, setNodeCount] = useState('—');
  const [workloadCount, setWorkloadCount] = useState('—');
  const [simulations, setSimulations] = useState<SimulationSummary[]>([]);
  const [monthlyCost, setMonthlyCost] = useState('—');
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [overprovisionedCount, setOverprovisionedCount] = useState(0);
  const [services, setServices] = useState<ServiceHealth[]>([]);
  const [healthError, setHealthError] = useState(false);

  // Every request is independent: one service being down must not blank the others.
  const fetchDashboardData = useCallback(async (isBackground = false) => {
    if (!isBackground) setLoading(true);
    try {
      await Promise.allSettled([
        api.get<{ services: ServiceHealth[] }>('/api/v1/health/services')
          .then(({ data }) => { setServices(data.services ?? []); setHealthError(false); })
          .catch(() => setHealthError(true)),
        api.get<SyncStatus>('/api/v1/cluster/status').then(({ data }) => setSyncStatus(data)),
        api.get<unknown[]>('/api/v1/cluster/nodes')
          .then(({ data }) => { if (Array.isArray(data)) setNodeCount(String(data.length)); }),
        api.get<unknown[]>('/api/v1/cluster/deployments')
          .then(({ data }) => { if (Array.isArray(data)) setWorkloadCount(String(data.length)); }),
        // Only ADMIN can read /api/v1/simulations
        isViewer ? Promise.resolve() : api.get<SimulationSummary[]>('/api/v1/simulations')
          .then(({ data }) => { if (Array.isArray(data)) setSimulations(data); }),
        api.get<{ totalMonthlyCostUsd?: number; overprovisionedPodCount?: number }>('/api/v1/cost/summary')
          .then(({ data }) => {
            if (data?.totalMonthlyCostUsd != null) {
              setMonthlyCost(`$${data.totalMonthlyCostUsd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);
            }
            if (data?.overprovisionedPodCount != null) setOverprovisionedCount(data.overprovisionedPodCount);
          }),
      ]);
      setLastUpdated(new Date());
    } finally {
      if (!isBackground) setLoading(false);
    }
  }, [isViewer]);

  usePolling(fetchDashboardData);

  const badge = syncBadge(syncStatus);
  const upCount = services.filter((s) => s.status === 'UP').length;

  return (
    <div className="page-content">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 600 }}>Cluster Overview</h2>
          <p style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
            Live mirror of your EKS cluster
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.9rem' }}>
          <span className="live-indicator" aria-live="polite">
            <span className="dot" aria-hidden="true" />
            Auto-refresh 5s{lastUpdated ? ` · updated ${lastUpdated.toLocaleTimeString()}` : ''}
          </span>
          <button id="refresh-btn" className="btn btn-outline" onClick={() => fetchDashboardData(false)} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
        <StatCard
          title="Nodes"
          value={nodeCount}
          sub="Discovered in EKS"
          icon={Server}
          badge={nodeCount !== '—' ? 'Ready' : 'Pending'}
          badgeClass={nodeCount !== '—' ? 'badge-green' : 'badge-yellow'}
        />
        <StatCard
          title="Workloads"
          value={workloadCount}
          sub="Active Deployments"
          icon={GitBranch}
          badge={badge.label}
          badgeClass={badge.cls}
        />
        <StatCard
          title="Simulations Run"
          value={isViewer ? 'Restricted' : String(simulations.length)}
          sub={isViewer ? 'Admin access only' : 'Total audit records'}
          icon={Activity}
          badge={isViewer ? 'Viewer' : simulations.length > 0 ? `${simulations.length} runs` : 'No runs yet'}
          badgeClass={isViewer ? 'badge-blue' : simulations.length > 0 ? 'badge-green' : 'badge-yellow'}
        />
        <StatCard
          title="Est. Monthly Cost"
          value={monthlyCost}
          sub="Current cluster compute"
          icon={DollarSign}
        />
      </div>

      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-title" style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>Service Health</span>
          {services.length > 0 && (
            <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)', fontWeight: 400 }}>
              {upCount} of {services.length} up
            </span>
          )}
        </div>
        {healthError && services.length === 0 ? (
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem' }}>
            <StatusPill tone="critical" label="Unreachable" /> The gateway did not answer the health check.
          </div>
        ) : services.length === 0 ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>Checking services…</div>
        ) : (
          <div className="health-grid">
            {services.map((s) => (
              <div className="health-tile" key={s.name}>
                <span className="name">{s.name}</span>
                <StatusPill tone={healthTone(s.status)} label={s.status} title={s.error} />
                <span className="meta">{s.error ? s.error : `${s.latencyMs} ms`}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Recent Simulations</span>
            {!isViewer && (
              <Link to="/simulate" className="btn btn-outline btn-sm" style={{ fontSize: '0.72rem' }}>
                Run What-If
              </Link>
            )}
          </div>
          {isViewer ? (
            <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem 0' }}>
              Simulations are restricted to Administrator roles.
            </div>
          ) : simulations.length === 0 ? (
            <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem 0' }}>
              No simulations recorded yet. Run a what-if simulation to evaluate proposed cluster changes.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {simulations.slice(0, 5).map((sim, i) => (
                <div key={sim.id || sim.simulationId || i} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0.6rem 0.75rem',
                  background: 'var(--color-surface-2)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.8rem',
                }}>
                  <div>
                    <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>
                      {sim.changeSet?.target || sim.target || 'Cluster change'}
                    </span>
                    <span style={{ marginLeft: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.72rem' }}>
                      ({sim.changeSet?.kind || sim.riskLevel || 'SIMULATION'})
                    </span>
                  </div>
                  <span className={`badge ${
                    sim.riskLevel === 'CRITICAL' ? 'badge-red' :
                    sim.riskLevel === 'HIGH' ? 'badge-yellow' :
                    sim.riskLevel === 'MEDIUM' ? 'badge-blue' : 'badge-green'
                  }`}>
                    {sim.riskLevel || 'COMPLETED'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem' }}>Cluster Sync & Alerts</div>
          {syncStatus?.status === 'NO_CLUSTER' ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.85rem', padding: '1rem 0' }}>
              <StatusPill tone="warning" label="NO_CLUSTER" /> No Kubernetes cluster connected. Set KUBECONFIG_BASE64 in .env to enable live sync.
            </div>
          ) : syncStatus?.status === 'STALE' ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.85rem', padding: '1rem 0' }}>
              <StatusPill tone="warning" label="STALE" /> Cluster sync lag detected: {syncStatus.lagSeconds ?? 0}s behind K8s API.
            </div>
          ) : overprovisionedCount > 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.85rem', padding: '1rem 0' }}>
              <AlertTriangle size={16} color="var(--status-warning)" /> {overprovisionedCount} pod(s) flagged as over-provisioned based on 7-day p95 usage.
            </div>
          ) : syncStatus ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.85rem', padding: '2rem 0', justifyContent: 'center' }}>
              <CheckCircle2 size={16} color="var(--status-good)" /> All cluster informers healthy; zero active alerts
            </div>
          ) : (
            <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', padding: '2rem 0', textAlign: 'center' }}>
              Sync status unavailable: cluster-sync did not respond.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
