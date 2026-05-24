import { ReactNode } from 'react';
import { clsx } from 'clsx';
import { consoleTheme } from '../../theme/consoleTheme';
import { ModuleId } from './surface';
import { JobStatus, SAMPLE_JOB } from './sampleJob';
import { SAMPLE_JOBS, SAMPLE_COMM, SAMPLE_CREW, SAMPLE_CLIENTS, SAMPLE_ON_SITE } from './sampleApp';

// One feature module = one app container, rendered as a mini panel. The app
// shell shows as many of these as the surface can hold.

const ONLINE = consoleTheme.glyphs.online; // ((+))  -- presence / GPS
const OFFLINE = consoleTheme.glyphs.offline; // (( ))

const STATUS_TOKEN: Record<JobStatus, string> = {
  planned: '[ ]',
  in_progress: '[~]',
  complete: '[OK]',
};

const STATUS_COLOR: Record<JobStatus, string> = {
  planned: 'text-console-text-muted',
  in_progress: 'text-console-warn',
  complete: 'text-console-ok',
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
    <div className="border border-console-border bg-console-surface overflow-hidden flex flex-col min-h-0">
      <div className="bg-console-bg text-console-text-muted text-[0.72em] uppercase tracking-wider px-[0.6em] py-[0.25em] border-b border-console-border">
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
              'bg-console-warn text-white',
            )}
          >
            {STATUS_TOKEN[SAMPLE_JOB.status]} {SAMPLE_JOB.status.replace('_', ' ')}
          </span>
          <span className="font-bold truncate">{SAMPLE_JOB.title}</span>
          <span className="text-console-accent font-bold tabular-nums">{SAMPLE_JOB.metric}</span>
          <div className="h-[0.45em] w-full bg-console-border rounded-sm overflow-hidden">
            <div className="h-full bg-console-accent" style={{ width: `${Math.round(SAMPLE_JOB.progress * 100)}%` }} />
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
              <span className="text-console-accent font-semibold">{c.who}:</span> {c.text}
            </div>
          ))}
        </div>
      );
    case 'map':
      return (
        <div className="relative h-full min-h-[2.5em] bg-console-bg border border-console-border rounded-sm overflow-hidden flex items-center justify-center">
          <span className="absolute left-[0.4em] top-[0.3em] text-console-border text-[1.4em] leading-none select-none">+ + +</span>
          <span className="text-console-ok font-semibold">
            {ONLINE} {SAMPLE_ON_SITE} on site
          </span>
        </div>
      );
    case 'crew':
      return (
        <div className="flex flex-col gap-[0.2em]">
          {SAMPLE_CREW.map((m) => (
            <div key={m.name} className="flex items-center gap-[0.4em] truncate">
              <span className={m.online ? 'text-console-ok' : 'text-console-text-muted'}>
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
              <span className="inline-block w-[0.35em] h-[0.35em] bg-console-accent shrink-0" />
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
