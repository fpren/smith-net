import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabaseAuth } from './supabaseAuth';

/**
 * Guild of Smiths Password Reset Component
 * Handles password reset flow after user clicks email link
 */
export default function ResetPassword() {
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  // Check if we have a valid session from the reset link
  useEffect(() => {
    const checkSession = async () => {
      const session = await supabaseAuth.getSession();
      if (!session) {
        setError('Invalid or expired reset link. Please request a new one.');
      }
    };
    checkSession();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setIsLoading(true);

    try {
      await supabaseAuth.updatePassword(password);
      setSuccess(true);
      setTimeout(() => {
        navigate('/portal');
      }, 2000);
    } catch (err) {
      setError((err as Error).message || 'Failed to update password');
    } finally {
      setIsLoading(false);
    }
  };

  const styles = {
    container: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      background: '#0a0a0a',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace",
      padding: '20px'
    },
    form: {
      maxWidth: '400px',
      width: '100%',
      textAlign: 'center' as const
    },
    brand: {
      fontSize: '32px',
      fontWeight: 'bold',
      letterSpacing: '4px',
      marginBottom: '8px',
      color: '#4a9eff'
    },
    subtitle: {
      fontSize: '14px',
      color: '#666',
      letterSpacing: '1px',
      marginBottom: '40px'
    },
    title: {
      fontSize: '18px',
      marginBottom: '24px',
      color: '#e0e0e0'
    },
    input: {
      width: '100%',
      background: '#1a1a1a',
      border: '1px solid #333',
      padding: '12px 14px',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace",
      fontSize: '14px',
      borderRadius: '4px',
      marginBottom: '16px',
      boxSizing: 'border-box' as const
    },
    button: {
      width: '100%',
      background: '#1a1a1a',
      border: '1px solid #4a9eff',
      padding: '12px 16px',
      color: '#4a9eff',
      fontFamily: "'Courier New', monospace",
      fontSize: '14px',
      cursor: 'pointer',
      borderRadius: '4px'
    },
    error: {
      color: '#f87171',
      fontSize: '14px',
      marginBottom: '16px',
      padding: '12px',
      background: '#2a1a1a',
      border: '1px solid #4a2a2a',
      borderRadius: '4px'
    },
    success: {
      color: '#4ade80',
      fontSize: '14px',
      marginBottom: '16px',
      padding: '12px',
      background: '#1a2a1a',
      border: '1px solid #2a4a2a',
      borderRadius: '4px'
    }
  };

  if (success) {
    return (
      <div style={styles.container}>
        <div style={styles.form}>
          <h1 style={styles.brand}>GUILD OF SMITHS</h1>
          <div style={styles.subtitle}>smith net • v1.0</div>
          <div style={styles.success}>
            Password updated successfully! Redirecting to portal...
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.form}>
        <h1 style={styles.brand}>GUILD OF SMITHS</h1>
        <div style={styles.subtitle}>smith net • v1.0</div>
        
        <h2 style={styles.title}>Reset Your Password</h2>

        {error && <div style={styles.error}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <input
            type="password"
            style={styles.input}
            placeholder="New password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
          />
          <input
            type="password"
            style={styles.input}
            placeholder="Confirm new password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            disabled={isLoading}
          />
          <button
            type="submit"
            style={{
              ...styles.button,
              opacity: isLoading ? 0.5 : 1
            }}
            disabled={isLoading}
          >
            {isLoading ? 'UPDATING...' : 'UPDATE PASSWORD'}
          </button>
        </form>
      </div>
    </div>
  );
}
