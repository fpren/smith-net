import { Routes, Route, useNavigate } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';
import Planner from './Planner';
import ResetPassword from './ResetPassword';

function AuthWithSkip() {
  const navigate = useNavigate();
  return (
    <Auth
      onAuthSuccess={() => navigate('/portal')}
      onSkipToPortal={() => navigate('/portal')}
    />
  );
}

/**
 * Guild of Smiths Web Portal
 * Routes between authentication and the main portal
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AuthWithSkip />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/auth/reset-password" element={<ResetPassword />} />
      <Route path="/portal" element={<Portal />} />
      <Route path="/planner" element={<Planner />} />
    </Routes>
  );
}
