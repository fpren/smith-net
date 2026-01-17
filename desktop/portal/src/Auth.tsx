import React, { useState, useEffect } from 'react';
import { Auth as SupabaseAuth } from '@supabase/auth-ui-react';
import { ThemeSupa } from '@supabase/auth-ui-shared';
import { supabaseAuth } from './supabaseAuth';

interface AuthProps {
  onAuthSuccess: () => void;
  onSkipToPortal?: () => void;
}

/**
 * Guild of Smiths Authentication Component
 * Handles user registration and login with email confirmation
 */
export default function Auth({ onAuthSuccess, onSkipToPortal }: AuthProps) {
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    // Check if user is already authenticated
    const checkAuth = async () => {
      try {
        const session = await supabaseAuth.getSession();
        if (session) {
          setIsAuthenticated(true);
          onAuthSuccess();
        }
      } catch (error) {
        console.error('Auth check failed:', error);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();

    // Listen for auth state changes
    const { data: { subscription } } = supabaseAuth.onAuthStateChange((event, session) => {
      console.log('[Auth] State change:', event, session?.user?.email);

      if (event === 'SIGNED_IN' && session) {
        setIsAuthenticated(true);
        onAuthSuccess();
      } else if (event === 'SIGNED_OUT') {
        setIsAuthenticated(false);
      }
    });

    return () => subscription.unsubscribe();
  }, [onAuthSuccess]);

  if (isLoading) {
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
        <div>Loading Guild of Smiths...</div>
        <div style={{ fontSize: '12px', color: '#666' }}>smith net</div>
      </div>
    );
  }

  if (isAuthenticated) {
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
        <div>Welcome back!</div>
        <div style={{ fontSize: '12px', color: '#666' }}>Redirecting to app...</div>
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      background: '#0a0a0a',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace",
      padding: '20px'
    }}>
      <div style={{
        maxWidth: '400px',
        width: '100%',
        textAlign: 'center'
      }}>
        {/* Guild of Smiths Branding */}
        <div style={{
          marginBottom: '40px'
        }}>
          <h1 style={{
            fontSize: '32px',
            fontWeight: 'bold',
            letterSpacing: '4px',
            marginBottom: '8px',
            color: '#4a9eff'
          }}>
            GUILD OF SMITHS
          </h1>
          <div style={{
            fontSize: '14px',
            color: '#666',
            letterSpacing: '1px'
          }}>
            smith net • v1.0
          </div>
        </div>

        {/* Welcome Message */}
        <div style={{
          marginBottom: '32px'
        }}>
          <h2 style={{
            fontSize: '18px',
            marginBottom: '12px',
            color: '#e0e0e0'
          }}>
            Join the Guild
          </h2>
          <p style={{
            fontSize: '14px',
            color: '#888',
            lineHeight: '1.5',
            marginBottom: '24px'
          }}>
            Create your account to access the Smith Net platform.
            We'll send you a confirmation email to get started.
          </p>
        </div>

        {/* Supabase Auth UI */}
        <div style={{
          marginBottom: '32px'
        }}>
          <SupabaseAuth
            supabaseClient={supabaseAuth.getClient()}
            appearance={{
              theme: ThemeSupa,
              style: {
                button: {
                  background: '#1a1a1a',
                  border: '1px solid #333',
                  color: '#e0e0e0',
                  fontFamily: "'Courier New', monospace",
                  borderRadius: '4px',
                  padding: '10px 16px'
                },
                anchor: {
                  color: '#4a9eff',
                  textDecoration: 'none'
                },
                input: {
                  background: '#1a1a1a',
                  border: '1px solid #333',
                  color: '#e0e0e0',
                  fontFamily: "'Courier New', monospace",
                  borderRadius: '4px',
                  padding: '10px 12px'
                },
                label: {
                  color: '#e0e0e0',
                  fontFamily: "'Courier New', monospace",
                  fontSize: '14px'
                },
                message: {
                  color: '#888',
                  fontFamily: "'Courier New', monospace",
                  fontSize: '12px'
                }
              },
              variables: {
                default: {
                  colors: {
                    brand: '#4a9eff',
                    brandAccent: '#3a8eff'
                  }
                }
              }
            }}
            providers={[]}
            redirectTo={`${window.location.origin}/auth/callback`}
            onlyThirdPartyProviders={false}
            magicLink={false}
            showLinks={true}
            view="sign_up"
          />
        </div>

        {/* Skip Option for Development */}
        {onSkipToPortal && (
          <div style={{
            marginTop: '24px',
            padding: '16px',
            background: '#1a1a1a',
            border: '1px solid #333',
            borderRadius: '4px'
          }}>
            <div style={{
              fontSize: '12px',
              color: '#666',
              marginBottom: '12px'
            }}>
              Development Access
            </div>
            <button
              onClick={onSkipToPortal}
              style={{
                background: '#2a2a2a',
                border: '1px solid #444',
                color: '#888',
                fontFamily: "'Courier New', monospace",
                fontSize: '12px',
                padding: '8px 16px',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              SKIP TO PORTAL
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
