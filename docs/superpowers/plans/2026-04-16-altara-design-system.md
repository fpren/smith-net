# Altara Design System Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SmithNet's cool-toned saturated design system with the warm utilitarian Altara aesthetic across all scaffold files.

**Architecture:** Token-based refactor — define the entire design system in CSS variables and Tailwind config, then sweep each component file replacing hardcoded colors, fonts, borders, and shadows with Altara equivalents. No structural/logic changes.

**Tech Stack:** React 18, TypeScript, Vite 5, Tailwind CSS 3, Zustand

**Working directory:** `/tmp/smithnet-scaffold`

---

### Task 1: CSS Variables & Google Fonts

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/index.css`

- [ ] **Step 1: Replace the Google Fonts import**

Replace line 4:
```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap');
```
With:
```css
@import url('https://fonts.googleapis.com/css2?family=Syne:wght@600;700;800&family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap');
```

- [ ] **Step 2: Replace all CSS variables**

Replace the entire `:root` block (lines 8–27) with:
```css
:root {
  /* Surfaces */
  --bg:             #F4F2EE;
  --card:           #FAFAF8;
  --surface-hover:  #F7F5F2;
  --surface-inset:  #F0EDE8;
  --surface-border: #E8E4DE;

  /* Dark surfaces */
  --dk:       #3A352E;
  --dk-hover: #4A443C;
  --dk-text:  #E8E4DE;
  --dk-text2: #8C8478;

  /* Accents */
  --gold:   #9A6F2E;
  --sage:   #5A8C76;
  --slate:  #3A6A8C;
  --dusty:  #6A4A8C;
  --brick:  #8C3A3A;
  --sienna: #8C5A2E;

  /* Text */
  --tx:  #2A2520;
  --tx2: #5C5347;
  --tx3: #8C8478;

  /* Shadows */
  --s1: 0 1px 3px rgba(0,0,0,.04), 0 4px 12px rgba(0,0,0,.03);
  --s2: 0 4px 12px rgba(0,0,0,.06), 0 16px 40px rgba(0,0,0,.08);
  --s3: 0 8px 24px rgba(0,0,0,.1), 0 40px 100px rgba(0,0,0,.15);

  /* Typography */
  --font-display: 'Syne', sans-serif;
  --font-mono:    'IBM Plex Mono', monospace;
  --font-body:    'IBM Plex Sans', sans-serif;
}
```

- [ ] **Step 3: Update base styles**

Replace the `html, body, #root` rule (line 33–41) with:
```css
html, body, #root {
  height: 100%;
  background: var(--bg);
  color: var(--tx);
  font-family: var(--font-body);
  font-size: 12px;
  overflow: hidden;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
```

- [ ] **Step 4: Update scrollbar and animation colors**

Replace the scrollbar thumb (line 50):
```css
::-webkit-scrollbar-thumb { background: var(--surface-border); border-radius: 2px; }
```

Replace the `ripple` keyframe colors (lines 58–62):
```css
@keyframes ripple {
  0%   { box-shadow: 0 0 0 0 rgba(154,111,46,.4); }
  70%  { box-shadow: 0 0 0 8px rgba(154,111,46,0); }
  100% { box-shadow: 0 0 0 0 rgba(154,111,46,0); }
}
```

- [ ] **Step 5: Verify the dev server reloads**

Run: `curl -s http://localhost:3000 | head -3`
Expected: HTML with no errors. Check browser — background should now be warm off-white `#F4F2EE`.

- [ ] **Step 6: Commit**

```bash
git add src/index.css
git commit -m "style: replace CSS variables and fonts with Altara design tokens"
```

---

### Task 2: Tailwind Config

**Files:**
- Modify: `/tmp/smithnet-scaffold/tailwind.config.ts`

- [ ] **Step 1: Replace the entire file**

Replace with:
```ts
import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg:             '#F4F2EE',
        card:           '#FAFAF8',
        'surface-hover':'#F7F5F2',
        'surface-inset':'#F0EDE8',
        'surface-border':'#E8E4DE',
        dk:       '#3A352E',
        'dk-hover':'#4A443C',
        'dk-text': '#E8E4DE',
        'dk-text2':'#8C8478',
        gold:   '#9A6F2E',
        sage:   '#5A8C76',
        slate:  '#3A6A8C',
        dusty:  '#6A4A8C',
        brick:  '#8C3A3A',
        sienna: '#8C5A2E',
        tx:  '#2A2520',
        tx2: '#5C5347',
        tx3: '#8C8478',
      },
      fontFamily: {
        display: ['Syne', 'sans-serif'],
        mono:    ['IBM Plex Mono', 'monospace'],
        body:    ['IBM Plex Sans', 'sans-serif'],
      },
      boxShadow: {
        s1: '0 1px 3px rgba(0,0,0,.04), 0 4px 12px rgba(0,0,0,.03)',
        s2: '0 4px 12px rgba(0,0,0,.06), 0 16px 40px rgba(0,0,0,.08)',
        s3: '0 8px 24px rgba(0,0,0,.1), 0 40px 100px rgba(0,0,0,.15)',
      },
      borderRadius: {
        DEFAULT: '6px',
      },
      animation: {
        pulse: 'pulse 1.5s ease-in-out infinite',
        ripple: 'ripple 2s ease-out infinite',
        'slide-in': 'slideIn .18s ease',
      },
      keyframes: {
        pulse: { '0%,100%': { opacity: '1' }, '50%': { opacity: '.2' } },
        ripple: {
          '0%':   { boxShadow: '0 0 0 0 rgba(154,111,46,.4)' },
          '70%':  { boxShadow: '0 0 0 8px rgba(154,111,46,0)' },
          '100%': { boxShadow: '0 0 0 0 rgba(154,111,46,0)' },
        },
        slideIn: { from: { opacity: '0', transform: 'scale(.97) translateY(5px)' }, to: { opacity: '1', transform: 'none' } },
      },
    },
  },
  plugins: [],
} satisfies Config
```

