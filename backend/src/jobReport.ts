// backend/src/jobReport.ts
//
// Per-job report rendering (W5). The client POSTs a denormalized JobReportData
// payload (it owns the data) and the backend renders a real PDF (pdfkit) or
// Excel workbook (exceljs) and returns the bytes. No headless browser -- pdfkit
// keeps the self-hosted footprint small.

import PDFDocument from 'pdfkit';
import ExcelJS from 'exceljs';

export interface JobReportMaterial {
  name: string;
  quantity: number;
  unit: string;
  unitCost: number;
  total: number;
}

export interface JobReportExpense {
  category: string;
  description: string;
  amount: number;
  vendor?: string;
  date?: string;
}

export interface JobReportData {
  jobTitle: string;
  clientName?: string;
  clientAddress?: string;
  contractorName?: string;
  workSummary?: string;
  periodLabel?: string;
  laborHours: number;
  laborRate: number;
  laborCost: number;
  materials: JobReportMaterial[];
  materialsCost: number;
  expenses: JobReportExpense[];
  expensesTotal: number;
  taxRate?: number;   // percent, e.g. 8.25
  taxAmount?: number;
  total: number;
  generatedAtMs?: number;
}

const money = (n: number | undefined | null) => `$${Number(n ?? 0).toFixed(2)}`;
const num = (n: number | undefined | null) => {
  const v = Number(n ?? 0);
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
};

/** A safe, normalized JobReportData (fills defaults so a sparse body still renders). */
export function normalizeJobReport(raw: any): JobReportData {
  return {
    jobTitle: String(raw?.jobTitle ?? 'Job Report'),
    clientName: raw?.clientName ?? undefined,
    clientAddress: raw?.clientAddress ?? undefined,
    contractorName: raw?.contractorName ?? undefined,
    workSummary: raw?.workSummary ?? undefined,
    periodLabel: raw?.periodLabel ?? undefined,
    laborHours: Number(raw?.laborHours ?? 0),
    laborRate: Number(raw?.laborRate ?? 0),
    laborCost: Number(raw?.laborCost ?? 0),
    materials: Array.isArray(raw?.materials) ? raw.materials.map((m: any) => ({
      name: String(m?.name ?? ''),
      quantity: Number(m?.quantity ?? 0),
      unit: String(m?.unit ?? 'ea'),
      unitCost: Number(m?.unitCost ?? 0),
      total: Number(m?.total ?? (Number(m?.quantity ?? 0) * Number(m?.unitCost ?? 0))),
    })) : [],
    materialsCost: Number(raw?.materialsCost ?? 0),
    expenses: Array.isArray(raw?.expenses) ? raw.expenses.map((e: any) => ({
      category: String(e?.category ?? ''),
      description: String(e?.description ?? ''),
      amount: Number(e?.amount ?? 0),
      vendor: e?.vendor ?? undefined,
      date: e?.date ?? undefined,
    })) : [],
    expensesTotal: Number(raw?.expensesTotal ?? 0),
    taxRate: raw?.taxRate != null ? Number(raw.taxRate) : undefined,
    taxAmount: raw?.taxAmount != null ? Number(raw.taxAmount) : undefined,
    total: Number(raw?.total ?? 0),
    generatedAtMs: raw?.generatedAtMs != null ? Number(raw.generatedAtMs) : undefined,
  };
}

// ── PDF (pdfkit) ─────────────────────────────────────────────────────────────

