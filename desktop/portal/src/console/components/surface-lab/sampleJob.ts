// Hardcoded sample content for the Surface Lab demo. A Smith Net-style job so
// the prototype shows real visual + logical integrity reshaping (not lorem).

export type JobStatus = 'planned' | 'in_progress' | 'complete';

export interface JobTask {
  label: string;
  done: boolean;
}

export interface DemoJob {
  title: string;
  status: JobStatus;
  metric: string;
  client: string;
  location: string;
  due: string;
  progress: number; // 0..1
  tasks: JobTask[];
}

export const SAMPLE_JOB: DemoJob = {
  title: 'Panel upgrade - 200A',
  status: 'in_progress',
  metric: '14.5 h',
  client: 'Aegis Assure',
  location: '4th St unit 2',
  due: 'due Fri',
  progress: 0.62,
  tasks: [
    { label: 'pull permit', done: true },
    { label: 'rough-in wiring', done: true },
    { label: 'final inspection', done: false },
  ],
};