- [ ] **Step 2: Verify Tailwind rebuilds**

Check browser — no build errors in terminal, warm background persists.

- [ ] **Step 3: Commit**

```bash
git add tailwind.config.ts
git commit -m "style: update Tailwind config with Altara color palette and fonts"
```

---

### Task 3: Constants — Color Mappings

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/lib/constants.ts`

- [ ] **Step 1: Replace STAGE colors**

Replace the `STAGE` object (lines 4–11) with:
```ts
export const STAGE: Record<string, { c: string; label: string }> = {
  new:       { c: '#8C8478', label: 'New'      },
  quote:     { c: '#3A6A8C', label: 'Quote'    },
  scheduled: { c: '#6A4A8C', label: 'Sched'    },
  active:    { c: '#9A6F2E', label: 'Active'   },
  invoiced:  { c: '#8C5A2E', label: 'Invoice'  },
  done:      { c: '#5A8C76', label: 'Done'     },
}
```

- [ ] **Step 2: Replace TRADE_C colors**

Replace the `TRADE_C` object (lines 14–24) with:
```ts
export const TRADE_C: Record<string, string> = {
  'Electrical':  '#9A6F2E',
  'Plumbing':    '#3A6A8C',
  'HVAC':        '#4A7A8C',
  'Carpentry':   '#8C5A2E',
  'Cleaning':    '#5A8C76',
  'Landscaping': '#6A8C5A',
  'Painting':    '#6A4A8C',
  'Roofing':     '#8C8478',
  'General':     '#7A7468',
}
```

- [ ] **Step 3: Replace ROLE_TYPES colors**

Replace the `ROLE_TYPES` array (lines 27–33) with:
```ts
export const ROLE_TYPES = [
  { id: 'worker',  label: 'Field Worker',  desc: 'Hours, tasks, GPS, field comms',                   color: '#3A6A8C' },
  { id: 'super',   label: 'Supervisor',    desc: 'Manages crew, approves tasks on site',              color: '#6A4A8C' },
  { id: 'manager', label: 'Manager',       desc: 'Full oversight, assigns crew, all jobs + reports',  color: '#9A6F2E' },
  { id: 'dispatch',label: 'Dispatcher',    desc: 'Schedules and routes crew, comms only',             color: '#5A8C76' },
  { id: 'admin',   label: 'Admin / Owner', desc: 'Full access — same as you',                         color: '#8C3A3A' },
] as const
```

- [ ] **Step 4: Replace ROLE_BADGE colors**

Replace the `ROLE_BADGE` object (lines 35–40) with:
```ts
export const ROLE_BADGE: Record<string, { c: string; bg: string; l: string }> = {
  manager:  { c: '#9A6F2E', bg: 'rgba(154,111,46,.08)',  l: 'MGR'   },
  super:    { c: '#6A4A8C', bg: 'rgba(106,74,140,.08)',  l: 'SUPER'  },
  dispatch: { c: '#5A8C76', bg: 'rgba(90,140,118,.08)',  l: 'DISP'   },
  admin:    { c: '#8C3A3A', bg: 'rgba(140,58,58,.08)',   l: 'ADMIN'  },
}
```

- [ ] **Step 5: Replace SIGNAL colors**

Replace `SIGNAL_C` (lines 96–102) with:
```ts
export const SIGNAL_C: Record<string, string> = {
  access:   '#8C3A3A',
  hazard:   '#8C4A2E',
  material: '#8C5A2E',
  break:    '#3A6A8C',
  enroute:  '#5A8C76',
}
```

- [ ] **Step 6: Commit**

```bash
git add src/lib/constants.ts
git commit -m "style: replace all constant colors with Altara palette"
```

---

### Task 4: UI Components — Chip, ProgressBar, SectionHeader

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/components/ui/Chip.tsx`
- Modify: `/tmp/smithnet-scaffold/src/components/ui/ProgressBar.tsx`
- Modify: `/tmp/smithnet-scaffold/src/components/ui/SectionHeader.tsx`

- [ ] **Step 1: Update Chip.tsx**

