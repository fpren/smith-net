// resolver/playwrightSearch.ts
// Enhanced Playwright-based job research for TEST ⧉ compile path
import { chromium } from 'playwright';

/**
 * ENHANCED JOB RESEARCH OUTPUT
 * 
 * Returns a comprehensive job structure matching GOSPLAN template:
 * - Job header (title, client, location, type, trade, urgency)
 * - Scope summary with detailed breakdown
 * - Materials list with quantities
 * - Labor estimates
 * - Task checklist
 * - Financial snapshot
 * - Safety/code requirements
 * - Timeline estimates
 */
export interface EnhancedJobData {
  // Job Header
  jobTitle: string;
  clientName: string | null;
  location: string | null;
  jobType: string;
  primaryTrade: string;
  urgency: 'low' | 'moderate' | 'high' | 'urgent';
  
  // Scope
  scope: string;
  scopeDetails: string[];
  
  // Tasks (execution checklist)
  tasks: string[];
  
  // Materials
  materials: Array<{
    name: string;
    quantity: string | null;
    unit: string | null;
    estimatedCost: number | null;
  }>;
  
  // Labor
  labor: Array<{
    role: string;
    hours: number;
    rate: number;
  }>;
  crewSize: number;
  
  // Timeline
  estimatedDays: number;
  phases: Array<{
    name: string;
    description: string;
    order: number;
  }>;
  
  // Financial
  estimatedLaborCost: number;
  estimatedMaterialCost: number;
  estimatedTotal: number;
  depositRequired: string;
  warranty: string;
  
  // Safety & Code
  safetyRequirements: string[];
  codeRequirements: string[];
  permitRequired: boolean;
  inspectionRequired: boolean;
  
  // Notes
  assumptions: string[];
  exclusions: string[];
  notes: string[];
  
  // Metadata
  detectedKeywords: string[];
  tradeClassification: string;
  researchSources: string[];
}

/**
 * Trade-specific knowledge base for enriching job data
 */