export function renderJobReportPdf(data: JobReportData): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'LETTER', margin: 50 });
    const chunks: Buffer[] = [];
    doc.on('data', (c: Buffer) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    const left = doc.page.margins.left;
    const right = doc.page.width - doc.page.margins.right;

    // Header
    doc.font('Helvetica-Bold').fontSize(18).text(data.jobTitle);
    doc.moveDown(0.2);
    doc.font('Helvetica').fontSize(10).fillColor('#555');
    if (data.contractorName) doc.text(`Contractor: ${data.contractorName}`);
    if (data.clientName) doc.text(`Client: ${data.clientName}${data.clientAddress ? ` — ${data.clientAddress}` : ''}`);
    if (data.periodLabel) doc.text(`Period: ${data.periodLabel}`);
    const dateMs = data.generatedAtMs ?? Date.now();
    doc.text(`Generated: ${new Date(dateMs).toISOString().slice(0, 10)}`);
    doc.fillColor('#000');
    doc.moveDown(0.6);
    doc.moveTo(left, doc.y).lineTo(right, doc.y).strokeColor('#ccc').stroke();
    doc.moveDown(0.6);

    const sectionTitle = (t: string) => {
      doc.moveDown(0.4).font('Helvetica-Bold').fontSize(12).fillColor('#000').text(t);
      doc.moveDown(0.2).font('Helvetica').fontSize(10);
    };

    if (data.workSummary) {
      sectionTitle('Work Performed');
      doc.text(data.workSummary, { width: right - left });
    }

    // Labor
    sectionTitle('Labor');
    doc.text(`${num(data.laborHours)} hrs @ ${money(data.laborRate)}/hr = ${money(data.laborCost)}`);

    // Materials table
    sectionTitle('Materials');
    if (data.materials.length === 0) {
      doc.fillColor('#777').text('None').fillColor('#000');
    } else {
      data.materials.forEach((m) => {
        doc.text(`${m.name} — ${num(m.quantity)} ${m.unit} @ ${money(m.unitCost)} = ${money(m.total)}`);
      });
      doc.moveDown(0.2).font('Helvetica-Bold').text(`Materials subtotal: ${money(data.materialsCost)}`).font('Helvetica');
    }

    // Expenses table
    sectionTitle('Expenses');
    if (data.expenses.length === 0) {
      doc.fillColor('#777').text('None').fillColor('#000');
    } else {
      data.expenses.forEach((e) => {
        const meta = [e.category, e.vendor, e.date].filter(Boolean).join(' · ');
        doc.text(`${e.description}${meta ? ` (${meta})` : ''} — ${money(e.amount)}`);
      });
      doc.moveDown(0.2).font('Helvetica-Bold').text(`Expenses subtotal: ${money(data.expensesTotal)}`).font('Helvetica');
    }

    // Totals
    doc.moveDown(0.6);
    doc.moveTo(left, doc.y).lineTo(right, doc.y).strokeColor('#ccc').stroke();
    doc.moveDown(0.4).font('Helvetica-Bold').fontSize(11);
    if (data.taxAmount != null) {
      doc.font('Helvetica').text(`Tax${data.taxRate != null ? ` (${num(data.taxRate)}%)` : ''}: ${money(data.taxAmount)}`);
    }
    doc.font('Helvetica-Bold').fontSize(13).text(`Total: ${money(data.total)}`, { align: 'right' });

    doc.end();
  });
}

// ── Excel (exceljs) ──────────────────────────────────────────────────────────

export async function renderJobReportXlsx(data: JobReportData): Promise<Buffer> {
  const wb = new ExcelJS.Workbook();
  wb.creator = 'Smith Net';
  const ws = wb.addWorksheet('Job Report');
  ws.columns = [
    { header: '', key: 'a', width: 28 },
    { header: '', key: 'b', width: 14 },
    { header: '', key: 'c', width: 10 },
    { header: '', key: 'd', width: 14 },
    { header: '', key: 'e', width: 14 },
  ];

  const title = ws.addRow([data.jobTitle]);
  title.font = { bold: true, size: 16 };
  if (data.contractorName) ws.addRow([`Contractor: ${data.contractorName}`]);
  if (data.clientName) ws.addRow([`Client: ${data.clientName}${data.clientAddress ? ` — ${data.clientAddress}` : ''}`]);
  if (data.periodLabel) ws.addRow([`Period: ${data.periodLabel}`]);
  ws.addRow([`Generated: ${new Date(data.generatedAtMs ?? Date.now()).toISOString().slice(0, 10)}`]);
  ws.addRow([]);

  if (data.workSummary) {
    const h = ws.addRow(['Work Performed']); h.font = { bold: true };
    ws.addRow([data.workSummary]);
    ws.addRow([]);
  }

  const headerRow = (cells: string[]) => {
    const r = ws.addRow(cells);
    r.font = { bold: true };
    return r;
  };

  // Labor
  headerRow(['Labor']);
  ws.addRow(['Hours', data.laborHours, 'Rate', data.laborRate, data.laborCost]);
  ws.addRow([]);

  // Materials
  headerRow(['Materials', 'Qty', 'Unit', 'Unit Cost', 'Total']);
  data.materials.forEach((m) => ws.addRow([m.name, m.quantity, m.unit, m.unitCost, m.total]));
  const matSub = ws.addRow(['Materials subtotal', '', '', '', data.materialsCost]);
  matSub.font = { bold: true };
  ws.addRow([]);

  // Expenses
  headerRow(['Expenses', 'Category', 'Vendor', 'Date', 'Amount']);
  data.expenses.forEach((e) => ws.addRow([e.description, e.category, e.vendor ?? '', e.date ?? '', e.amount]));
  const expSub = ws.addRow(['Expenses subtotal', '', '', '', data.expensesTotal]);
  expSub.font = { bold: true };
  ws.addRow([]);

  // Totals
  if (data.taxAmount != null) {
    ws.addRow([`Tax${data.taxRate != null ? ` (${data.taxRate}%)` : ''}`, '', '', '', data.taxAmount]);
  }
  const totalRow = ws.addRow(['Total', '', '', '', data.total]);
  totalRow.font = { bold: true, size: 13 };

  // Currency format on the money column (E) where numeric.
  ws.getColumn('e').numFmt = '$#,##0.00';
  ws.getColumn('d').numFmt = '$#,##0.00';

  const out = await wb.xlsx.writeBuffer();
  return Buffer.isBuffer(out) ? out : Buffer.from(out as ArrayBuffer);
}
