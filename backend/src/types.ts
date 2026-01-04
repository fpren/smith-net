/**
 * Smith Net Core Types
 * Shared across backend, desktop portal, and gateway
 */

// ════════════════════════════════════════════════════════════════════
// CANONICAL CHANNEL
// ════════════════════════════════════════════════════════════════════

export type ChannelVisibility = 'public' | 'private' | 'restricted';
export type AccessStatus = 'granted' | 'pending' | 'can_request' | 'denied';

export interface Channel {
  id: string;                    // Canonical UUID - source of truth
  name: string;
  type: 'broadcast' | 'group' | 'dm';
  visibility: ChannelVisibility; // NEW: Access control level
  creatorId: string;
  createdAt: number;
  memberIds: string[];
  allowedUsers: string[];        // Users explicitly allowed (for restricted)
  blockedUsers: string[];        // Users explicitly blocked
  pendingRequests: string[];     // Users waiting for approval
  requiresApproval: boolean;     // Manual approval needed for joins
  isArchived: boolean;
  isDeleted: boolean;
  meshHash?: number;             // 2-byte hash for mesh routing (derived)
}

// ════════════════════════════════════════════════════════════════════
// MESSAGE
// ════════════════════════════════════════════════════════════════════

export type MessageOrigin = 'online' | 'mesh' | 'gateway' | 'online+mesh';

export interface Message {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: number;
  origin: MessageOrigin;
  recipientId?: string;          // For DMs
  recipientName?: string;
}

// ════════════════════════════════════════════════════════════════════
// USER / PRESENCE
// ════════════════════════════════════════════════════════════════════

export interface User {
  id: string;
  name: string;
  lastSeen: number;
  status: 'online' | 'away' | 'offline';
  role: 'user' | 'foreman' | 'admin';
}

export interface Presence {
  userId: string;
  userName: string;
  status: 'online' | 'away' | 'offline';
  lastSeen: number;
  connectionType: 'online' | 'mesh' | 'gateway' | 'mobile';
}

// ════════════════════════════════════════════════════════════════════
// GATEWAY
// ════════════════════════════════════════════════════════════════════

export type GatewayMode = 'online' | 'gateway' | 'hybrid';

export interface GatewayRelay {
  id: string;
  name: string;
  connectedAt: number;
  lastActivity: number;
  capabilities: string[];
}

export interface GatewayStatus {
  mode: GatewayMode;
  relayConnected: boolean;
  relay?: GatewayRelay;
  lastMeshActivity?: number;
}

// ════════════════════════════════════════════════════════════════════
// WEBSOCKET MESSAGES
// ════════════════════════════════════════════════════════════════════

export type WSMessageType = 
  | 'auth'
  | 'auth_ok'
  | 'auth_error'
  | 'message'
  | 'message_ack'
  | 'message_deleted'
  | 'channel_created'
  | 'channel_updated'
  | 'channel_deleted'
  | 'channel_cleared'
  | 'channel_subscribed'
  | 'channels_updated'
  | 'presence_update'
  | 'gateway_connect'
  | 'gateway_disconnect'
  | 'gateway_message'
  | 'error';

export interface WSMessage {
  type: WSMessageType;
  payload: unknown;
  timestamp: number;
}

// ════════════════════════════════════════════════════════════════════
// API REQUESTS / RESPONSES
// ════════════════════════════════════════════════════════════════════

export interface CreateChannelRequest {
  name: string;
  type: 'broadcast' | 'group' | 'dm';
  visibility?: ChannelVisibility;
  memberIds?: string[];
  requiresApproval?: boolean;
}

// ════════════════════════════════════════════════════════════════════
// ACCESS CONTROL REQUESTS
// ════════════════════════════════════════════════════════════════════

export interface AccessRequestPayload {
  channelId: string;
  userId: string;
}

export interface AccessResponsePayload {
  channelId: string;
  requesterId: string;
  approve: boolean;
}

export interface UpdateChannelAccessPayload {
  channelId: string;
  userId: string;
  allow: boolean;  // true = allow, false = block
}

export interface UpdateChannelVisibilityPayload {
  channelId: string;
  visibility: ChannelVisibility;
  requiresApproval?: boolean;
}

export interface InjectMessageRequest {
  channelId: string;
  content: string;
  origin: MessageOrigin;
}

export interface RegisterGatewayRequest {
  relayId: string;
  capabilities: string[];
}