const TRADE_KNOWLEDGE: Record<string, {
  materials: Array<{ name: string; unit: string }>;
  phases: Array<{ name: string; description: string }>;
  safety: string[];
  code: string[];
  laborRoles: string[];
  typicalCrewSize: number;
  typicalDaysPerUnit: number;
}> = {
  MASONRY: {
    materials: [
      { name: 'Fire brick', unit: 'ea' },
      { name: 'Fireclay mortar', unit: 'bags' },
      { name: 'Refractory cement', unit: 'bags' },
      { name: 'Common brick', unit: 'ea' },
      { name: 'Type S mortar', unit: 'bags' },
      { name: 'Sand', unit: 'cu yd' },
      { name: 'Flue liner', unit: 'ea' },
      { name: 'Chimney cap', unit: 'ea' },
      { name: 'Damper assembly', unit: 'ea' },
      { name: 'Concrete block', unit: 'ea' },
      { name: 'Rebar', unit: 'ft' },
      { name: 'Wire ties', unit: 'box' },
    ],
    phases: [
      { name: 'Site Prep', description: 'Layout, material staging, protection' },
      { name: 'Foundation', description: 'Footing preparation, base construction' },
      { name: 'Firebox Construction', description: 'Build firebox with fire brick' },
      { name: 'Chimney Build', description: 'Stack chimney, install flue liner' },
      { name: 'Cap & Finish', description: 'Install cap, damper, finish pointing' },
      { name: 'Cure & Clean', description: 'Allow curing, clean up, final inspection' },
    ],
    safety: [
      'Fall protection above 6 feet',
      'Dust mask/respirator for mortar mixing',
      'Eye protection when cutting brick',
      'Proper lifting techniques for heavy materials',
    ],
    code: [
      'Firebox minimum dimensions per IRC R1001',
      'Hearth extension requirements',
      'Chimney height above roof per code',
      'Clearance to combustibles',
      'Flue sizing per appliance BTU',
    ],
    laborRoles: ['Lead Mason', 'Mason Helper', 'Laborer'],
    typicalCrewSize: 2,
    typicalDaysPerUnit: 5,
  },
  ELECTRICAL: {
    materials: [
      { name: 'Romex 12/2', unit: 'ft' },
      { name: 'Romex 14/2', unit: 'ft' },
      { name: 'Wire nuts', unit: 'box' },
      { name: 'Outlet boxes', unit: 'ea' },
      { name: 'Switch boxes', unit: 'ea' },
      { name: 'GFCI outlets', unit: 'ea' },
      { name: 'Standard outlets', unit: 'ea' },
      { name: 'Switches', unit: 'ea' },
      { name: 'Circuit breakers', unit: 'ea' },
      { name: 'Conduit', unit: 'ft' },
    ],
    phases: [
      { name: 'Planning', description: 'Review scope, pull permits' },
      { name: 'Rough-in', description: 'Run cables, install boxes' },
      { name: 'Inspection', description: 'Rough-in inspection' },
      { name: 'Finish', description: 'Install devices, covers' },
      { name: 'Testing', description: 'Test circuits, final inspection' },
    ],
    safety: [
      'De-energize and lock-out before work',
      'Test for voltage before touching wires',
      'Arc-flash PPE for panel work',
    ],
    code: [
      'GFCI protection in wet locations',
      'AFCI protection in bedrooms',
      'Proper wire gauge for circuit amperage',
      'Box fill calculations',
    ],
    laborRoles: ['Licensed Electrician', 'Apprentice'],
    typicalCrewSize: 1,
    typicalDaysPerUnit: 2,
  },
  PLUMBING: {
    materials: [
      { name: 'PEX tubing', unit: 'ft' },
      { name: 'Copper pipe', unit: 'ft' },
      { name: 'PVC pipe', unit: 'ft' },
      { name: 'Fittings assortment', unit: 'lot' },
      { name: 'Shut-off valves', unit: 'ea' },
      { name: 'P-traps', unit: 'ea' },
      { name: 'Teflon tape', unit: 'rolls' },
      { name: 'Pipe hangers', unit: 'box' },
    ],
    phases: [
      { name: 'Planning', description: 'Review scope, pull permits' },
      { name: 'Rough-in', description: 'Run supply and drain lines' },
      { name: 'Pressure Test', description: 'Test for leaks' },
      { name: 'Inspection', description: 'Rough-in inspection' },
      { name: 'Finish', description: 'Install fixtures' },
    ],
    safety: [
      'Shut off water supply before work',
      'Proper ventilation for soldering',
      'Eye protection when cutting pipe',
    ],
    code: [
      'Proper venting per DWV code',
      'Minimum fixture unit calculations',
      'Backflow prevention requirements',
    ],
    laborRoles: ['Licensed Plumber', 'Apprentice'],
    typicalCrewSize: 1,
    typicalDaysPerUnit: 2,
  },
  CARPENTRY: {
    materials: [
      { name: 'Lumber 2x4', unit: 'ea' },
      { name: 'Lumber 2x6', unit: 'ea' },
      { name: 'Plywood', unit: 'sheets' },
      { name: 'Nails', unit: 'lbs' },
      { name: 'Screws', unit: 'box' },
      { name: 'Wood glue', unit: 'bottles' },
      { name: 'Trim/molding', unit: 'ft' },
    ],
    phases: [
      { name: 'Layout', description: 'Measure and mark' },
      { name: 'Cut', description: 'Cut materials to size' },
      { name: 'Assemble', description: 'Build structure' },
      { name: 'Finish', description: 'Sand, fill, prep' },
    ],
    safety: [
      'Eye protection when cutting',
      'Hearing protection for power tools',
      'Proper saw guards in place',
    ],
    code: [
      'Structural load requirements',
      'Fire blocking requirements',
      'Fastener schedules per code',
    ],
    laborRoles: ['Carpenter', 'Helper'],
    typicalCrewSize: 2,
    typicalDaysPerUnit: 3,
  },
  GENERAL: {
    materials: [
      { name: 'Various materials', unit: 'lot' },
    ],
    phases: [
      { name: 'Planning', description: 'Review scope and requirements' },
      { name: 'Preparation', description: 'Gather materials, prep area' },
      { name: 'Execution', description: 'Perform main work' },
      { name: 'Cleanup', description: 'Clean area, remove debris' },
      { name: 'Verification', description: 'Verify work completion' },
    ],
    safety: [
      'PPE as required for task',
      'Work area safety barriers',
    ],
    code: [
      'Verify applicable codes for work type',
    ],
    laborRoles: ['Tradesperson', 'Helper'],
    typicalCrewSize: 2,
    typicalDaysPerUnit: 2,
  },
};

