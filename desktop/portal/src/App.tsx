import { Routes, Route, Outlet, Navigate } from 'react-router-dom';
import { LoginForm } from './console/auth/LoginForm';
import { RegisterForm } from './console/auth/RegisterForm';
import { RequireAuth } from './console/auth/RequireAuth';
import { ConsoleShell } from './console/ConsoleShell';
import { JobsListRoute } from './console/routes/JobsListRoute';
import { JobDetailRoute } from './console/routes/JobDetailRoute';
import { ClientsListRoute } from './console/routes/ClientsListRoute';
import { ClientDetailRoute } from './console/routes/ClientDetailRoute';
import { MapRoute } from './console/routes/MapRoute';
import { CrewRoute } from './console/routes/CrewRoute';
import { CommRoute } from './console/routes/CommRoute';
import { InvoicesListRoute } from './console/routes/InvoicesListRoute';
import { InvoiceDetailRoute } from './console/routes/InvoiceDetailRoute';
import { AdminRoute } from './console/routes/AdminRoute';
import { RequireAdmin } from './console/auth/RequireAdmin';
import { RequireForemanRole } from './console/auth/RequireForemanRole';
import { SurfaceLabRoute } from './console/routes/SurfaceLabRoute';
import { AdaptiveHomeRoute } from './console/routes/AdaptiveHomeRoute';
import { SurfaceHomePreviewRoute } from './console/routes/SurfaceHomePreviewRoute';
import { SettingsRoute } from './console/routes/SettingsRoute';
import { TimeRoute } from './console/routes/TimeRoute';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication and the operator console.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/console" replace />} />
      <Route path="/surface-lab" element={<SurfaceLabRoute />} />
      <Route path="/home-preview" element={<SurfaceHomePreviewRoute />} />

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
        <Route index element={<RequireForemanRole><MapRoute /></RequireForemanRole>} />
        <Route path="home" element={<AdaptiveHomeRoute />} />
        <Route path="settings" element={<SettingsRoute />} />
        <Route path="time" element={<TimeRoute />} />
        <Route path="jobs" element={<RequireForemanRole><JobsListRoute /></RequireForemanRole>} />
        <Route path="jobs/:id" element={<RequireForemanRole><JobDetailRoute /></RequireForemanRole>} />
        <Route path="clients" element={<RequireForemanRole><ClientsListRoute /></RequireForemanRole>} />
        <Route path="clients/:id" element={<RequireForemanRole><ClientDetailRoute /></RequireForemanRole>} />
        <Route path="crew" element={<RequireForemanRole><CrewRoute /></RequireForemanRole>} />
        <Route path="comm" element={<CommRoute />} />
        <Route path="invoices" element={<RequireForemanRole><InvoicesListRoute /></RequireForemanRole>} />
        <Route path="invoices/:id" element={<RequireForemanRole><InvoiceDetailRoute /></RequireForemanRole>} />
        <Route path="admin" element={<RequireAdmin><AdminRoute /></RequireAdmin>} />
      </Route>
    </Routes>
  );
}