Replace the entire file with:
```tsx
import { cn } from '@/lib/utils'

interface ChipProps {
  label: string
  color: string
  xs?: boolean
  className?: string
}

export function Chip({ label, color, xs, className }: ChipProps) {
  return (
    <span
      className={cn('inline-flex items-center whitespace-nowrap', className)}
      style={{
        padding:       xs ? '1px 7px' : '2px 9px',
        borderRadius:  3,
        fontSize:      xs ? 8 : 10,
        fontFamily:    "var(--font-mono)",
        fontWeight:    600,
        background:    `${color}14`,
        color,
        border:        `.5px solid ${color}1f`,
        letterSpacing: '0.02em',
      }}
    >
      {label}
    </span>
  )
}
```

- [ ] **Step 2: Update ProgressBar.tsx**

Replace the entire file with:
```tsx
interface ProgressBarProps {
  pct: number
  color: string
  height?: number
}

export function ProgressBar({ pct, color, height = 3 }: ProgressBarProps) {
  return (
    <div
      style={{
        width:        '100%',
        height,
        background:   'var(--surface-inset)',
        borderRadius: 2,
        overflow:     'hidden',
        position:     'relative',
      }}
    >
      <div
        style={{
          height:       '100%',
          width:        `${pct}%`,
          background:   `linear-gradient(90deg, ${color} 0%, ${color}cc 100%)`,
          borderRadius: 2,
          minWidth:     pct > 0 ? 4 : 0,
          transition:   'width 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      />
    </div>
  )
}
```

- [ ] **Step 3: Update SectionHeader.tsx**

Replace the entire file with:
```tsx
import type { ReactNode } from 'react'

interface SectionHeaderProps {
  label: string
  right?: ReactNode
}

export function SectionHeader({ label, right }: SectionHeaderProps) {
  return (
    <div
      style={{
        display:         'flex',
        alignItems:      'center',
        justifyContent:  'space-between',
        padding:         '8px 12px',
        background:      'var(--dk)',
        flexShrink:      0,
      }}
    >
      <span
        style={{
          fontSize:      9,
          fontFamily:    'var(--font-mono)',
          fontWeight:    700,
          textTransform: 'uppercase',
          letterSpacing: '0.1em',
          color:         'var(--dk-text2)',
        }}
      >
        {label}
      </span>
      {right}
    </div>
  )
}
```

- [ ] **Step 4: Verify components render**

Check browser — chips should show `.5px` borders with muted tint backgrounds. Section headers should be warm dark `#3A352E`. Progress bar tracks should be warm `#F0EDE8`.

- [ ] **Step 5: Commit**

```bash
git add src/components/ui/Chip.tsx src/components/ui/ProgressBar.tsx src/components/ui/SectionHeader.tsx
git commit -m "style: update Chip, ProgressBar, SectionHeader to Altara tokens"
```

---

### Task 5: AppHeader

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/layouts/AppHeader.tsx`

- [ ] **Step 1: Replace the entire AppHeader component**

Replace the full file with:
```tsx
import { useState }   from 'react'
import { useStore }    from '@/store'
import { Avatar }      from '@/components/ui/Avatar'
import { fmtElapsed }  from '@/lib/utils'

type Nav = 'Dashboard' | 'Jobs' | 'Crew' | 'Reports'
const NAVS: Nav[] = ['Dashboard', 'Jobs', 'Crew', 'Reports']