/**
 * Detect trade classification from input text
 */
function detectTrade(input: string): string {
  const lower = input.toLowerCase();
  
  if (/fireplace|chimney|brick|mortar|masonry|stone|concrete|foundation|block/.test(lower)) {
    return 'MASONRY';
  }
  if (/wire|wiring|electrical|outlet|switch|circuit|breaker|panel|voltage/.test(lower)) {
    return 'ELECTRICAL';
  }
  if (/pipe|plumb|drain|faucet|toilet|water|sewage|valve/.test(lower)) {
    return 'PLUMBING';
  }
  if (/wood|frame|framing|cabinet|trim|door|window|deck|stair|carpenter/.test(lower)) {
    return 'CARPENTRY';
  }
  if (/hvac|heating|cooling|furnace|ac|air condition|duct/.test(lower)) {
    return 'HVAC';
  }
  if (/roof|shingle|gutter|flashing/.test(lower)) {
    return 'ROOFING';
  }
  if (/paint|primer|stain|finish|coat/.test(lower)) {
    return 'PAINTING';
  }
  if (/floor|tile|carpet|hardwood|laminate/.test(lower)) {
    return 'FLOORING';
  }
  
  return 'GENERAL';
}

/**
 * Extract client name from input
 */
function extractClient(input: string): string | null {
  // Pattern: "for [Name]" or "client: [Name]" or "[Name]'s"
  const forMatch = input.match(/\bfor\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)/);
  if (forMatch) return forMatch[1];
  
  const clientMatch = input.match(/client[:\s]+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)/i);
  if (clientMatch) return clientMatch[1];
  
  const possessiveMatch = input.match(/([A-Z][a-z]+)'s\s+(?:home|house|property|place)/);
  if (possessiveMatch) return possessiveMatch[1];
  
  return null;
}

/**
 * Extract location from input
 */
