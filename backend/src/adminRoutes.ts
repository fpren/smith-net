import express from 'express';
import { authenticateToken, requireRole, UserRole } from './auth';
import { pg, isPgEnabled } from './db';

const router = express.Router();

// Tables the admin cleanup endpoint touches. Ordered so that truncation
// respects foreign-key expectations once we add them.
const CLEANUP_TABLES = [
  'messages', 'channel_members', 'channels',
  'work_logs', 'job_crew', 'materials', 'tasks', 'jobs',
  'plan_snapshots', 'plan_outputs', 'invoices', 'reports',
  'plan_summaries', 'proposals', 'plans', 'engagements',
  'organizations',
];

const COUNT_TABLES = [
  'profiles', 'organizations', 'channels', 'channel_members', 'messages',
  'jobs', 'tasks', 'materials', 'job_crew', 'work_logs',
  'plans', 'engagements', 'proposals', 'plan_summaries', 'reports',
  'invoices', 'plan_outputs', 'plan_snapshots',
];

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[Admin] Postgres client not initialized');
  return pg;
}

router.delete('/cleanup', authenticateToken, requireRole(UserRole.ADMIN), async (_req, res) => {
  try {
    const db = requirePg();
    console.log('[Admin] Starting complete database cleanup...');

    for (const t of CLEANUP_TABLES) {
      try {
        await db.query(`DELETE FROM ${t}`);
      } catch (err) {
        console.log(`[Admin] ${t} delete skipped:`, (err as Error).message);
      }
    }

    // Keep only the admin profile.
    try {
      await db.query(`DELETE FROM profiles WHERE email <> 'admin@smithnet.local'`);
    } catch (err) {
      console.log('[Admin] profiles delete error:', (err as Error).message);
    }

    console.log('[Admin] Database cleanup completed successfully!');

    const counts: Record<string, number> = {};
    for (const table of COUNT_TABLES) {
      try {
        const { rows } = await db.query(`SELECT COUNT(*)::int AS n FROM ${table}`);
        counts[table] = rows[0].n;
      } catch {
        counts[table] = 0;
      }
    }

    const { rows: adminRows } = await db.query(
      `SELECT id, email, display_name, role FROM profiles WHERE email = 'admin@smithnet.local' LIMIT 1`
    );

    res.json({
      success: true,
      message: 'Database cleanup completed successfully',
      adminPreserved: adminRows[0] || null,
      remainingData: counts,
    });
  } catch (error) {
    console.error('[Admin] Cleanup error:', error);
    res.status(500).json({
      success: false,
      message: 'Database cleanup failed',
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
});

router.get('/status', authenticateToken, requireRole(UserRole.ADMIN), async (_req, res) => {
  try {
    const db = requirePg();
    const counts: Record<string, number> = {};
    for (const table of COUNT_TABLES) {
      try {
        const { rows } = await db.query(`SELECT COUNT(*)::int AS n FROM ${table}`);
        counts[table] = rows[0].n;
      } catch {
        counts[table] = 0;
      }
    }

    const { rows: adminRows } = await db.query(
      `SELECT id, email, display_name, role FROM profiles WHERE email = 'admin@smithnet.local' LIMIT 1`
    );

    res.json({
      success: true,
      adminProfile: adminRows[0] || null,
      tableCounts: counts,
    });
  } catch (error) {
    console.error('[Admin] Status check error:', error);
    res.status(500).json({
      success: false,
      message: 'Status check failed',
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
});

export default router;
