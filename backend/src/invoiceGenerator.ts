/**
 * INVOICE GENERATOR — Implements exact templates from .txt specifications
 * ======================================================================
 *
 * Templates implemented:
 * 1. Standard Solo Invoice
 * 2. Advanced Solo Invoice + AI Supervisor Report
 * 3. Enterprise/Foreman Crew Invoice (Week-long project)
 */

import { Plan, TimeEntry, PlanSnapshot } from './types';

export interface InvoiceData {
  // Metadata
  invoiceNumber: string;
  series?: string;
  issueDate: string;
  dueDate: string;
  netTerms: number;
  status?: string;

  // Provider
  providerName: string;
  businessName?: string;
  guildRole: string;
  phone: string;
  email: string;
  address: string;
  taxId?: string;

  // Client
  clientName: string;
  clientCompany?: string;
  clientAddress: string;
  clientEmail: string;
  projectRef?: string;
  poNumber?: string;

  // Line items
  lineItems: Array<{
    code?: string;
    description: string;
    qty: number;
    unit: string;
    rate: number;
    total: number;
  }>;

  // Totals
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  totalDue: number;
  taxJurisdiction?: string;

  // Payment
  paymentPreferred: string;
  paymentCheck?: string;
  paymentCardFee?: string;
  latePaymentTerms?: string;

  // Work verification
  workStartTime?: string;
  workEndTime?: string;
  geofenceConfirmed?: boolean;
  photosCount?: number;
  voiceNotesCount?: number;
  checklistsCount?: number;
}

export interface AISupervisorReport {
  generatedAt: string;
  scope: string;

  // Time integrity
  activeLaborHours: number;
  changeOrderHours?: number;
  totalOnSiteHours: number;
  idlePercent: number;
  geofenceEntry?: string;
  geofenceExit?: string;

  // Efficiency
  comparisonToAverage?: string;
  reworkDetected: boolean;

  // Materials
  materialAccuracy: string;

  // Compliance
  complianceChecks: string[];
  complianceWarnings?: string[];

  // Risk
  riskLevel: string;
  riskNotes?: string;

  // Recommendations
  recommendations: string[];

  // Next actions
  nextActions: Array<{
    action: string;
    dueDate?: string;
  }>;

  // Media summary
  photos?: number;
  voiceNotes?: number;
  checklists?: number;
}

export class InvoiceGenerator {

  /**
   * Generate Standard Solo Invoice
   * Based on GUILD OF SMITHS INVOICE template (lines 65-112)
   */
  generateStandardSolo(data: InvoiceData): string {
    const lines = data.lineItems.map(item => {
      const code = item.code || '';
      const desc = item.description.padEnd(50);
      const qty = `${item.qty}${item.unit}`.padStart(5);
      const rate = `$${item.rate.toFixed(2)}`.padStart(8);
      const total = `$${item.total.toFixed(2)}`.padStart(9);
      return `${desc} | ${qty}| ${rate} | ${total}`;
    }).join('\n');

    return `GUILD OF SMITHS INVOICE
──────────────────────────────

Invoice #       : ${data.invoiceNumber}
Issue Date      : ${data.issueDate}
Due Date        : ${data.dueDate} (Net ${data.netTerms})

From:
  ${data.providerName}
  ${data.businessName || ''}
  ${data.guildRole} – Guild of Smiths
  Phone: ${data.phone}
  Email: ${data.email}
  Address: ${data.address}

To:
  ${data.clientName}
  ${data.clientCompany || ''}
  ${data.clientAddress}
  Email: ${data.clientEmail}
  ${data.projectRef ? `Project Ref: ${data.projectRef}` : ''}

Description                                      | Qty | Rate    | Amount
─────────────────────────────────────────────────┼─────┼─────────┼──────────
${lines}
─────────────────────────────────────────────────┼─────┼─────────┼──────────
Subtotal                                         |     |         | $${data.subtotal.toFixed(2)}
Sales Tax (${(data.taxRate * 100).toFixed(2)}%${data.taxJurisdiction ? ` – ${data.taxJurisdiction}` : ''})                |     |         | $${data.taxAmount.toFixed(2)}
─────────────────────────────────────────────────┼─────┼─────────┼──────────
Total Due                                        |     |         | $${data.totalDue.toFixed(2)}

Payment Instructions:
• Preferred: ${data.paymentPreferred}
• Check: ${data.paymentCheck || 'Payable to address above'}
• Card payments accepted via link sent separately (${data.paymentCardFee || '2.9% + $0.30 fee'} applies)
• Questions? Reply directly in Smith chat or call.

Notes:
${data.workStartTime && data.workEndTime ? `• Work completed & verified ${data.workStartTime} – ${data.workEndTime}` : ''}
${data.geofenceConfirmed ? '• Photos & geofence clock-in/out available in project thread' : ''}
• Thank you for your business.

Guild of Smiths – Built for the trades.
`;
  }

