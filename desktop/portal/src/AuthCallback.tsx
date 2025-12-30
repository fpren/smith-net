import React, { useEffect, useState } from 'react';
import { supabaseAuth } from './supabaseAuth';

interface AuthCallbackProps {
  onAuthSuccess: () => void;
}

/**
 * Handle Supabase auth callback after email confirmation
 */
export default function AuthCallback({ onAuthSuccess }: AuthCallbackProps) {
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const handleAuthCallback = async () => {
      try {
        // Handle the auth callback
        const { data, error } = await supabaseAuth.getClient().auth.getSession();

        if (error) {
          console.error('[AuthCallback] Error:', error);
          setStatus('error');
          setMessage(error.message);
          return;
        }

        if (data.session) {
          console.log('[AuthCallback] Auth successful for:', data.session.user.email);
          setStatus('success');
          setMessage('Account confirmed! Redirecting to app...');

          // Redirect to app after successful auth
          setTimeout(() => {
            // Try to open the app with deep link
            const appUrl = `guildofsmiths://auth?token=${data.session.access_token}&refresh_token=${data.session.refresh_token}`;

            // For development, fallback to localhost
            const fallbackUrl = `http://localhost:8080?authenticated=true`;

            // Try app deep link first
            window.location.href = appUrl;

            // If app doesn't open within 2 seconds, show manual instructions
            setTimeout(() => {
              window.location.href = fallbackUrl;
            }, 2000);
          }, 2000);
        } else {
          setStatus('error');
          setMessage('No session found. Please try signing up again.');
        }
      } catch (error) {
        console.error('[AuthCallback] Unexpected error:', error);
        setStatus('error');
        setMessage('An unexpected error occurred. Please try again.');
      }
    };

    handleAuthCallback();
  }, [onAuthSuccess]);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      height: '100vh',
      flexDirection: 'column',
      gap: '16px',
      background: '#0a0a0a',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace"
    }}>
      {/* Guild of Smiths Branding */}
      <div style={{
        textAlign: 'center',
        marginBottom: '20px'
      }}>
        <h1 style={{
          fontSize: '28px',
          fontWeight: 'bold',
          letterSpacing: '3px',
          marginBottom: '8px',
          color: '#4a9eff'
        }}>
          GUILD OF SMITHS
        </h1>
        <div style={{
          fontSize: '12px',
          color: '#666',
          letterSpacing: '1px'
        }}>
          smith net • v1.0
        </div>
      </div>

      {/* Status Display */}
      <div style={{
        textAlign: 'center',
        padding: '32px',
        background: '#1a1a1a',
        border: '1px solid #333',
        borderRadius: '8px',
        maxWidth: '400px'
      }}>
        {status === 'loading' && (
          <>
            <div style={{ fontSize: '18px', marginBottom: '12px' }}>
              Confirming your account...
            </div>
            <div style={{
              width: '32px',
              height: '32px',
              border: '2px solid #333',
              borderTop: '2px solid #4a9eff',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite',
              margin: '0 auto'
            }} />
          </>
        )}

        {status === 'success' && (
          <>
            <div style={{
              fontSize: '18px',
              marginBottom: '12px',
              color: '#4ade80'
            }}>
              ✓ Account Confirmed!
            </div>
            <div style={{
              fontSize: '14px',
              color: '#888',
              lineHeight: '1.5'
            }}>
              {message}
            </div>
            <div style={{
              fontSize: '12px',
              color: '#666',
              marginTop: '16px'
            }}>
              If the app doesn't open automatically, please launch it manually.
            </div>
          </>
        )}

        {status === 'error' && (
          <>
            <div style={{
              fontSize: '18px',
              marginBottom: '12px',
              color: '#f87171'
            }}>
              ✗ Confirmation Failed
            </div>
            <div style={{
              fontSize: '14px',
              color: '#888',
              lineHeight: '1.5',
              marginBottom: '20px'
            }}>
              {message}
            </div>
            <button
              onClick={() => window.location.href = '/'}
              style={{
                background: '#1a1a1a',
                border: '1px solid #333',
                color: '#e0e0e0',
                fontFamily: "'Courier New', monospace",
                padding: '10px 20px',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              BACK TO SIGNUP
            </button>
          </>
        )}
      </div>

      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