function extractLocation(input: string): string | null {
  // Common location patterns
  const inMatch = input.match(/\bin\s+([A-Z][a-zA-Z\s,]+(?:,\s*[A-Z]{2})?)/);
  if (inMatch) return inMatch[1].trim();
  
  const atMatch = input.match(/\bat\s+(\d+[^,]+,\s*[A-Z][a-zA-Z\s,]+)/);
  if (atMatch) return atMatch[1].trim();
  
  // City, State pattern
  const cityStateMatch = input.match(/([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?),?\s*([A-Z]{2}|D\.?C\.?)/);
  if (cityStateMatch) return `${cityStateMatch[1]}, ${cityStateMatch[2]}`;
  
  return null;
}

/**
 * Generate job title from input
 */
function generateJobTitle(input: string, trade: string, client: string | null, location: string | null): string {
  // Extract the main action/object
  const cleanInput = input.replace(/\bfor\s+\w+/gi, '').replace(/\bin\s+[\w\s,]+/gi, '').trim();
  
  // Capitalize first letter of each word
  const titleCase = cleanInput.split(' ')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
  
  let title = titleCase || `${trade} Work`;
  
  if (client) {
    title += ` for ${client}`;
  }
  if (location) {
    title += ` - ${location}`;
  }
  
  return title.substring(0, 100); // Cap at 100 chars
}

/**
 * Estimate urgency from input
 */
function estimateUrgency(input: string): 'low' | 'moderate' | 'high' | 'urgent' {
  const lower = input.toLowerCase();
  
  if (/urgent|emergency|asap|immediately|critical/.test(lower)) return 'urgent';
  if (/soon|quickly|before\s+winter|time.?sensitive/.test(lower)) return 'high';
  if (/when.?available|no\s+rush|flexible/.test(lower)) return 'low';
  
  return 'moderate';
}

/**
 * Main resolver function - Enhanced for comprehensive job research
 */
export async function playwrightResolve(query: string): Promise<EnhancedJobData | null> {
  let browser;

  try {
    console.log(`[PlaywrightSearch] Starting enhanced research for: ${query}`);

    // Detect trade first
    const trade = detectTrade(query);
    const tradeKnowledge = TRADE_KNOWLEDGE[trade] || TRADE_KNOWLEDGE.GENERAL;
    
    // Extract structured data from input
    const clientName = extractClient(query);
    const location = extractLocation(query);
    const urgency = estimateUrgency(query);
    const jobTitle = generateJobTitle(query, trade, clientName, location);
    
    console.log(`[PlaywrightSearch] Detected: trade=${trade}, client=${clientName}, location=${location}`);
    
    // Launch browser for online research
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
      userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
    });
    const page = await context.newPage();

    // Search for specific trade information
    const searchQuery = encodeURIComponent(`${query} ${trade.toLowerCase()} materials list cost estimate how to`);
    const searchUrl = `https://www.bing.com/search?q=${searchQuery}`;

    await page.goto(searchUrl, {
      waitUntil: 'domcontentloaded',
      timeout: 5000,
    });

    // Extract search results (code runs in browser context)
    const searchResults: string[] = await page.evaluate(`
      (() => {
        const results = [];
      document.querySelectorAll('li.b_algo h2, li.b_algo p').forEach(el => {
        const t = el.textContent?.trim();
          if (t && t.length < 300) results.push(t);
        });
        return results.slice(0, 15);
      })()
    `) as string[];

    const searchText = searchResults.join(' ');
    console.log(`[PlaywrightSearch] Got ${searchResults.length} search results`);

    // Build comprehensive job data
    const jobData: EnhancedJobData = {
      // Job Header
      jobTitle,
      clientName,
      location,
      jobType: `${trade} / ${getJobSubtype(query, trade)}`,
      primaryTrade: trade,
      urgency,
      
      // Scope
      scope: generateScope(query, trade, searchText),
      scopeDetails: generateScopeDetails(query, trade, searchText),
      
      // Tasks from trade knowledge + search enrichment
      tasks: generateTasks(query, trade, tradeKnowledge, searchText),
      
      // Materials from trade knowledge
      materials: generateMaterials(query, trade, tradeKnowledge, searchText),
      
      // Labor
      labor: generateLabor(trade, tradeKnowledge),
      crewSize: tradeKnowledge.typicalCrewSize,
      
      // Timeline
      estimatedDays: calculateEstimatedDays(query, trade, tradeKnowledge),
      phases: tradeKnowledge.phases.map((p, i) => ({ ...p, order: i + 1 })),
      
      // Financial
      estimatedLaborCost: calculateLaborCost(trade, tradeKnowledge),
      estimatedMaterialCost: calculateMaterialCost(trade, tradeKnowledge),
      estimatedTotal: 0, // Will be calculated
      depositRequired: '50% on approval',
      warranty: trade === 'MASONRY' ? '2 years workmanship' : '1 year workmanship',
      
      // Safety & Code
      safetyRequirements: tradeKnowledge.safety,
      codeRequirements: tradeKnowledge.code,
      permitRequired: ['ELECTRICAL', 'PLUMBING', 'MASONRY'].includes(trade),
      inspectionRequired: ['ELECTRICAL', 'PLUMBING', 'MASONRY'].includes(trade),
      
      // Notes
      assumptions: generateAssumptions(query, trade, searchText),
      exclusions: generateExclusions(query, trade),
      notes: [`Trade classification: ${trade}`, 'Estimates based on typical project scope'],
      
      // Metadata
      detectedKeywords: extractKeywords(query, searchText),
      tradeClassification: trade,
      researchSources: ['Bing Search', 'Trade Knowledge Base'],
    };
    
    // Calculate total
    jobData.estimatedTotal = jobData.estimatedLaborCost + jobData.estimatedMaterialCost;

    console.log(`[PlaywrightSearch] Generated job data: ${jobData.tasks.length} tasks, ${jobData.materials.length} materials`);

    return jobData;

  } catch (err) {
    console.error('[PlaywrightSearch] Error:', err);
    return null;
  } finally {
    if (browser) await browser.close();
  }
}

