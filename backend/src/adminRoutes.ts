import express from 'express';
import { supabaseAdmin } from './supabase';
import { authenticateToken, requireRole, UserRole } from './auth';

const router = express.Router();

// ════════════════════════════════════════════════════════════════════════════
// ADMIN CLEANUP ROUTES
// ════════════════════════════════════════════════════════════════════════════

// DELETE /api/admin/cleanup - Complete database cleanup (admin only)
router.delete('/cleanup', authenticateToken, requireRole(UserRole.ADMIN), async (req, res) => {
  if (!supabaseAdmin) {
    return res.status(503).json({ error: 'Database not configured' });
  }

  try {
    console.log('[Admin] Starting complete database cleanup...');

    // Begin transaction
    const { error: beginError } = await supabaseAdmin.rpc('begin_transaction');
    if (beginError) {
      console.log('[Admin] Using individual deletes instead of transaction');
    }

    // Delete in order to respect foreign key constraints

    // 1. Messages and media
    console.log('[Admin] Deleting messages...');
    const { error: msgError } = await supabaseAdmin.from('messages').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (msgError) console.log('[Admin] Messages delete error:', msgError);

    // 2. Channel members and channels
    console.log('[Admin] Deleting channel members...');
    const { error: memError } = await supabaseAdmin.from('channel_members').delete().neq('channel_id', '00000000-0000-0000-0000-000000000000');
    if (memError) console.log('[Admin] Channel members delete error:', memError);

    console.log('[Admin] Deleting channels...');
    const { error: chanError } = await supabaseAdmin.from('channels').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (chanError) console.log('[Admin] Channels delete error:', chanError);

    // 3. Job-related data
    console.log('[Admin] Deleting work logs...');
    const { error: workLogError } = await supabaseAdmin.from('work_logs').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (workLogError) console.log('[Admin] Work logs delete error:', workLogError);

    console.log('[Admin] Deleting job crew...');
    const { error: crewError } = await supabaseAdmin.from('job_crew').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (crewError) console.log('[Admin] Job crew delete error:', crewError);

    console.log('[Admin] Deleting materials...');
    const { error: matError } = await supabaseAdmin.from('materials').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (matError) console.log('[Admin] Materials delete error:', matError);

    console.log('[Admin] Deleting tasks...');
    const { error: taskError } = await supabaseAdmin.from('tasks').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (taskError) console.log('[Admin] Tasks delete error:', taskError);

    console.log('[Admin] Deleting jobs...');
    const { error: jobError } = await supabaseAdmin.from('jobs').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (jobError) console.log('[Admin] Jobs delete error:', jobError);

    // 4. Plan management data
    console.log('[Admin] Deleting plan snapshots...');
    const { error: snapError } = await supabaseAdmin.from('plan_snapshots').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (snapError) console.log('[Admin] Plan snapshots delete error:', snapError);

    console.log('[Admin] Deleting plan outputs...');
    const { error: outputError } = await supabaseAdmin.from('plan_outputs').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (outputError) console.log('[Admin] Plan outputs delete error:', outputError);

    console.log('[Admin] Deleting invoices...');
    const { error: invError } = await supabaseAdmin.from('invoices').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (invError) console.log('[Admin] Invoices delete error:', invError);

    console.log('[Admin] Deleting reports...');
    const { error: repError } = await supabaseAdmin.from('reports').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (repError) console.log('[Admin] Reports delete error:', repError);

    console.log('[Admin] Deleting plan summaries...');
    const { error: sumError } = await supabaseAdmin.from('plan_summaries').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (sumError) console.log('[Admin] Plan summaries delete error:', sumError);

    console.log('[Admin] Deleting proposals...');
    const { error: propError } = await supabaseAdmin.from('proposals').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (propError) console.log('[Admin] Proposals delete error:', propError);

    console.log('[Admin] Deleting plans...');
    const { error: planError } = await supabaseAdmin.from('plans').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (planError) console.log('[Admin] Plans delete error:', planError);

    console.log('[Admin] Deleting engagements...');
    const { error: engError } = await supabaseAdmin.from('engagements').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (engError) console.log('[Admin] Engagements delete error:', engError);

    // 5. Organizations
    console.log('[Admin] Deleting organizations...');
    const { error: orgError } = await supabaseAdmin.from('organizations').delete().neq('id', '00000000-0000-0000-0000-000000000000');
    if (orgError) console.log('[Admin] Organizations delete error:', orgError);

    // 6. Delete all user profiles except admin
    console.log('[Admin] Deleting user profiles (keeping admin)...');
    const { error: profileError } = await supabaseAdmin
      .from('profiles')
      .delete()
      .neq('email', 'admin@smithnet.local');
    if (profileError) console.log('[Admin] Profiles delete error:', profileError);

    // Commit transaction if it was started
    if (!beginError) {
      const { error: commitError } = await supabaseAdmin.rpc('commit_transaction');
      if (commitError) console.log('[Admin] Commit error:', commitError);
    }

    console.log('[Admin] Database cleanup completed successfully!');

    // Get final counts
    const tables = [
      'profiles', 'organizations', 'channels', 'channel_members', 'messages',
      'jobs', 'tasks', 'materials', 'job_crew', 'work_logs',
      'plans', 'engagements', 'proposals', 'plan_summaries', 'reports', 'invoices', 'plan_outputs', 'plan_snapshots'
    ];

    const counts: Record<string, number> = {};
    for (const table of tables) {
      const { count, error } = await supabaseAdmin
        .from(table)
        .select('*', { count: 'exact', head: true });
      counts[table] = count || 0;
      if (error) console.log(`[Admin] Error counting ${table}:`, error);
    }

    // Verify admin still exists
    const { data: adminProfile, error: adminError } = await supabaseAdmin
      .from('profiles')
      .select('id, email, display_name, role')
      .eq('email', 'admin@smithnet.local')
      .single();

    res.json({
      success: true,
      message: 'Database cleanup completed successfully',
      adminPreserved: adminProfile,
      remainingData: counts
    });

  } catch (error) {
    console.error('[Admin] Cleanup error:', error);
    res.status(500).json({
      success: false,
      message: 'Database cleanup failed',
      error: error instanceof Error ? error.message : 'Unknown error'
    });
  }
});

// GET /api/admin/status - Get database status
router.get('/status', authenticateToken, requireRole(UserRole.ADMIN), async (req, res) => {
  if (!supabaseAdmin) {
    return res.json({ status: 'no_database', message: 'Supabase not configured' });
  }

  try {
    const tables = [
      'profiles', 'organizations', 'channels', 'channel_members', 'messages',
      'jobs', 'tasks', 'materials', 'job_crew', 'work_logs',
      'plans', 'engagements', 'proposals', 'plan_summaries', 'reports', 'invoices', 'plan_outputs', 'plan_snapshots'
    ];

    const counts: Record<string, number> = {};
    for (const table of tables) {
      const { count, error } = await supabaseAdmin
        .from(table)
        .select('*', { count: 'exact', head: true });
      counts[table] = count || 0;
      if (error) console.log(`[Admin] Error counting ${table}:`, error);
    }

    // Get admin profile
    const { data: adminProfile, error: adminError } = await supabaseAdmin
      .from('profiles')
      .select('id, email, display_name, role')
      .eq('email', 'admin@smithnet.local')
      .single();

    res.json({
      success: true,
      adminProfile,
      tableCounts: counts
    });

  } catch (error) {
    console.error('[Admin] Status check error:', error);
    res.status(500).json({
      success: false,
      message: 'Status check failed',
      error: error instanceof Error ? error.message : 'Unknown error'
    });
  }
});

export default router;