// ════════════════════════════════════════════════════════════════════
// PLAN MANAGEMENT SYSTEM (CORE WORKFLOW)
// ════════════════════════════════════════════════════════════════════

export interface Engagement {
  id: string;
  name: string;
  description?: string;
  clientName?: string;
  location?: string;
  createdBy: string;
  createdAt: number;
  status: 'active' | 'converted' | 'archived';
  // No facts yet - only intent
  intent: string; // What the engagement is about
}

export interface Plan {
  id: string;
  engagementId: string;
  name: string;
  description?: string;

  // Phase tracking
  phase: PlanPhase;
  createdAt: number;
  updatedAt: number;

  // Serial assignment (for validated plans)
  serial?: PlanSerial;

  // Standard Flow: Proposal
  proposal?: Proposal;

  // Jobs (from jobboard component)
  jobIds: string[];

  // Time entries (from timetracking component)
  timeEntryIds: string[];

  // Synthesis phase
  summary?: PlanSummary;

  // Output generation
  outputs: PlanOutput[];

  // Archive (becomes immutable)
  archivedAt?: number;
  immutableHash?: string;
}

export type PlanPhase =
  | 'draft'           // Initial creation
  | 'proposal_pending' // Standard flow - proposal created but not confirmed
  | 'active'          // Jobs created, time tracking active
  | 'jobs_complete'   // All jobs done, all time closed
  | 'synthesis'       // Reading jobs, time, notes, chat
  | 'summary_ready'   // Summary created and confirmed
  | 'output_pending'  // Ready for output selection
  | 'output_generated' // Reports/invoices created
  | 'finalized'       // Locked and cannot be modified
  | 'archived';       // Final immutable state

export interface Proposal {
  id: string;
  planId: string;
  title: string;
  description: string;
  scope: string[];
  estimatedHours?: number;
  estimatedCost?: number;
  createdBy: string;
  createdAt: number;
  confirmedAt?: number;
  confirmedBy?: string;
  status: 'draft' | 'submitted' | 'approved' | 'rejected';
}

export interface PlanSummary {
  id: string;
  planId: string;
  title: string;
  executiveSummary: string;
  workPerformed: string[];
  challenges?: string[];
  recommendations?: string[];
  totalHours: number;
  totalCost?: number;
  createdBy: string; // AI or human
  createdAt: number;
  confirmedAt?: number;
  confirmedBy?: string;
  status: 'draft' | 'confirmed';
}

export interface PlanOutput {
  id: string;
  planId: string;
  type: 'report' | 'invoice' | 'both';
  reportId?: string;
  invoiceId?: string;
  generatedAt: number;
  generatedBy: string;
}

export interface Report {
  id: string;
  planId: string;
  title: string;
  content: string; // Narrative explanation
  // No pricing if report-only
  totalHours?: number;
  createdAt: number;
  createdBy: string;
  archived: boolean;
}

export interface Invoice {
  id: string;
  planId: string;
  title: string;
  clientName: string;
  lineItems: InvoiceLineItem[];
  subtotal: number;
  tax?: number;
  total: number;
  dueDate: number;
  status: 'draft' | 'sent' | 'paid' | 'overdue';
  createdAt: number;
  createdBy: string;
  archived: boolean;
}

export interface InvoiceLineItem {
  id: string;
  description: string;
  quantity: number;
  rate: number;
  amount: number;
  jobId?: string;
  timeEntryIds?: string[];
}

export interface PlanSnapshot {
  id: string;
  planId: string;
  snapshotType: 'archive' | 'output';
  data: Plan;
  jobs: any[]; // Job data snapshot
  timeEntries: any[]; // Time entry data snapshot
  messages: any[]; // Chat context snapshot
  createdAt: number;
  immutableHash: string;
}

// ════════════════════════════════════════════════════════════════════
// API REQUESTS FOR PLAN SYSTEM
// ════════════════════════════════════════════════════════════════════

export interface CreateEngagementRequest {
  name: string;
  description?: string;
  clientName?: string;
  location?: string;
  intent: string;
}

export interface CreatePlanRequest {
  engagementId: string;
  name: string;
  description?: string;
  flowType: 'standard' | 'small_project';
}

export interface CreateProposalRequest {
  planId: string;
  title: string;
  description: string;
  scope: string[];
  estimatedHours?: number;
  estimatedCost?: number;
}

export interface ConfirmProposalRequest {
  proposalId: string;
  approved: boolean;
  notes?: string;
}