// Helper functions

function getJobSubtype(input: string, trade: string): string {
  const lower = input.toLowerCase();
  
  if (trade === 'MASONRY') {
    if (/fireplace/.test(lower)) return 'Fireplace Construction';
    if (/chimney/.test(lower)) return 'Chimney Work';
    if (/foundation/.test(lower)) return 'Foundation Work';
    if (/repair|fix/.test(lower)) return 'Masonry Repair';
    return 'Masonry Construction';
  }
  if (trade === 'ELECTRICAL') {
    if (/panel/.test(lower)) return 'Panel Upgrade';
    if (/outlet/.test(lower)) return 'Outlet Installation';
    if (/rewire/.test(lower)) return 'Rewiring';
    return 'Electrical Work';
  }
  
  return 'General Work';
}

function generateScope(input: string, trade: string, searchText: string): string {
  const subtype = getJobSubtype(input, trade);
  return `[${trade}] ${subtype} - ${input.charAt(0).toUpperCase() + input.slice(1)}`;
}

function generateScopeDetails(input: string, trade: string, searchText: string): string[] {
  const details: string[] = [];
  const lower = input.toLowerCase();
  
  if (trade === 'MASONRY' && /fireplace/.test(lower)) {
    details.push('Construct new masonry fireplace per client specifications');
    details.push('Build firebox with fire-rated materials');
    details.push('Install flue liner and chimney');
    details.push('Install damper assembly');
    details.push('Apply finish pointing and cleaning');
    details.push('Ensure code compliance for clearances');
  } else if (trade === 'ELECTRICAL') {
    details.push('Install electrical components per NEC code');
    details.push('Run appropriate gauge wiring');
    details.push('Install protective devices (GFCI/AFCI as required)');
    details.push('Test all circuits for proper operation');
  } else {
    details.push(`Perform ${trade.toLowerCase()} work as specified`);
    details.push('Ensure all work meets applicable codes');
    details.push('Clean up work area upon completion');
  }
  
  return details;
}

function generateTasks(input: string, trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL, searchText: string): string[] {
  const tasks: string[] = [];
  
  // Add phase-based tasks
  knowledge.phases.forEach(phase => {
    tasks.push(`[${phase.name}] ${phase.description}`);
  });
  
  // Add inspection task if required
  if (['ELECTRICAL', 'PLUMBING', 'MASONRY'].includes(trade)) {
    tasks.push('Schedule and pass required inspections');
  }
  
  // Standard closing tasks
  tasks.push('Final walkthrough with client');
  tasks.push('Client sign-off on completed work');
  tasks.push('Generate invoice and close job');
  
  return tasks;
}

function generateMaterials(input: string, trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL, searchText: string): EnhancedJobData['materials'] {
  const lower = input.toLowerCase();
  
  // Get base materials from trade knowledge
  const materials = knowledge.materials.map(m => ({
    name: m.name,
    quantity: estimateMaterialQuantity(m.name, input, trade),
    unit: m.unit,
    estimatedCost: estimateMaterialCost(m.name, trade),
  }));
  
  // Add fireplace-specific materials if detected
  if (trade === 'MASONRY' && /fireplace/.test(lower)) {
    const fireplaceSpecific = [
      { name: 'Mantel (if requested)', quantity: '1', unit: 'ea', estimatedCost: 200 },
      { name: 'Hearth stone', quantity: '1', unit: 'ea', estimatedCost: 150 },
      { name: 'Fire screen', quantity: '1', unit: 'ea', estimatedCost: 100 },
    ];
    materials.push(...fireplaceSpecific);
  }
  
  return materials;
}