export function AppHeader() {
  const nav         = useStore(s => s.activeNav)
  const setNav      = useStore(s => s.setNav)
  const crew        = useStore(s => s.crew)
  const notifications = useStore(s => s.notifications)
  const tick        = useStore(s => s.tick)
  const elapsed     = useStore(s => s.elapsed)
  const clockedIn   = useStore(s => s.clockedIn)

  const [profileOpen, setProfileOpen]   = useState(false)
  const [notifOpen, setNotifOpen]       = useState(false)
  const [newJobOpen, setNewJobOpen]     = useState(false)

  const teamSecs = crew.filter(c => c.joined && c.e > 0).reduce((s, c) => s + c.e + elapsed, 0)
  const teamH    = Math.floor(teamSecs / 3600)
  const teamM    = Math.floor((teamSecs % 3600) / 60)

  const now  = new Date()
  const hh   = String(now.getHours()).padStart(2, '0')
  const mm   = String(now.getMinutes()).padStart(2, '0')
  const ss   = String(now.getSeconds()).padStart(2, '0')

  const unreadCount = notifications.filter(n => !n.read).length

  return (
    <header style={{
      height:     50,
      background: 'var(--dk)',
      display:    'flex',
      alignItems: 'center',
      padding:    '0 16px',
      gap:        12,
      flexShrink: 0,
      boxShadow:  '0 2px 16px rgba(0,0,0,.18)',
    }}>
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          width: 30, height: 30, borderRadius: 8,
          background: 'var(--gold)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--bg)',
          boxShadow: '0 2px 8px rgba(154,111,46,.35)',
        }}>GS</div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--dk-text)', letterSpacing: '-0.02em', fontFamily: 'var(--font-display)' }}>SmithNet</div>
          <div style={{ fontSize: 8, fontFamily: 'var(--font-mono)', color: 'var(--dk-text2)', textTransform: 'uppercase', letterSpacing: '.07em' }}>Guild of Smiths</div>
        </div>
      </div>

      <div style={{ width: 1, height: 20, background: 'rgba(255,255,255,.08)', margin: '0 2px' }} />

      {/* Nav — bottom border pattern */}
      <nav style={{ display: 'flex', gap: 2, height: '100%', alignItems: 'stretch' }}>
        {NAVS.map(l => (
          <button
            key={l}
            onClick={() => setNav(l)}
            style={{
              fontSize:      11,
              padding:       '0 12px',
              background:    'transparent',
              color:         nav === l ? 'var(--gold)' : 'var(--dk-text2)',
              fontWeight:    nav === l ? 700 : 400,
              fontFamily:    'var(--font-body)',
              borderBottom:  nav === l ? '1.5px solid var(--gold)' : '1.5px solid transparent',
              transition:    'all .13s',
              display:       'flex',
              alignItems:    'center',
            }}
          >{l}</button>
        ))}
      </nav>

      {/* Search */}
      <div style={{
        flex: 1, display: 'flex', alignItems: 'center', gap: 6,
        background: 'rgba(255,255,255,.06)', borderRadius: 7, padding: '5px 10px',
        maxWidth: 180, marginLeft: 4, border: '.5px solid rgba(255,255,255,.06)',
      }}>
        <span style={{ color: 'var(--dk-text2)', fontSize: 11 }}>&#x2315;</span>
        <input placeholder="Search..." style={{ flex: 1, fontSize: 11, color: 'var(--dk-text)', fontFamily: 'var(--font-body)' }} />
      </div>

      <div style={{ flex: 1 }} />

      {/* Live clock */}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 1, marginRight: 8 }}>
        <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--dk-text)', fontFamily: 'var(--font-mono)' }}>{hh}:{mm}</span>
        <span style={{ fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--dk-text2)' }}>:{ss}</span>
      </div>

      {/* Team hours */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px',
        background: 'rgba(154,111,46,.1)', borderRadius: 7,
        border: '.5px solid rgba(154,111,46,.15)', marginRight: 6,
      }}>
        <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--gold)', animation: 'ripple 2s ease-out infinite' }} />
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: 'var(--gold)', fontFamily: 'var(--font-mono)' }}>Team Today</div>
          <div style={{ fontSize: 9, fontFamily: 'var(--font-mono)', color: 'var(--gold)' }}>
            {teamH}h {String(teamM).padStart(2, '0')}m
          </div>
        </div>
      </div>

      {/* Notification bell */}
      <button
        onClick={() => setNotifOpen(o => !o)}
        style={{
          position: 'relative', width: 34, height: 34, borderRadius: 8,
          background: unreadCount > 0 ? 'rgba(140,58,58,.1)' : 'rgba(255,255,255,.06)',
          border: `.5px solid ${unreadCount > 0 ? 'rgba(140,58,58,.2)' : 'rgba(255,255,255,.08)'}`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          transition: 'all .13s',
        }}
      >
        <svg width={16} height={16} viewBox="0 0 16 16" fill="none">
          <path d="M8 2a4.5 4.5 0 00-4.5 4.5v1.8L2 10v1h12v-1l-1.5-1.7V6.5A4.5 4.5 0 008 2zM6.5 13a1.5 1.5 0 003 0" stroke="var(--dk-text)" strokeWidth={1.3} strokeLinecap="round" />
          <circle cx={8} cy={2} r={0.8} fill="var(--dk-text)" />
        </svg>
        {unreadCount > 0 && (
          <div style={{
            position: 'absolute', top: -3, right: -3,
            background: 'var(--brick)', color: '#fff', borderRadius: 99,
            minWidth: 16, height: 16, display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700,
            border: '2px solid var(--dk)',
          }}>{unreadCount}</div>
        )}
      </button>

      {/* Profile */}
      <button
        onClick={() => setProfileOpen(true)}
        style={{
          display: 'flex', alignItems: 'center', gap: 7, padding: '4px 8px',
          borderRadius: 8, background: 'rgba(255,255,255,.06)',
          border: '.5px solid rgba(255,255,255,.08)', transition: 'all .13s',
        }}
      >
        <div style={{ position: 'relative' }}>
          <Avatar name="James Park" color="#0891b2" size={28} />
          <div style={{
            position: 'absolute', bottom: -1, right: -1,
            width: 8, height: 8, borderRadius: '50%',
            background: 'var(--sage)', border: '2px solid var(--dk)',
            animation: 'pulse 2s infinite',
          }} />
        </div>
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--dk-text)', fontFamily: 'var(--font-body)' }}>James Park</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--gold)', letterSpacing: '.05em' }}>OWNER</span>
            <div style={{ width: 1, height: 7, background: 'rgba(255,255,255,.1)' }} />
            <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', color: 'var(--dk-text2)' }}>Electrician</span>
          </div>
        </div>
        <span style={{ fontSize: 9, color: 'var(--dk-text2)', marginLeft: 2 }}>&rsaquo;</span>
      </button>

      {/* New Job */}
      <button
        onClick={() => setNewJobOpen(true)}
        style={{
          fontSize: 11, padding: '6px 14px', borderRadius: 7,
          background: 'var(--gold)', color: 'var(--bg)', fontWeight: 700,
          fontFamily: 'var(--font-body)',
          marginLeft: 8, boxShadow: '0 2px 8px rgba(154,111,46,.3)',
          transition: 'all .13s',
        }}
      >+ New Job</button>
    </header>
  )
}
```

- [ ] **Step 2: Verify header renders**

Check browser — header should be warm dark `#3A352E`, nav tabs should show gold bottom border on active, logo badge should be gold, "SmithNet" in Syne font.

