import { Routes, Route } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';
import DashboardApp from './dashboard/App';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication, the chat portal, and the dashboard.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Auth />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/portal" element={<Portal />} />
      <Route path="/dashboard" element={<DashboardApp />} />
    </Routes>
  );
}
