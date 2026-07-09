import { ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';
import { adaptLayout, PX_PER_IN, Surface, SlotId } from './surface';
import { DemoJob, JobStatus } from './sampleJob';

// The HOST renderer. adaptLayout(surface) decides WHAT shows; this draws the
// chosen slots into a surface-shaped, surface-sized container and SCALES the
// type so the content always fills the surface.

interface Props {
  surface: Surface;
  job: DemoJob;
}

// Job status tokens. NOTE: do not use ((+)) / (( )) here -- those are the
// theme's online/offline (presence/GPS) glyphs and mean something else.
const STATUS_GLYPH: Record<JobStatus, string> = {
  planned: '[ ]',
  in_progress: '[~]',
  complete: '[OK]',
};

const STATUS_TEXT: Record<JobStatus, string> = {
  planned: 'planned',
  in_progress: 'in progress',
  complete: 'complete',
};

const STATUS_CHIP: Record<JobStatus, string> = {
  planned: 'bg-sn-line text-sn-ink-muted',
  in_progress: 'bg-sn-attention text-sn-ink-on-accent',
  complete: 'bg-sn-status-online text-sn-ink-on-accent',
};

const PAD_PX: Record<1 | 2 | 3, number> = { 1: 5, 2: 9, 3: 13 };

const SLOT_LINES: Record<SlotId, number> = {
  title: 1,
  status: 1,
  metric: 1.3,
  progress: 1.4,
  details: 3,
  tasks: 3,
  actions: 1.1,
  statusGlyph: 1,
};

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

function slotWidthChars(slot: SlotId, job: DemoJob, abbreviate: boolean): number {
  switch (slot) {
    case 'title':
      return (abbreviate ? job.title.split(' ')[0] : job.title).length;
    case 'status':
      return `${STATUS_GLYPH[job.status]} ${STATUS_TEXT[job.status]}`.length + 2;
    case 'statusGlyph':
      return STATUS_GLYPH[job.status].length + 2;
    case 'metric':
      return Math.ceil(job.metric.length * 1.3);
    case 'progress':
      return 14;
    case 'details':
      return Math.max(job.client.length, job.location.length, job.due.length) + 3;
    case 'tasks':
      return Math.max(...job.tasks.map((t) => t.label.length)) + 5;
    case 'actions':
      return 'open -> msg'.length + 4;
    default:
      return 1;
  }
}

function Bullet() {
  return <span className="inline-block w-[0.4em] h-[0.4em] bg-sn-accent shrink-0" />;
}

export function AdaptiveCard({ surface, job }: Props) {
  const plan = adaptLayout(surface);
  const isCircle = surface.shape === 'circle';
  const wPx = surface.wIn * PX_PER_IN;
  const hPx = surface.hIn * PX_PER_IN;
  const pad = PAD_PX[plan.padScale];

  const effW = isCircle ? wPx * 0.707 : wPx;
  const effH = isCircle ? hPx * 0.707 : hPx;
  const availW = Math.max(8, effW - pad * 2);
  const availH = Math.max(8, effH - pad * 2);

  const lines = plan.slots.reduce((n, s) => n + SLOT_LINES[s], 0);
  const widest = Math.max(...plan.slots.map((s) => slotWidthChars(s, job, plan.abbreviate)));
  const widthBound = availW / (widest * 0.62);
  const heightBound = availH / (lines * 1.5);
  const fontPx = clamp(Math.min(widthBound, heightBound), 8, 46);

  function renderSlot(slot: SlotId): ReactNode {
    switch (slot) {
      case 'title':
        return (
          <div key="title" className="font-bold tracking-tight truncate max-w-full text-[1.05em] leading-tight">
            {plan.abbreviate ? job.title.split(' ')[0] : job.title}
          </div>
        );
      case 'status':
        return (
          <span
            key="status"
            className={clsx(
              'inline-flex items-center self-start rounded-sm px-[0.5em] py-[0.1em] font-semibold whitespace-nowrap',
              STATUS_CHIP[job.status],
            )}
          >
            {STATUS_GLYPH[job.status]} {STATUS_TEXT[job.status]}
          </span>
        );
      case 'statusGlyph':
        return (
          <span
            key="statusGlyph"
            className={clsx(
              'inline-flex items-center justify-center rounded-full px-[0.55em] py-[0.2em] font-bold whitespace-nowrap',
              STATUS_CHIP[job.status],
            )}
          >
            {STATUS_GLYPH[job.status]}
          </span>
        );
      case 'metric':
        return (
          <div key="metric" className="text-sn-accent font-bold tabular-nums text-[1.45em] leading-none">
            {job.metric}
          </div>
        );
      case 'progress': {
        const pct = Math.round(job.progress * 100);
        return (
          <div key="progress" className="w-full text-[0.8em]">
            <div className="flex justify-between text-sn-ink-muted leading-none mb-[0.3em]">
              <span>progress</span>
              <span className="tabular-nums">{pct}%</span>
            </div>
            <div className="h-[0.5em] w-full bg-sn-line rounded-sm overflow-hidden">
              <div className="h-full bg-sn-accent" style={{ width: `${pct}%` }} />
            </div>
          </div>
        );
      }
      case 'details':
        return (
          <div key="details" className="text-sn-ink-muted text-[0.82em] leading-snug w-full">
            {[job.client, job.location, job.due].map((line) => (
              <div key={line} className="flex items-center gap-[0.5em] truncate">
                <Bullet />
                <span className="truncate">{line}</span>
              </div>
            ))}
          </div>
        );
      case 'tasks':
        return (
          <div key="tasks" className="text-[0.82em] leading-snug w-full">
            {job.tasks.map((t) => (
              <div key={t.label} className="flex items-center gap-[0.45em] truncate">
                <span className={clsx('font-semibold', t.done ? 'text-sn-status-online' : 'text-sn-ink-muted')}>
                  {t.done ? '[x]' : '[ ]'}
                </span>
                <span className={clsx('truncate', t.done && 'text-sn-ink-muted')}>{t.label}</span>
              </div>
            ))}
          </div>
        );
      case 'actions':
        return (
          <div key="actions" className="flex items-center gap-[0.4em] flex-wrap">
            <span className="bg-sn-ink text-sn-bg-base px-[0.7em] py-[0.18em] font-semibold rounded-sm whitespace-nowrap">
              open -&gt;
            </span>
            <span className="border border-sn-line text-sn-ink-muted px-[0.55em] py-[0.18em] rounded-sm whitespace-nowrap">
              msg
            </span>
          </div>
        );
      default:
        return null;
    }
  }

  return (
    <div
      style={{ width: wPx, height: hPx, padding: pad, fontSize: fontPx, lineHeight: 1.2 }}
      className={twMerge(
        clsx(
          'bg-sn-bg-panel text-sn-ink font-mono overflow-hidden flex shadow-sm border border-sn-line',
          isCircle ? 'rounded-full items-center justify-center text-center' : 'rounded-md',
        ),
      )}
    >
      <div
        className={clsx(
          'flex flex-col w-full h-full min-w-0',
          isCircle ? 'items-center justify-center gap-[0.3em]' : 'justify-between gap-[0.3em]',
        )}
      >
        {plan.slots.map(renderSlot)}
      </div>
    </div>
  );
}
