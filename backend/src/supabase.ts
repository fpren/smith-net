/**
 * Supabase Client Configuration
 * Guild of Smiths Backend
 */

import { createClient, SupabaseClient } from '@supabase/supabase-js';

// ════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ════════════════════════════════════════════════════════════════════

const SUPABASE_URL = process.env.SUPABASE_URL || '';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || '';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  console.warn('[Supabase] Missing SUPABASE_URL or SUPABASE_ANON_KEY - using fallback mode');
}

// ════════════════════════════════════════════════════════════════════
// CLIENTS
// ════════════════════════════════════════════════════════════════════

/**
 * Public client - uses anon key, respects RLS
 * Use for user-facing operations
 */
export const supabase: SupabaseClient = createClient(
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  {
    auth: {
      autoRefreshToken: true,
      persistSession: false,
    },
  }
);

/**
 * Admin client - uses service role key, bypasses RLS
 * Use only for server-side admin operations
 */
export const supabaseAdmin: SupabaseClient = createClient(
  SUPABASE_URL,
  SUPABASE_SERVICE_ROLE_KEY,
  {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  }
);

// ════════════════════════════════════════════════════════════════════
// DATABASE TYPES (auto-generate with `supabase gen types typescript`)
// ════════════════════════════════════════════════════════════════════