- [ ] **Step 3: Commit**

```bash
git add src/layouts/AppHeader.tsx
git commit -m "style: restyle AppHeader with Altara warm dark + bottom-border nav"
```

---

### Task 6: StatusBar

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/layouts/StatusBar.tsx`

- [ ] **Step 1: Replace the entire StatusBar component**

Replace the full file with:
```tsx
import { useStore } from '@/store'
import { fmtDollar } from '@/lib/utils'

export function StatusBar() {
  const jobs   = useStore(s => s.jobs)
  const crew   = useStore(s => s.crew)
  const elapsed = useStore(s => s.elapsed)

  const onJob    = crew.filter(c => c.status === 'on-job').length
  const onRoute  = crew.filter(c => c.status === 'en-route').length
  const owed     = fmtDollar(jobs.filter(j => j.stage === 'invoiced').reduce((s, j) => s + j.est, 0))
  const blockers = jobs.filter(j => j.blocker).length

  const teamSecs = crew.filter(c => c.joined && c.e > 0).reduce((s, c) => s + c.e, 0)
  const teamH    = Math.floor(teamSecs / 3600)
  const teamM    = Math.floor((teamSecs % 3600) / 60)

  const sep = (
    <div style={{ width: 1, height: 9, background: 'rgba(255,255,255,.08)' }} />
  )

  return (
    <div style={{
      height:     22,
      background: 'var(--dk)',
      display:    'flex',
      alignItems: 'center',
      padding:    '0 14px',
      gap:        10,
      flexShrink: 0,
    }}>
      <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--gold)', letterSpacing: '.1em' }}>
        SMITHNET
      </span>
      {sep}
      <span style={{ fontSize: 9, fontWeight: 500, color: 'var(--dk-text2)', fontFamily: 'var(--font-body)' }}>
        James Park &middot; Brooklyn NY
      </span>
      {sep}
      <span style={{ fontSize: 9, fontWeight: 600, color: 'var(--dk-text)', fontFamily: 'var(--font-body)' }}>
        200A Panel Upgrade
      </span>
      {sep}
      <span style={{ fontSize: 9, fontWeight: 600, color: 'var(--dk-text2)', fontFamily: 'var(--font-mono)' }}>
        Crew: {onJob} on job
      </span>
      {onRoute > 0 && (
        <span style={{ fontSize: 9, fontWeight: 500, color: 'var(--dk-text2)', fontFamily: 'var(--font-mono)' }}>
          {onRoute} en route
        </span>
      )}
      {teamSecs > 0 && (
        <span style={{ fontSize: 9, fontFamily: 'var(--font-mono)', fontWeight: 500, color: 'var(--dk-text2)' }}>
          {teamH}h {String(teamM).padStart(2, '0')}m team today
        </span>
      )}
      {blockers > 0 && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: 3,
          padding: '1px 6px', background: 'rgba(140,58,58,.12)', borderRadius: 3,
        }}>
          <div style={{ width: 4, height: 4, borderRadius: '50%', background: 'var(--brick)' }} />
          <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--brick)' }}>
            {blockers} blocker
          </span>
        </div>
      )}
      <div style={{ flex: 1 }} />
      <span style={{ fontSize: 9, fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--gold)' }}>
        {owed} outstanding
      </span>
    </div>
  )
}
```

- [ ] **Step 2: Verify and commit**

```bash
git add src/layouts/StatusBar.tsx
git commit -m "style: restyle StatusBar with Altara warm dark strip"
```

---

### Task 7: Dashboard Page

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/pages/Dashboard.tsx`

- [ ] **Step 1: Update the grid and surface styles**

