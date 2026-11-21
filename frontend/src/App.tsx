import { lazy, Suspense, type ReactNode } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import SimulationPage from './pages/SimulationPage';
import CostRiskPage from './pages/CostRiskPage';
import useAuthStore, { isViewerRole } from './store/authStore';

const TopologyPage = lazy(() => import('./pages/TopologyPage'));

function PrivateLayout({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content">{children}</div>
    </div>
  );
}

function AdminOnlyRoute({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (isViewerRole(user)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={
        <PrivateLayout><DashboardPage /></PrivateLayout>
      } />
      <Route path="/topology" element={
        <PrivateLayout>
          <Suspense fallback={<div role="status">Loading topology…</div>}>
            <TopologyPage />
          </Suspense>
        </PrivateLayout>
      } />
      <Route path="/simulate" element={
        <PrivateLayout><AdminOnlyRoute><SimulationPage /></AdminOnlyRoute></PrivateLayout>
      } />
      <Route path="/cost-risk" element={
        <PrivateLayout><CostRiskPage /></PrivateLayout>
      } />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