export interface Database {
  public: {
    Tables: {
      profiles: {
        Row: {
          id: string;
          email: string;
          display_name: string;
          role: 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';
          organization_id: string | null;
          phone: string | null;
          trade: string | null;
          hourly_rate: number;
          created_at: string;
          updated_at: string;
          is_active: boolean;
        };
        Insert: Omit<Database['public']['Tables']['profiles']['Row'], 'created_at' | 'updated_at'>;
        Update: Partial<Database['public']['Tables']['profiles']['Insert']>;
      };
      jobs: {
        Row: {
          id: string;
          organization_id: string | null;
          title: string;
          description: string | null;
          client_name: string | null;
          location: string | null;
          status: 'backlog' | 'todo' | 'in_progress' | 'review' | 'done' | 'archived';
          priority: 'low' | 'medium' | 'high' | 'urgent';
          created_by: string;
          assigned_to: string[] | null;
          crew_size: number;
          due_date: string | null;
          started_at: string | null;
          completed_at: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: Omit<Database['public']['Tables']['jobs']['Row'], 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Database['public']['Tables']['jobs']['Insert']>;
      };
      time_entries: {
        Row: {
          id: string;
          user_id: string;
          user_name: string;
          organization_id: string | null;
          job_id: string | null;
          job_title: string | null;
          clock_in_time: string;
          clock_out_time: string | null;
          clock_out_reason: string | null;
          duration_minutes: number | null;
          entry_type: 'regular' | 'overtime' | 'break' | 'travel' | 'on_call';
          source: 'manual' | 'geofence' | 'beacon' | 'mesh';
          status: 'active' | 'completed' | 'pending_review' | 'approved' | 'disputed';
          location: string | null;
          notes: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: Omit<Database['public']['Tables']['time_entries']['Row'], 'id' | 'created_at' | 'updated_at' | 'duration_minutes'>;
        Update: Partial<Database['public']['Tables']['time_entries']['Insert']>;
      };
      invoices: {
        Row: {
          id: string;
          invoice_number: string;
          organization_id: string | null;
          job_id: string | null;
          status: 'draft' | 'issued' | 'sent' | 'viewed' | 'paid' | 'overdue' | 'disputed' | 'cancelled';
          mode: 'solo' | 'enterprise';
          from_name: string;
          from_email: string | null;
          to_name: string | null;
          project_ref: string | null;
          issue_date: string;
          due_date: string;
          subtotal: number;
          tax_rate: number;
          tax_amount: number;
          total_due: number;
          created_at: string;
          updated_at: string;
        };
        Insert: Omit<Database['public']['Tables']['invoices']['Row'], 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Database['public']['Tables']['invoices']['Insert']>;
      };
      messages: {
        Row: {
          id: string;
          channel_id: string;
          sender_id: string;
          sender_name: string;
          content: string;
          origin: 'online' | 'mesh' | 'gateway' | 'online+mesh';
          media_type: string | null;
          media_url: string | null;
          is_deleted: boolean;
          created_at: string;
        };
        Insert: Omit<Database['public']['Tables']['messages']['Row'], 'id' | 'created_at' | 'is_deleted'>;
        Update: Partial<Database['public']['Tables']['messages']['Insert']>;
      };
      channels: {
        Row: {
          id: string;
          name: string;
          type: 'broadcast' | 'group' | 'dm' | 'job';
          creator_id: string;
          organization_id: string | null;
          job_id: string | null;
          mesh_hash: number | null;
          is_archived: boolean;
          is_deleted: boolean;
          created_at: string;
          updated_at: string;
        };
        Insert: Omit<Database['public']['Tables']['channels']['Row'], 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Database['public']['Tables']['channels']['Insert']>;
      };
      materials: {
        Row: {
          id: string;
          job_id: string;
          name: string;
          notes: string | null;
          quantity: number;
          unit: string;
          unit_cost: number;
          total_cost: number;
          vendor: string | null;
          receipt_url: string | null;
          is_checked: boolean;
          checked_at: string | null;
          checked_by: string | null;
          created_at: string;
        };
        Insert: Omit<Database['public']['Tables']['materials']['Row'], 'id' | 'created_at'>;
        Update: Partial<Database['public']['Tables']['materials']['Insert']>;
      };
      tasks: {
        Row: {
          id: string;
          job_id: string;
          title: string;
          description: string | null;
          status: 'pending' | 'in_progress' | 'done' | 'blocked';
          assigned_to: string | null;
          created_by: string;
          sort_order: number;
          completed_at: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: Omit<Database['public']['Tables']['tasks']['Row'], 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Database['public']['Tables']['tasks']['Insert']>;
      };
      work_logs: {
        Row: {
          id: string;
          job_id: string;
          author_id: string;
          text: string;
          log_type: 'note' | 'issue' | 'change_order' | 'completion';
          media_urls: string[] | null;
          created_at: string;
        };
        Insert: Omit<Database['public']['Tables']['work_logs']['Row'], 'id' | 'created_at'>;
        Update: Partial<Database['public']['Tables']['work_logs']['Insert']>;
      };
    };
  };
}

// ════════════════════════════════════════════════════════════════════
// STORAGE BUCKETS
// ════════════════════════════════════════════════════════════════════

export const STORAGE_BUCKETS = {
  IMAGES: 'images',
  VOICE_NOTES: 'voice-notes',
  DOCUMENTS: 'documents',
  RECEIPTS: 'receipts',
} as const;

// ════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ════════════════════════════════════════════════════════════════════

/**
 * Get a Supabase client authenticated with a user's JWT
 */
export function getAuthenticatedClient(accessToken: string): SupabaseClient {
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  });
}

/**
 * Upload file to Supabase Storage
 */
export async function uploadFile(
  bucket: string,
  path: string,
  file: Buffer | Blob,
  contentType: string
): Promise<string | null> {
  const { data, error } = await supabaseAdmin.storage
    .from(bucket)
    .upload(path, file, { contentType, upsert: true });

  if (error) {
    console.error('[Supabase] Upload error:', error);
    return null;
  }

  // Get public URL
  const { data: urlData } = supabaseAdmin.storage
    .from(bucket)
    .getPublicUrl(data.path);

  return urlData.publicUrl;
}

/**
 * Delete file from Supabase Storage
 */
export async function deleteFile(bucket: string, path: string): Promise<boolean> {
  const { error } = await supabaseAdmin.storage
    .from(bucket)
    .remove([path]);

  if (error) {
    console.error('[Supabase] Delete error:', error);
    return false;
  }

  return true;
}

console.log('[Supabase] Client initialized', SUPABASE_URL ? '✓' : '(fallback mode)');
