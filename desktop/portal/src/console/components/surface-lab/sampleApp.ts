// Sample data for the app's feature modules (jobs list, comm, crew, clients,
// map). Hardcoded -- this is a presentation demo, no backend.

import { JobStatus } from './sampleJob';

export interface JobRow {
  title: string;
  status: JobStatus;
}

export const SAMPLE_JOBS: JobRow[] = [
  { title: 'Panel upgrade 200A', status: 'in_progress' },
  { title: 'Service call - 12 Oak', status: 'planned' },
  { title: 'Rewire shed', status: 'complete' },
];

export interface CommLine {
  who: string;
  text: string;
}

export const SAMPLE_COMM: CommLine[] = [
  { who: 'Mara', text: 'on my way to site' },
  { who: 'You', text: 'bring the 200A panel' },
  { who: 'Jon', text: 'permit cleared' },
];

export interface CrewMember {
  name: string;
  online: boolean;
}

export const SAMPLE_CREW: CrewMember[] = [
  { name: 'Mara D.', online: true },
  { name: 'Jon P.', online: false },
  { name: 'Lee K.', online: true },
];

export const SAMPLE_CLIENTS: string[] = ['Aegis Assure', 'Northwind Co', '4th St LLC'];

export const SAMPLE_ON_SITE = 3;
