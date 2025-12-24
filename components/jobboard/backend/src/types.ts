/**
 * C-11: Job Board Types
 * Role-adaptive task management system
 */

// ════════════════════════════════════════════════════════════════════
// JOB / PROJECT
// ════════════════════════════════════════════════════════════════════

export interface Job {
  id: string;
  title: string;
  description: string;
  projectId?: string;
  clientName?: string;
  location?: string;
  status: JobStatus;
  priority: Priority;
  createdBy: string;
  assignedTo: string[];
  createdAt: number;
  updatedAt: number;
  dueDate?: number;
  completedAt?: number;
  tags: string[];
  metadata: Record<string, any>;
}

export type JobStatus = 
  | 'backlog'
  | 'todo'
  | 'in_progress'
  | 'review'
  | 'done'
  | 'archived';

export type Priority = 'low' | 'medium' | 'high' | 'urgent';

// ════════════════════════════════════════════════════════════════════
// TASK (Sub-items within a Job)
// ════════════════════════════════════════════════════════════════════

export interface Task {
  id: string;
  jobId: string;
  title: string;
  description?: string;
  status: TaskStatus;
  assignedTo?: string;
  createdBy: string;
  createdAt: number;
  updatedAt: number;
  completedAt?: number;
  order: number;         // For drag-drop ordering
  checklist: ChecklistItem[];
}

export type TaskStatus = 'pending' | 'in_progress' | 'done' | 'blocked';

export interface ChecklistItem {
  id: string;
  text: string;
  checked: boolean;
  checkedAt?: number;
  checkedBy?: string;
}

// ════════════════════════════════════════════════════════════════════
// BOARD VIEW
// ════════════════════════════════════════════════════════════════════

export interface BoardColumn {
  id: string;
  name: string;
  status: JobStatus;
  order: number;
  jobIds: string[];
}

export interface BoardView {
  id: string;
  name: string;
  ownerId: string;
  columns: BoardColumn[];
  filters: BoardFilters;
  createdAt: number;
}

export interface BoardFilters {
  assignedTo?: string[];
  priority?: Priority[];
  tags?: string[];
  dateRange?: {
    start: number;
    end: number;
  };
}

// ════════════════════════════════════════════════════════════════════
// USER CONTEXT (from SmithNet auth)
// ════════════════════════════════════════════════════════════════════

export interface UserContext {
  userId: string;
  email: string;
  displayName: string;
  role: string;
  organizationId?: string;
}

// ════════════════════════════════════════════════════════════════════
// API REQUESTS
// ════════════════════════════════════════════════════════════════════

export interface CreateJobRequest {
  title: string;
  description?: string;
  projectId?: string;
  clientName?: string;
  location?: string;
  priority?: Priority;
  assignedTo?: string[];
  dueDate?: number;
  tags?: string[];
}

export interface UpdateJobRequest {
  title?: string;
  description?: string;
  status?: JobStatus;
  priority?: Priority;
  assignedTo?: string[];
  dueDate?: number;
  tags?: string[];
}

export interface CreateTaskRequest {
  jobId: string;
  title: string;
  description?: string;
  assignedTo?: string;
}

export interface MoveJobRequest {
  jobId: string;
  newStatus: JobStatus;
  newOrder?: number;
}
