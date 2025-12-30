# Supabase Setup for Guild of Smiths

## 🚀 Quick Start

### Step 1: Create Supabase Project

1. Go to [supabase.com](https://supabase.com) and sign up/login
2. Click "New Project"
3. Choose your organization
4. Set project name: `guild-of-smiths`
5. Set a strong database password (save it!)
6. Select your region (closest to your users)
7. Click "Create new project"

### Step 2: Get Your Credentials

Once your project is created (takes ~2 minutes):

1. Go to **Settings** → **API**
2. Copy these values:
   - **Project URL**: `https://xxxxx.supabase.co`
   - **anon public key**: `eyJhbG...` (safe to use in clients)
   - **service_role key**: `eyJhbG...` (NEVER expose to clients!)

### Step 3: Run Database Migrations

1. Go to **SQL Editor** in your Supabase dashboard
2. Click **New query**
3. Paste the contents of `supabase/migrations/001_initial_schema.sql`
4. Click **Run** (or Ctrl+Enter)

This creates all tables:
- `profiles` - User profiles (extends Supabase Auth)
- `organizations` - Business entities
- `channels` - SmithNet messaging channels
- `messages` - Chat messages
- `jobs` - Job Board jobs
- `tasks` - Job tasks
- `materials` - Job materials with costs
- `work_logs` - Job notes/logs
- `time_entries` - Time Clock entries
- `invoices` - Generated invoices
- `invoice_line_items` - Invoice details
- `audit_logs` - Audit trail

### Step 4: Create Storage Buckets

1. Go to **Storage** in your Supabase dashboard
2. Click **New bucket** and create:
   - `images` - For job photos
   - `voice-notes` - For voice memos
   - `documents` - For PDFs/documents
   - `receipts` - For material receipts

3. For each bucket, set policies:
   - **SELECT**: Allow authenticated users to read their own files
   - **INSERT**: Allow authenticated users to upload
   - **DELETE**: Allow authenticated users to delete their own files

### Step 5: Configure Backend

1. Create `.env` file in `backend/`:

```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
JWT_SECRET=your-jwt-secret-for-legacy-auth
PORT=3000
```

2. Install dependencies:
```bash
cd backend
npm install
```

3. Start the server:
```bash
npm run dev
```

### Step 6: Configure Android App

1. Open `android/app/src/main/java/com/guildofsmiths/trademesh/data/SupabaseClient.kt`

2. Replace the placeholder values:
```kotlin
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_ANON_KEY = "your-anon-key"
```

3. **Configure AI Assistant** (Optional):
   - The app includes an embedded AI assistant with two modes:
     - **Standard Mode**: Local rule-based AI, always available, zero battery drain
     - **Hybrid Mode**: Local rules + cloud AI when connected and charged
   - AI settings are configured in-app via Settings → AI Assistant
   - Default is Standard Mode (recommended for field work)

4. Sync Gradle and rebuild the app

---

## 📊 Database Schema Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   auth.users│────▶│  profiles   │────▶│organizations│
└─────────────┘     └─────────────┘     └─────────────┘
                           │
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│    jobs     │     │time_entries │     │  channels   │
└─────────────┘     └─────────────┘     └─────────────┘
        │                  │                  │
   ┌────┴────┐            │             ┌────┴────┐
   ▼         ▼            ▼             ▼         ▼
┌──────┐ ┌────────┐  ┌─────────┐  ┌─────────┐ ┌──────────┐
│tasks │ │materials│ │invoices │  │messages │ │channel_  │
└──────┘ └────────┘  └─────────┘  └─────────┘ │members   │
                          │                   └──────────┘
                     ┌────┴────┐
                     ▼         ▼
              ┌──────────┐ ┌──────────────┐
              │line_items│ │daily_breakdown│
              └──────────┘ └──────────────┘
```

---

## 🤖 Embedded AI Assistant

The mobile app includes an ambient AI assistant that provides contextual help without explicit commands. The AI operates in two modes:

### Standard Mode (Default)
- **Always-on, local rule-based AI**
- **Zero battery drain** when idle
- **Deterministic responses** for common trade scenarios:
  - Clock in/out confirmations
  - Material request acknowledgments
  - Trade-specific checklists (electrical, plumbing, HVAC, etc.)
  - Non-English text translation (cached phrases)
  - Job status updates
- **Mesh-friendly payloads** (tiny, compressed)

### Hybrid Mode (Optional)
- **Combines local rules with cloud AI**
- **Gated by conditions**: Internet + battery (>20%) + thermal state
- **Graceful fallback** to Standard mode when conditions not met
- **Offline queuing** - responses sync when connectivity returns
- **Battery-aware** - automatically degrades based on device state

### AI Configuration
- **Settings** → **AI Assistant** to toggle modes
- **Battery Gate** automatically disables when battery <15%
- **Thermal Gate** reduces load when device is hot
- **Offline Queue** preserves responses for sync when online

### AI Event Sources
The AI observes these app events contextually:
- Incoming mesh/IP messages
- Time entry creations/edits
- Job/checklist views/edits
- Screen navigation changes
- Connectivity state changes
- Battery level changes
- Location/geofence events

---

## 🔐 Row Level Security (RLS)

All tables have RLS enabled. Key policies:

| Table | SELECT | INSERT | UPDATE | DELETE |
|-------|--------|--------|--------|--------|
| profiles | All users | Auto on signup | Own only | Admin |
| jobs | Own + org | Own | Own + org | Own |
| time_entries | Own | Own | Own | Own |
| invoices | Own + org | Own | Own | Admin |
| messages | Channel members | Channel members | Own | Own |

---

## 🔄 Real-time Subscriptions

These tables broadcast real-time updates:
- `messages` - For live chat
- `channels` - For channel updates
- `time_entries` - For live time tracking
- `jobs` - For job board updates

Example usage in Android:
```kotlin
val channel = supabase.realtime.channel("messages")
channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
    table = "messages"
}.collect { change ->
    // Handle new message
}
channel.subscribe()
```

---

## 📁 Storage Structure

```
images/
├── {user_id}/
│   ├── {job_id}/
│   │   ├── before_001.jpg
│   │   ├── during_001.jpg
│   │   └── after_001.jpg
│   └── profile.jpg

