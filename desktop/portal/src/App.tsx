import { Routes, Route } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';
import DashboardApp from './dashboard/App';
import { LoginForm } from './console/auth/LoginForm';
import { RegisterForm } from './console/auth/RegisterForm';
import { RequireAuth } from './console/auth/RequireAuth';
import { ConsoleShell } from './console/ConsoleShell';
import { PlaceholderConsoleRoute } from './console/routes/PlaceholderConsoleRoute';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication, the chat portal, the dashboard, and the
 * operator console (foundation only — feature routes added in later plans).
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Auth />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/portal" element={<Portal />} />
      <Route path="/dashboard" element={<DashboardApp />} />

      <Route path="/console/login" element={<LoginForm />} />
      <Route path="/console/register" element={<RegisterForm />} />
      <Route
        path="/console"
        element={
          <RequireAuth>
            <ConsoleShell>
              <PlaceholderConsoleRoute />
            </ConsoleShell>
          </RequireAuth>
        }
      />
    </Routes>
  );
}
