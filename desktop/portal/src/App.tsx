import { Routes, Route } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication and the main portal
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Auth />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/portal" element={<Portal />} />
    </Routes>
  );
}
