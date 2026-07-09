import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';

function validatePasswordClient(p: string): string | null {
  if (p.length < 8) return 'Password must be at least 8 characters';
  if (!/[a-zA-Z]/.test(p)) return 'Password must contain at least one letter';
  if (!/[0-9]/.test(p)) return 'Password must contain at least one digit';
  return null;
}

export function RegisterForm() {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const pwErr = validatePasswordClient(password);
    if (pwErr) {
      setError(pwErr);
      return;
    }
    setBusy(true);
    const result = await authClient.register(email, password, displayName);
    setBusy(false);
    if (!result.ok) {
      setError(result.error || 'Registration failed');
      return;
    }
    setUser(result.user);
    navigate('/console');
  }

  return (
    <Card title="Create Console Account" className="max-w-sm mx-auto mt-16">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <Input
          label="Display Name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
        />
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
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="text-sn-status-error text-xs">{error}</div>}
        <Button type="submit" disabled={busy}>
          {busy ? 'Creating...' : 'Create account'}
        </Button>
      </form>
    </Card>
  );
}