Replace the entire file with:
```tsx
import { useStore }       from '@/store'
import { SectionHeader }  from '@/components/ui/SectionHeader'
import { Chip }           from '@/components/ui/Chip'
import { JobCard }        from '@/components/job/JobCard'

export function Dashboard() {
  const jobs        = useStore(s => s.jobs)
  const crew        = useStore(s => s.crew)
  const threads     = useStore(s => s.threads)
  const jobMaterials = useStore(s => s.jobMaterials)
  const selectedJobId = useStore(s => s.selectedJobId)
  const setSelectedJob = useStore(s => s.setSelectedJob)

  const activeCount  = jobs.filter(j => j.stage === 'active').length
  const hasBlocker   = jobs.some(j => j.blocker)
  const unreadCount  = threads.reduce((s, t) => s + t.unread, 0)

  return (
    <div style={{
      flex: 1,
      display: 'grid',
      gridTemplateColumns: '220px 1fr 180px',
      gap: 8,
      padding: 8,
      overflow: 'hidden',
    }}>

      {/* Left: Job Structure */}
      <div style={{
        background: 'var(--card)', borderRadius: 8,
        border: '.5px solid rgba(0,0,0,.07)',
        boxShadow: 'var(--s1)',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        <SectionHeader
          label="Job Structure"
          right={
            <div style={{ display: 'flex', gap: 4 }}>
              <Chip label={`${activeCount} active`} color="#9A6F2E" xs />
              {hasBlocker && <Chip label="Blocked" color="#8C3A3A" xs />}
            </div>
          }
        />
        <div style={{ flex: 1, overflowY: 'auto', padding: 6, display: 'flex', flexDirection: 'column', gap: 5 }}>
          {jobs.map(job => (
            <JobCard
              key={job.id}
              job={job}
              crew={crew}
              selected={selectedJobId === job.id}
              mats={jobMaterials[job.id] ?? []}
              onSelect={() => setSelectedJob(selectedJobId === job.id ? null : job.id)}
              onAssign={() => {}}
              onDetail={() => {}}
              onMaterials={() => {}}
            />
          ))}
        </div>
      </div>

      {/* Center: Map */}
      <div style={{ borderRadius: 8, overflow: 'hidden', border: '.5px solid rgba(0,0,0,.07)', boxShadow: 'var(--s1)' }}>
        <div style={{ width: '100%', height: '100%', background: 'var(--surface-inset)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ textAlign: 'center', color: 'var(--tx3)' }}>
            <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 4, fontFamily: 'var(--font-display)' }}>Map View</div>
            <div style={{ fontSize: 11, fontFamily: 'var(--font-body)' }}>Connect Google Maps or OpenStreetMap</div>
          </div>
        </div>
      </div>

      {/* Right: Field Comms */}
      <div style={{
        background: 'var(--card)', borderRadius: 8,
        border: '.5px solid rgba(0,0,0,.07)',
        boxShadow: 'var(--s1)',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        <SectionHeader
          label="Field Comms"
          right={
            unreadCount > 0 ? (
              <div style={{
                background: 'var(--brick)', color: '#fff', borderRadius: 99,
                minWidth: 16, height: 16, display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700,
              }}>{unreadCount}</div>
            ) : undefined
          }
        />
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--tx3)', fontSize: 11, fontFamily: 'var(--font-body)' }}>
          ThreadList component
        </div>
      </div>

    </div>
  )
}
```

- [ ] **Step 2: Verify and commit**

```bash
git add src/pages/Dashboard.tsx
git commit -m "style: restyle Dashboard with Altara surfaces and tighter grid"
```

---

### Task 8: JobCard

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/components/job/JobCard.tsx`

- [ ] **Step 1: Replace the entire JobCard component**

See spec `docs/superpowers/specs/2026-04-16-altara-design-system-redesign.md` for the full component styling rules. Replace the file with the Altara-themed version — key changes:

```tsx
import { Avatar }      from '@/components/ui/Avatar'
import { Chip }        from '@/components/ui/Chip'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { STAGE }       from '@/lib/constants'
import { jobPct, fmtElapsed } from '@/lib/utils'
import type { Job, CrewMember, Material } from '@/types'

interface JobCardProps {
  job:      Job
  crew:     CrewMember[]
  selected: boolean
  mats:     Material[]
  onSelect:    () => void
  onAssign:    () => void
  onDetail:    () => void
  onMaterials: (e: React.MouseEvent) => void
}