export interface CreatePlanSummaryRequest {
  planId: string;
  useAI?: boolean; // Use AI to generate summary from data
  customSummary?: {
    title: string;
    executiveSummary: string;
    workPerformed: string[];
    challenges?: string[];
    recommendations?: string[];
  };
}

export interface ConfirmSummaryRequest {
  summaryId: string;
  approved: boolean;
  notes?: string;
}

export interface GenerateOutputRequest {
  planId: string;
  outputType: 'report_only' | 'invoice_only' | 'report_and_invoice';
  reportOptions?: {
    includePricing?: boolean;
    customContent?: string;
  };
  invoiceOptions?: {
    dueDate?: number;
    taxRate?: number;
    lineItems?: InvoiceLineItem[];
  };
}

// ════════════════════════════════════════════════════════════════════
// PLAN AUTHORITY — VALIDATION & SERIALIZATION
// ════════════════════════════════════════════════════════════════════

export interface PlanSerial {
  id: string;                    // Canonical UUID
  recordType: 'PLAN';           // Always 'PLAN'
  sequenceNumber: number;       // Atomic sequence
  timestamp: number;            // Generation timestamp
  latitude?: number;            // GPS coordinate
  longitude?: number;           // GPS coordinate
  verticalUnitId?: string;      // Vertical unit ID (if applicable)
  timeEnvelope: {               // Time bounds of all referenced work
    start: number;
    end: number;
  };
  environmentalContext?: string; // Environmental data
  actorBlock: ActorBlock;       // All actors by UUID only
  scopeReferences: {            // Explicit scope - no inference
    jobIds: string[];
    timeEntryIds: string[];
  };
  engagementId: string;         // Parent engagement
}

export interface ActorBlock {
  clientUuid: string;           // Client UUID only
  workerUuids: string[];        // Worker UUIDs only
  foremanUuid: string | null;   // Foreman UUID or null
}

export interface LedgerTransaction {
  id: string;                   // Transaction UUID
  hash: string;                 // SHA-256 hash of plan serial
  timestamp: number;            // Ledger write timestamp
  blockHeight: number;          // Blockchain block height
  transactionId: string;        // Blockchain transaction ID
  verified: boolean;            // Verification status
}

export interface PlanValidationResult {
  valid: boolean;
  planSerial?: PlanSerial;
  ledgerTransaction?: LedgerTransaction;
  rejectionReason?: string;
  message: string;
}

// ════════════════════════════════════════════════════════════════════
// REPORT ASSEMBLY & RENDERING (ARCHITECTURAL COMPONENTS)
// ════════════════════════════════════════════════════════════════════

export interface ReportAssembler {
  assembleFromPlanSnapshot(planSnapshot: PlanSnapshot): StructuredReportModel;
}

export interface StructuredReportModel {
  id: string;
  planId: string;
  sections: ReportSection[];
  metadata: ReportMetadata;
}

export interface ReportSection {
  id: string;
  title: string;
  type: 'narrative' | 'summary' | 'table' | 'totals';
  content: any; // Section-specific content structure
}

export interface ReportMetadata {
  generatedAt: number;
  generatedBy: string;
  planSerial: string;
  ledgerTx: string;
}

export interface ReportRenderer {
  render(model: StructuredReportModel, format: 'pdf' | 'html' | 'xlsx'): RenderedReport;
}

export interface RenderedReport {
  id: string;
  format: string;
  content: Buffer; // Binary content
  filename: string;
  metadata: ReportMetadata;
}

export interface ReportOutput {
  export(report: RenderedReport, destination: string): Promise<void>;
  download(report: RenderedReport): Promise<void>;
  share(report: RenderedReport, recipients: string[]): Promise<void>;
}

// ════════════════════════════════════════════════════════════════════
// TIME ENTRY
// ════════════════════════════════════════════════════════════════════

export interface TimeEntry {
  id: string;
  userId: string;
  userName: string;
  clockInTime: number;
  clockOutTime?: number;
  durationMinutes: number;
  jobId?: string;
  entryType: 'regular' | 'overtime' | 'travel' | 'break';
  source: 'manual' | 'geofence' | 'mesh';
  createdAt: number;
  immutableHash: string;
  notes: string[];
  status: 'pending' | 'approved' | 'rejected' | 'pending_review';
  clockOutContext?: {
    type: 'worker_note' | 'ai_summary';
    content: string;
    generatedAt: number;
    generatedBy: string;
    isImmutable: boolean;
  };
}
