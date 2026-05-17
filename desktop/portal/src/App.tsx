import { Routes, Route, Outlet } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';
import { LoginForm } from './console/auth/LoginForm';
import { RegisterForm } from './console/auth/RegisterForm';
import { RequireAuth } from './console/auth/RequireAuth';
import { ConsoleShell } from './console/ConsoleShell';
import { JobsListRoute } from './console/routes/JobsListRoute';
import { JobDetailRoute } from './console/routes/JobDetailRoute';
import { MapRoute } from './console/routes/MapRoute';
import { CrewRoute } from './console/routes/CrewRoute';
import { AdminRoute } from './console/routes/AdminRoute';
import { RequireAdmin } from './console/auth/RequireAdmin';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication, the chat portal, the dashboard, and the
 * operator console (foundation only — feature routes added in later plans).
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Auth onAuthSuccess={() => {}} />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/portal" element={<Portal />} />

      <Route path="/console/login" element={<LoginForm />} />
      <Route path="/console/register" element={<RegisterForm />} />
      <Route
        path="/console"
        element={
          <RequireAuth>
            <ConsoleShell><Outlet /></ConsoleShell>
          </RequireAuth>
        }
      >
        <Route index element={<MapRoute />} />
        <Route path="jobs" element={<JobsListRoute />} />
        <Route path="jobs/:id" element={<JobDetailRoute />} />
        <Route path="crew" element={<CrewRoute />} />
        <Route path="admin" element={<RequireAdmin><AdminRoute /></RequireAdmin>} />
      </Route>
    </Routes>
  );
}