export function JobCard({
  job, crew, selected, mats,
  onSelect, onAssign, onDetail, onMaterials,
}: JobCardProps) {
  const s       = STAGE[job.stage] ?? STAGE.new
  const pct     = jobPct(job)
  const jCrew   = crew.filter(c => c.job === job.id)
  const act     = job.tasks.find(t => t.active && !t.done)
  const missing = mats.filter(m => !m.onSite)
  const hasCrit = missing.some(m => m.critical)
  const jobSecs = jCrew.reduce((sum, c) => sum + c.e, 0)
  const jobHrs  = Math.floor(jobSecs / 3600)
  const jobMins = Math.floor((jobSecs % 3600) / 60)

  return (
    <div
      onClick={onSelect}
      style={{
        background:   'var(--card)',
        borderRadius: 8,
        overflow:     'hidden',
        border:       '.5px solid rgba(0,0,0,.07)',
        boxShadow:    selected
          ? `inset 2px 0 0 ${s.c}, var(--s2)`
          : 'var(--s1)',
        cursor:     'pointer',
        flexShrink: 0,
        transition: 'box-shadow 0.17s',
      }}
    >
      <div style={{ padding: '9px 10px 8px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--tx)', fontFamily: 'var(--font-display)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 1 }}>
              {job.title}
            </div>
            <div style={{ fontSize: 10, color: 'var(--tx3)', fontFamily: 'var(--font-body)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {job.client}
            </div>
          </div>
          <Chip label={s.label} color={s.c} xs />
        </div>

        <ProgressBar pct={pct} color={s.c} />

        {jobSecs > 0 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 2 }}>
            <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', color: 'var(--tx3)' }}>
              {jobHrs}h {String(jobMins).padStart(2, '0')}m logged
            </span>
          </div>
        )}

        {missing.length > 0 && (
          <div style={{ marginTop: 4 }}>
            <button
              onClick={e => { e.stopPropagation(); onMaterials(e) }}
              style={{
                display: 'flex', alignItems: 'center', gap: 4,
                padding: '2px 7px', borderRadius: 99,
                background: hasCrit ? 'rgba(140,90,46,.06)' : 'var(--surface-inset)',
                border: `.5px solid ${hasCrit ? 'rgba(140,90,46,.15)' : 'rgba(0,0,0,.07)'}`,
                cursor: 'pointer',
              }}
            >
              <div style={{
                width: 4, height: 4, borderRadius: '50%',
                background: hasCrit ? 'var(--sienna)' : 'var(--tx3)',
                animation: hasCrit ? 'pulse 1.5s infinite' : 'none',
              }} />
              <span style={{ fontSize: 8, fontFamily: 'var(--font-mono)', fontWeight: 700, color: hasCrit ? 'var(--sienna)' : 'var(--tx3)' }}>
                {missing.length} item{missing.length !== 1 ? 's' : ''} needed
              </span>
            </button>
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 6 }}>
          <div style={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            {jCrew.map(c => (
              <Avatar key={c.id} name={c.name} color={c.c} size={15} />
            ))}
            <button
              onClick={e => { e.stopPropagation(); onAssign() }}
              style={{
                width: 15, height: 15, borderRadius: 3,
                background: 'var(--surface-inset)',
                border: '1px dashed var(--surface-border)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 10, color: 'var(--tx3)', lineHeight: 1,
                flexShrink: 0, cursor: 'pointer',
              }}
            >+</button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {act && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                <div style={{ width: 4, height: 4, borderRadius: '50%', background: s.c, animation: 'pulse 1.5s infinite' }} />
                <span style={{ fontSize: 9, fontFamily: 'var(--font-mono)', color: s.c, fontWeight: 600, maxWidth: 90, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {act.t}
                </span>
              </div>
            )}
            <span style={{ fontSize: 9, fontFamily: 'var(--font-mono)', color: 'var(--tx3)' }}>{pct}%</span>
          </div>
        </div>
      </div>

      {selected && (
        <div style={{ borderTop: '.5px solid rgba(0,0,0,.07)', padding: '8px 10px', background: 'var(--surface-inset)' }}>
          {job.blocker && (
            <div style={{ display: 'flex', gap: 5, padding: '5px 8px', background: 'rgba(140,58,58,.06)', borderRadius: 5, marginBottom: 6, border: '.5px solid rgba(140,58,58,.1)' }}>
              <span style={{ fontSize: 9, fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--brick)', flexShrink: 0 }}>BLOCKED</span>
              <span style={{ fontSize: 10, color: 'var(--brick)', fontFamily: 'var(--font-body)', lineHeight: 1.4 }}>{job.blocker}</span>
            </div>
          )}
          <button
            onClick={e => { e.stopPropagation(); onDetail() }}
            style={{ width: '100%', fontSize: 10, fontWeight: 600, color: 'var(--tx2)', fontFamily: 'var(--font-body)', padding: 5, borderRadius: 5, background: 'var(--card)', marginBottom: 6, border: '.5px solid rgba(0,0,0,.07)', cursor: 'pointer' }}
          >
            View Full Report
          </button>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {job.tasks.map(t => (
              <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '4px 6px', borderRadius: 4 }}>
                <div style={{
                  width: 13, height: 13, borderRadius: 3.5,
                  border: `1.5px solid ${t.done ? s.c : t.active ? s.c + '99' : 'var(--surface-border)'}`,
                  background: t.done ? s.c : 'var(--card)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                }}>
                  {t.done && (
                    <svg width={7} height={7} viewBox="0 0 10 10" fill="none">
                      <path d="M1.5 5l3 3 4-4.5" stroke="white" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  )}
                </div>
                <span style={{ fontSize: 11, fontFamily: 'var(--font-body)', color: t.done ? 'var(--tx3)' : t.active ? 'var(--tx)' : 'var(--tx2)', textDecoration: t.done ? 'line-through' : 'none', flex: 1, lineHeight: 1.3 }}>
                  {t.t}
                </span>
                {t.active && !t.done && (
                  <div style={{ width: 4, height: 4, borderRadius: '50%', background: s.c, animation: 'pulse 1.5s infinite', flexShrink: 0 }} />
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Verify and commit**

```bash
git add src/components/job/JobCard.tsx
git commit -m "style: restyle JobCard with Altara surfaces, borders, and typography"
```

---

### Task 9: Jobs Page

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/pages/Jobs.tsx`

- [ ] **Step 1: Replace the entire Jobs page**

Key changes: all `#fff` to `var(--card)`, all `#f2f4f9` to `var(--surface-inset)`, all `#e0e5f0` to `rgba(0,0,0,.07)` borders, all `#9ca3af` to `var(--tx3)`, all `#1a1d28` to `var(--dk)`, all `#d4daea` to `var(--dk-text)`, all `JetBrains Mono` to `var(--font-mono)`, all `#dc2626` to `var(--brick)`, all `#64748b` to `#8C8478`, titles use `var(--font-display)`, body text uses `var(--font-body)`, border-radius from 10 to 8, box-shadows to `var(--s1)`, borders to `.5px solid rgba(0,0,0,.07)`, padding from 9 to 8, blocker bg to `rgba(140,58,58,.04)`.

Full replacement file provided in the executing agent's context.

- [ ] **Step 2: Verify and commit**

```bash
git add src/pages/Jobs.tsx
git commit -m "style: restyle Jobs page with Altara palette"
```

---

### Task 10: Crew Page

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/pages/Crew.tsx`

- [ ] **Step 1: Replace the entire Crew page**

Key changes: Status definition colors change (`on-job` to `#5A8C76`, `en-route` to `#9A6F2E`, `available` to `#8C8478`, `offline` to `#5C5347`, `pending` to `#8C8478`). All surface/border/font token swaps same as Jobs page. "Simulate Join" button uses sage tint. "Add Member" button uses `var(--gold)`. Invite code badge uses `var(--gold)` on `var(--dk)`.

Full replacement file provided in the executing agent's context.

- [ ] **Step 2: Verify and commit**

```bash
git add src/pages/Crew.tsx
git commit -m "style: restyle Crew page with Altara palette and typography"
```

---

### Task 11: Reports Page

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/pages/Reports.tsx`

- [ ] **Step 1: Replace the entire Reports page**

Key changes: KPI numbers use `var(--font-display)`, stat card top bars use `var(--dk)`, mini progress bars use `var(--gold)`, period toggle uses `var(--dk)` active state, "Download Full Report" uses `var(--dk)` bg with `var(--gold)` text, invoice outstanding chip uses `#8C5A2E`, paid chip uses `#5A8C76`, draft uses `#8C8478`.

Full replacement file provided in the executing agent's context.

- [ ] **Step 2: Verify and commit**

```bash
git add src/pages/Reports.tsx
git commit -m "style: restyle Reports page with Altara palette and typography"
```

---

### Task 12: Print Document Styles

**Files:**
- Modify: `/tmp/smithnet-scaffold/src/lib/utils.ts`

- [ ] **Step 1: Update the printDoc function**

In `utils.ts`, replace the `printDoc` function (lines 56–63). Change font imports to Syne + IBM Plex Mono + IBM Plex Sans, body font-family to `'IBM Plex Sans'`, color `#0d1117` to `#2A2520`, th background `#f9fafb` to `#F4F2EE`, th color/font to IBM Plex Mono + `#8C8478`, `.total` color to `#9A6F2E`, border-bottom to `.5px solid rgba(0,0,0,.07)`, h1 font-family to `'Syne'`, h2 font to IBM Plex Mono + `#8C8478`, `.hdr` border-bottom color to `#2A2520`.

- [ ] **Step 2: Commit**

```bash
git add src/lib/utils.ts
git commit -m "style: update printDoc with Altara fonts and colors"
```

---

### Task 13: Final Visual Verification

- [ ] **Step 1: Full visual check in browser**

Open http://localhost:3000 and verify each view:
1. **Dashboard** — warm off-white background, tighter 3-column grid, warm dark header/status bar, gold nav tab active, `.5px` borders on all cards
2. **Jobs** — Syne titles, earthy stage chips, warm search bar, brick-red blocker labels
3. **Crew** — sage "On the Clock" pills, gold invite codes on dark badges, warm card surfaces
4. **Reports** — Syne KPI numbers, gold progress bars, warm dark period selector, sienna invoice chips

- [ ] **Step 2: Check no hardcoded old colors remain**

Run:
```bash
grep -rn '#f59e0b\|#dc2626\|#16a34a\|#3b82f6\|#0891b2\|#1a1d28\|#f2f4f9\|JetBrains Mono\|Inter,' src/ --include='*.tsx' --include='*.ts' --include='*.css'
```
Expected: No matches (all old colors and fonts replaced). The only exceptions would be in `src/lib/data.ts` (crew member personal colors like `c.c`) which are intentionally kept.

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "style: complete Altara design system migration — verify no old tokens remain"
```