function estimateMaterialQuantity(name: string, input: string, trade: string): string {
  // Very rough estimates - in production this would be more sophisticated
  const lower = name.toLowerCase();
  
  if (/brick/.test(lower)) return '500';
  if (/mortar|cement/.test(lower)) return '10';
  if (/sand/.test(lower)) return '2';
  if (/flue/.test(lower)) return '4';
  if (/lumber/.test(lower)) return '20';
  if (/wire|romex/.test(lower)) return '250';
  
  return '1';
}

function estimateMaterialCost(name: string, trade: string): number {
  const lower = name.toLowerCase();
  
  if (/brick/.test(lower)) return 1.50;
  if (/mortar|cement/.test(lower)) return 15;
  if (/sand/.test(lower)) return 50;
  if (/flue/.test(lower)) return 75;
  if (/damper/.test(lower)) return 150;
  if (/cap/.test(lower)) return 100;
  if (/romex|wire/.test(lower)) return 0.50;
  if (/outlet|switch/.test(lower)) return 5;
  if (/breaker/.test(lower)) return 25;
  
  return 25; // Default
}

function generateLabor(trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL): EnhancedJobData['labor'] {
  const rates: Record<string, number> = {
    'Lead Mason': 75,
    'Mason Helper': 45,
    'Laborer': 35,
    'Licensed Electrician': 85,
    'Licensed Plumber': 80,
    'Carpenter': 65,
    'Apprentice': 40,
    'Helper': 35,
    'Tradesperson': 60,
  };
  
  return knowledge.laborRoles.map(role => ({
    role,
    hours: knowledge.typicalDaysPerUnit * 8,
    rate: rates[role] || 50,
  }));
}

function calculateEstimatedDays(input: string, trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL): number {
  const lower = input.toLowerCase();
  let multiplier = 1;
  
  // Adjust based on scope keywords
  if (/large|big|full|complete|entire/.test(lower)) multiplier = 1.5;
  if (/small|minor|simple|quick/.test(lower)) multiplier = 0.5;
  if (/repair|fix|patch/.test(lower)) multiplier = 0.3;
  
  return Math.ceil(knowledge.typicalDaysPerUnit * multiplier);
}

function calculateLaborCost(trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL): number {
  const labor = generateLabor(trade, knowledge);
  return labor.reduce((sum, l) => sum + (l.hours * l.rate), 0);
}

function calculateMaterialCost(trade: string, knowledge: typeof TRADE_KNOWLEDGE.GENERAL): number {
  // Rough estimate based on trade
  const baseCosts: Record<string, number> = {
    MASONRY: 2500,
    ELECTRICAL: 800,
    PLUMBING: 600,
    CARPENTRY: 1000,
    GENERAL: 500,
  };
  
  return baseCosts[trade] || baseCosts.GENERAL;
}

function generateAssumptions(input: string, trade: string, searchText: string): string[] {
  const assumptions = [
    'Work area is accessible',
    'Client has approved scope and budget',
    'Required permits will be obtained before work begins',
  ];
  
  if (trade === 'MASONRY') {
    assumptions.push('Foundation/base is structurally sound');
    assumptions.push('Weather conditions permit masonry work');
  }
  if (trade === 'ELECTRICAL') {
    assumptions.push('Existing panel has capacity for new circuits');
  }
  if (trade === 'PLUMBING') {
    assumptions.push('Existing supply pressure is adequate');
  }
  
  return assumptions;
}

function generateExclusions(input: string, trade: string): string[] {
  const exclusions = [
    'Work not specifically described in scope',
    'Permit fees (billed separately if applicable)',
    'Repair of pre-existing conditions not in scope',
  ];
  
  if (trade === 'MASONRY' && /fireplace/.test(input.toLowerCase())) {
    exclusions.push('Gas line installation (requires licensed gas fitter)');
    exclusions.push('Interior finishing/painting around fireplace');
  }
  
  return exclusions;
}

function extractKeywords(input: string, text: string): string[] {
  const candidates = new Set<string>();
  const words = (input + ' ' + text)
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, '')
    .split(/\s+/);

  words.forEach(w => {
    if (w.length > 5) candidates.add(w);
  });

  return Array.from(candidates).slice(0, 15);
}

// Legacy export for backward compatibility
export { playwrightResolve as default };
