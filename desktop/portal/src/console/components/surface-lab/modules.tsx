import { ReactNode } from 'react';
import { clsx } from 'clsx';
import { ModuleId } from './surface';
import { JobStatus, SAMPLE_JOB } from './sampleJob';
import { SAMPLE_JOBS, SAMPLE_COMM, SAMPLE_CREW, SAMPLE_CLIENTS, SAMPLE_ON_SITE } from './sampleApp';

// One feature module = one app container, rendered as a mini panel. The app
// shell shows as many of these as the surface can hold.

// Presence/GPS glyphs -- were consoleTheme.glyphs.online/offline
// (theme/consoleTheme.ts, deleted this task); same literals, now local.
const ONLINE = '((+))'; // presence / GPS
const OFFLINE = '(( ))';

const STATUS_TOKEN: Record<JobStatus, string> = {
  planned: '[ ]',
  in_progress: '[~]',
  complete: '[OK]',
};

const STATUS_COLOR: Record<JobStatus, string> = {
  planned: 'text-sn-ink-muted',
  in_progress: 'text-sn-attention',
  complete: 'text-sn-status-online',
};

const MODULE_TITLE: Record<ModuleId, string> = {
  job: 'active job',
  jobs: 'jobs',
  comm: 'comm',
  map: 'map',
  crew: 'crew',
  clients: 'clients',
};

function MiniPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="border border-sn-line bg-sn-bg-panel overflow-hidden flex flex-col min-h-0">
      <div className="bg-sn-bg-base text-sn-ink-muted text-[0.72em] uppercase tracking-wider px-[0.6em] py-[0.25em] border-b border-sn-line">
        {title}
      </div>
      <div className="p-[0.55em] flex-1 leading-snug text-[0.82em] min-h-0 overflow-hidden">{children}</div>
    </div>
  );
}

function moduleBody(id: ModuleId): ReactNode {
  switch (id) {
    case 'job':
      return (
        <div className="flex flex-col gap-[0.3em]">
          <span
            className={clsx(
              'inline-flex items-center self-start rounded-sm px-[0.45em] py-[0.05em] font-semibold',
              'bg-sn-attention text-sn-ink-on-accent',
            )}
          >
            {STATUS_TOKEN[SAMPLE_JOB.status]} {SAMPLE_JOB.status.replace('_', ' ')}
          </span>
          <span className="font-bold truncate">{SAMPLE_JOB.title}</span>
          <span className="text-sn-accent font-bold tabular-nums">{SAMPLE_JOB.metric}</span>
          <div className="h-[0.45em] w-full bg-sn-line rounded-sm overflow-hidden">
            <div className="h-full bg-sn-accent" style={{ width: `${Math.round(SAMPLE_JOB.progress * 100)}%` }} />
          </div>
        </div>
      );
    case 'jobs':
      return (
        <div className="flex flex-col gap-[0.2em]">
          {SAMPLE_JOBS.map((j) => (
            <div key={j.title} className="flex items-center gap-[0.4em] truncate">
              <span className={clsx('font-semibold', STATUS_COLOR[j.status])}>{STATUS_TOKEN[j.status]}</span>
              <span className="truncate">{j.title}</span>
            </div>
          ))}
        </div>
      );
    case 'comm':
      return (
        <div className="flex flex-col gap-[0.2em]">
          {SAMPLE_COMM.map((c, i) => (
            <div key={i} className="truncate">
              <span className="text-sn-accent font-semibold">{c.who}:</span> {c.text}
            </div>
          ))}
        </div>
      );
    case 'map':
      return (
        <div className="relative h-full min-h-[2.5em] bg-sn-bg-base border border-sn-line rounded-sm overflow-hidden flex items-center justify-center">
          <span className="absolute left-[0.4em] top-[0.3em] text-sn-line text-[1.4em] leading-none select-none">+ + +</span>
          <span className="text-sn-status-online font-semibold">
            {ONLINE} {SAMPLE_ON_SITE} on site
          </span>
        </div>
      );
    case 'crew':
      return (
        <div className="flex flex-col gap-[0.2em]">
          {SAMPLE_CREW.map((m) => (
            <div key={m.name} className="flex items-center gap-[0.4em] truncate">
              <span className={m.online ? 'text-sn-status-online' : 'text-sn-ink-muted'}>
                {m.online ? ONLINE : OFFLINE}
              </span>
              <span className="truncate">{m.name}</span>
            </div>
          ))}
        </div>
      );
    case 'clients':
      return (
        <div className="flex flex-col gap-[0.2em]">
          {SAMPLE_CLIENTS.map((c) => (
            <div key={c} className="flex items-center gap-[0.4em] truncate">
              <span className="inline-block w-[0.35em] h-[0.35em] bg-sn-accent shrink-0" />
              <span className="truncate">{c}</span>
            </div>
          ))}
        </div>
      );
    default:
      return null;
  }
}

export function ModulePanel({ id }: { id: ModuleId }) {
  return <MiniPanel title={MODULE_TITLE[id]}>{moduleBody(id)}</MiniPanel>;
}
