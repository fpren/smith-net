import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';

export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const result = await authClient.login(email, password);
    setBusy(false);
    if (!result.ok) {
      setError(result.error || 'Invalid credentials');
      return;
    }
    setUser(result.user);
    navigate('/console');
  }

  return (
    <Card title="Console Login" className="max-w-sm mx-auto mt-16">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="text-sn-status-error text-xs">{error}</div>}
        <Button type="submit" disabled={busy}>
          {busy ? 'Logging in...' : 'Log in'}
        </Button>
      </form>
    </Card>
  );
}