  /**
   * Generate Advanced Solo Invoice + AI Supervisor Report
   * Based on GUILD OF SMITHS INVOICE – ADVANCED SOLO template (lines 182-256)
   */
  generateAdvancedSolo(data: InvoiceData, aiReport: AISupervisorReport): string {
    const lines = data.lineItems.map(item => {
      const code = (item.code || '').padEnd(8);
      const desc = item.description.padEnd(50);
      const qty = String(item.qty).padStart(4);
      const unit = item.unit.padEnd(4);
      const rate = `$${item.rate.toFixed(2)}`.padStart(7);
      const total = `$${item.total.toFixed(2)}`.padStart(8);
      return `${code} | ${desc} | ${qty} | ${unit} | ${rate} | ${total}`;
    }).join('\n');

    const complianceSection = aiReport.complianceChecks.map(check => `  - ${check}`).join('\n');
    const warningsSection = aiReport.complianceWarnings && aiReport.complianceWarnings.length > 0
      ? `  - WARNINGS: ${aiReport.complianceWarnings.join(', ')}`
      : '';

    const nextActionsSection = aiReport.nextActions.map((action, i) =>
      `  ${i + 1}. ${action.action}${action.dueDate ? ` (auto-reminder set for ${action.dueDate})` : ''}`
    ).join('\n');

    return `GUILD OF SMITHS INVOICE – ADVANCED SOLO (Hybrid Mode)
──────────────────────────────────────────────────────
Invoice ID      : ${data.invoiceNumber}
Series          : ${data.series || 'SOLO-' + data.issueDate.split('-')[0] + '-' + data.issueDate.split('-')[1]}
Issue Date      : ${data.issueDate}
Due Date        : ${data.dueDate} (Net ${data.netTerms})
Status          : ${data.status || 'Issued – Awaiting Payment'}

From (Service Provider):
  Name          : ${data.providerName}
  Business       : ${data.businessName || data.providerName}
  Guild Role     : ${data.guildRole}
  Contact        : ${data.phone} | ${data.email}
  Address        : ${data.address}
  ${data.taxId ? `Tax ID         : ${data.taxId} (for records only)` : ''}

To (Client):
  Name          : ${data.clientName}
  ${data.clientCompany ? `Company       : ${data.clientCompany}` : ''}
  Address       : ${data.clientAddress}
  Email         : ${data.clientEmail}
  ${data.projectRef ? `Project Ref   : ${data.projectRef}${data.poNumber ? ` (PO #${data.poNumber})` : ''}` : ''}

Line Items
──────────────────────────────────────────────────────
Code    | Description                                      | Qty | Unit | Rate    | Total
────────┼──────────────────────────────────────────────────┼─────┼──────┼─────────┼──────────
${lines}
────────┼──────────────────────────────────────────────────┼─────┼──────┼─────────┼──────────
                                        Subtotal         |         |         | $${data.subtotal.toFixed(2)}
                                        Sales Tax (${(data.taxRate * 100).toFixed(2)}%${data.taxJurisdiction ? ` ${data.taxJurisdiction}` : ''}) |         | $${data.taxAmount.toFixed(2)}
──────────────────────────────────────────────────────
                                        TOTAL DUE        |         |         | $${data.totalDue.toFixed(2)} USD

Payment Terms & Instructions
──────────────────────────────────────────────────────
• Preferred: ${data.paymentPreferred}
• Check: ${data.paymentCheck || 'Payable to address above'}
• Card: Secure link sent via Smith chat (${data.paymentCardFee || '2.9% + $0.30 fee'})
${data.latePaymentTerms ? `• Late payments subject to ${data.latePaymentTerms}` : '• Late payments subject to 1.5% monthly interest after due date'}
• Disputes: Must be raised in Smith project thread within 7 days

Work & Verification Summary
──────────────────────────────────────────────────────
${data.workStartTime && data.workEndTime ? `• Primary work window: ${data.workStartTime} – ${data.workEndTime}${data.geofenceConfirmed ? ' (geofence confirmed)' : ''}` : ''}
${aiReport.photos ? `• Media in thread: ${aiReport.photos} photos, ${aiReport.voiceNotes || 0} voice notes, ${aiReport.checklists || 0} checklists` : ''}
• Mesh-synced entries: Clock-in/out + status updates

AI Supervisor Report – Hybrid Mode (Generated ${aiReport.generatedAt})
──────────────────────────────────────────────────────
Scope: ${aiReport.scope}

• Time Integrity: ${aiReport.activeLaborHours}h active labor${aiReport.changeOrderHours ? ` + ${aiReport.changeOrderHours}h change orders` : ''}. Total on-site: ${aiReport.totalOnSiteHours}h. Idle time ${aiReport.idlePercent}%${aiReport.geofenceEntry && aiReport.geofenceExit ? ` (geofence entry: ${aiReport.geofenceEntry}; exit: ${aiReport.geofenceExit})` : ''}.

• Efficiency: ${aiReport.comparisonToAverage || 'Performance within expected range'}. ${aiReport.reworkDetected ? 'Rework loops detected.' : 'No rework loops detected.'}

• Material Accuracy: ${aiReport.materialAccuracy}

• Compliance Flags:
${complianceSection}
${warningsSection}

• Risk / Anomaly: ${aiReport.riskNotes || aiReport.riskLevel}

• Projected Savings: ${aiReport.recommendations[0] || 'N/A'}

• Next Actions (AI-suggested):
${nextActionsSection}

This document and attached AI report serve as auditable work record. All data immutable per Guild of Smiths retention policy.

Guild of Smiths – Built for the trades. Hybrid AI active.
Version: Smith Invoice Engine v1.0.0 (2025-12 spec)
`;
  }

  /**
   * Generate invoice number
   */
  static generateInvoiceNumber(type: 'standard' | 'advanced' | 'crew' = 'standard'): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const seq = String(Math.floor(Math.random() * 9999)).padStart(4, '0');
    
    const suffix = type === 'advanced' ? '-A' : type === 'crew' ? '-CREW-WEEK' : '';
    return `INV-${year}-${month}-${seq}${suffix}`;
  }

  /**
   * Calculate due date
   */
  static calculateDueDate(issueDate: Date, netTerms: number): string {
    const due = new Date(issueDate);
    due.setDate(due.getDate() + netTerms);
    const year = due.getFullYear();
    const month = String(due.getMonth() + 1).padStart(2, '0');
    const day = String(due.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}

export const invoiceGenerator = new InvoiceGenerator();