voice-notes/
├── {user_id}/
│   └── {job_id}/
│       └── note_001.m4a

receipts/
├── {user_id}/
│   └── {material_id}/
│       └── receipt.jpg
```

---

## 🔌 API Endpoints (Hybrid Mode)

The backend can run in hybrid mode - using Supabase for storage while keeping existing WebSocket/REST endpoints:

| Endpoint | Purpose | Storage |
|----------|---------|---------|
| POST /api/auth/login | Login | Supabase Auth |
| POST /api/auth/register | Register | Supabase Auth |
| GET /api/jobs | List jobs | Supabase Postgres |
| POST /api/jobs | Create job | Supabase Postgres |
| WS / | Real-time chat | Supabase Realtime + WebSocket |
| POST /api/media/upload | Upload files | Supabase Storage |

---

## 🧪 Testing

1. **Auth Test**:
```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123","displayName":"Test User"}'
```

2. **Database Test**:
   - Go to Supabase Dashboard → Table Editor
   - Check that data appears in tables after API calls

3. **Real-time Test**:
   - Open two browser tabs to your app
   - Send a message in one, see it appear in the other

---

## 🚨 Troubleshooting

### "JWT expired" errors
- Check that your Supabase keys are correct
- Ensure the client is refreshing tokens

### "RLS policy violation"
- Check that the user is authenticated
- Verify the RLS policies allow the operation
- Check the `auth.uid()` matches expected user

### "Storage permission denied"
- Create bucket policies in Supabase dashboard
- Ensure user is authenticated before upload

### Real-time not working
- Verify table is added to `supabase_realtime` publication
- Check WebSocket connection in browser devtools
- Ensure RLS allows SELECT on the table

---

## 📚 Resources

- [Supabase Docs](https://supabase.com/docs)
- [Supabase Kotlin SDK](https://github.com/supabase-community/supabase-kt)
- [Row Level Security Guide](https://supabase.com/docs/guides/auth/row-level-security)
- [Realtime Guide](https://supabase.com/docs/guides/realtime)

---

**Guild of Smiths – Built for the trades.**
