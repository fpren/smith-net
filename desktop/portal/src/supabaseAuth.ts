/**
 * Supabase Auth Client for Guild of Smiths Web Portal
 * Handles user authentication and registration
 */

import { createClient, SupabaseClient } from '@supabase/supabase-js';

// Supabase configuration (same as Android app)
const SUPABASE_URL = 'https://bhmeeuzjfniuocovwbyl.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobWVldXpqZm5pdW9jb3Z3YnlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY2MzEzNjksImV4cCI6MjA4MjIwNzM2OX0.SC_I94o68Q86rzaHi1Ojz_CeWa4rY7Le5y7b4-AyHgc';

class SupabaseAuthClient {
  private client: SupabaseClient;

  constructor() {
    this.client = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  }

  /**
   * Get the current user session
   */
  async getSession() {
    const { data: { session }, error } = await this.client.auth.getSession();
    if (error) {
      console.error('[Auth] Get session error:', error);
      return null;
    }
    return session;
  }

  /**
   * Get the current user
   */
  getUser() {
    return this.client.auth.getUser();
  }

  /**
   * Sign up a new user
   */
  async signUp(email: string, password: string, metadata?: { full_name?: string }) {
    const { data, error } = await this.client.auth.signUp({
      email,
      password,
      options: {
        data: metadata,
        emailRedirectTo: `${window.location.origin}/auth/callback`
      }
    });

    if (error) {
      console.error('[Auth] Sign up error:', error);
      throw error;
    }

    return data;
  }

  /**
   * Sign in an existing user
   */
  async signIn(email: string, password: string) {
    const { data, error } = await this.client.auth.signInWithPassword({
      email,
      password
    });

    if (error) {
      console.error('[Auth] Sign in error:', error);
      throw error;
    }

    return data;
  }

  /**
   * Sign out the current user
   */
  async signOut() {
    const { error } = await this.client.auth.signOut();
    if (error) {
      console.error('[Auth] Sign out error:', error);
      throw error;
    }
  }

  /**
   * Reset password
   */
  async resetPassword(email: string) {
    const { error } = await this.client.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/auth/reset-password`
    });

    if (error) {
      console.error('[Auth] Reset password error:', error);
      throw error;
    }
  }

  /**
   * Update password
   */
  async updatePassword(password: string) {
    const { error } = await this.client.auth.updateUser({
      password
    });

    if (error) {
      console.error('[Auth] Update password error:', error);
      throw error;
    }
  }

  /**
   * Listen for auth state changes
   */
  onAuthStateChange(callback: (event: string, session: any) => void) {
    return this.client.auth.onAuthStateChange(callback);
  }

  /**
   * Get the Supabase client (for use with other services)
   */
  getClient(): SupabaseClient {
    return this.client;
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return !!this.client.auth.getUser();
  }
}

export const supabaseAuth = new SupabaseAuthClient();
