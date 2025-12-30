-- ════════════════════════════════════════════════════════════════════════════
-- GUILD OF SMITHS DATABASE CLEANUP SCRIPT
-- Delete all data except admin user (admin@smithnet.local)
-- Run in Supabase SQL Editor (Dashboard → SQL Editor → New Query)
-- ════════════════════════════════════════════════════════════════════════════

-- Begin transaction for safety
BEGIN;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 1: DELETE ALL MESSAGES AND MEDIA
-- ════════════════════════════════════════════════════════════════════════════

-- Delete all messages
DELETE FROM messages;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 2: DELETE CHANNEL MEMBERS AND CHANNELS
-- ════════════════════════════════════════════════════════════════════════════

-- Delete channel memberships (except admin if they have any)
DELETE FROM channel_members
WHERE user_id NOT IN (
    SELECT id FROM profiles WHERE email = 'admin@smithnet.local'
);

-- Delete all channels
DELETE FROM channels;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 3: DELETE JOB-RELATED DATA
-- ════════════════════════════════════════════════════════════════════════════

-- Delete work logs
DELETE FROM work_logs;

-- Delete job crew members
DELETE FROM job_crew;

-- Delete materials
DELETE FROM materials;

-- Delete tasks
DELETE FROM tasks;

-- Delete job applications (if exists)
DELETE FROM job_applications;

-- Delete job proposals (if exists)
DELETE FROM job_proposals;

-- Delete time entries (if exists)
DELETE FROM time_entries;

-- Delete jobs
DELETE FROM jobs;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 4: DELETE PLAN MANAGEMENT DATA
-- ════════════════════════════════════════════════════════════════════════════

-- Delete plan snapshots
DELETE FROM plan_snapshots;

-- Delete plan outputs
DELETE FROM plan_outputs;

-- Delete invoices
DELETE FROM invoices;

-- Delete reports
DELETE FROM reports;

-- Delete plan summaries
DELETE FROM plan_summaries;

-- Delete proposals
DELETE FROM proposals;

-- Delete plans
DELETE FROM plans;

-- Delete engagements
DELETE FROM engagements;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 5: DELETE ORGANIZATIONS AND MEMBERSHIPS
-- ════════════════════════════════════════════════════════════════════════════

-- Delete organization memberships (except admin)
DELETE FROM organization_members
WHERE user_id NOT IN (
    SELECT id FROM profiles WHERE email = 'admin@smithnet.local'
);

-- Delete all organizations
DELETE FROM organizations;

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 6: DELETE ALL USER PROFILES EXCEPT ADMIN
-- ════════════════════════════════════════════════════════════════════════════

-- Delete all profiles except admin
DELETE FROM profiles
WHERE email != 'admin@smithnet.local';

-- ════════════════════════════════════════════════════════════════════════════
-- STEP 7: CLEAN UP SUPABASE STORAGE (OPTIONAL)
-- ════════════════════════════════════════════════════════════════════════════
-- Note: Storage cleanup requires API calls, do this manually in dashboard:
-- Dashboard → Storage → Delete all files in buckets

-- ════════════════════════════════════════════════════════════════════════════
-- VERIFICATION QUERIES
-- ════════════════════════════════════════════════════════════════════════════

-- Check remaining data (should only show admin profile and counts should be 0 or 1)
SELECT 'profiles' as table_name, COUNT(*) as count FROM profiles
UNION ALL
SELECT 'organizations' as table_name, COUNT(*) as count FROM organizations
UNION ALL
SELECT 'channels' as table_name, COUNT(*) as count FROM channels
UNION ALL
SELECT 'channel_members' as table_name, COUNT(*) as count FROM channel_members
UNION ALL
SELECT 'messages' as table_name, COUNT(*) as count FROM messages
UNION ALL
SELECT 'jobs' as table_name, COUNT(*) as count FROM jobs
UNION ALL
SELECT 'tasks' as table_name, COUNT(*) as count FROM tasks
UNION ALL
SELECT 'materials' as table_name, COUNT(*) as count FROM materials
UNION ALL
SELECT 'job_crew' as table_name, COUNT(*) as count FROM job_crew
UNION ALL
SELECT 'work_logs' as table_name, COUNT(*) as count FROM work_logs
UNION ALL
SELECT 'plans' as table_name, COUNT(*) as count FROM plans
UNION ALL
SELECT 'engagements' as table_name, COUNT(*) as count FROM engagements
UNION ALL
SELECT 'proposals' as table_name, COUNT(*) as count FROM proposals
UNION ALL
SELECT 'plan_summaries' as table_name, COUNT(*) as count FROM plan_summaries
UNION ALL
SELECT 'reports' as table_name, COUNT(*) as count FROM reports
UNION ALL
SELECT 'invoices' as table_name, COUNT(*) as count FROM invoices
UNION ALL
SELECT 'plan_outputs' as table_name, COUNT(*) as count FROM plan_outputs
UNION ALL
SELECT 'plan_snapshots' as table_name, COUNT(*) as count FROM plan_snapshots
ORDER BY table_name;

-- Verify admin still exists
SELECT id, email, display_name, role FROM profiles WHERE email = 'admin@smithnet.local';

-- Commit the transaction
COMMIT;

-- ════════════════════════════════════════════════════════════════════════════
-- SUCCESS MESSAGE
-- ════════════════════════════════════════════════════════════════════════════

-- If you see this, the cleanup was successful!
-- Only the admin user remains, all other data has been deleted.
