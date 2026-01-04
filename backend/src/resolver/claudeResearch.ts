// resolver/claudeResearch.ts
// Claude Haiku-based job research for TEST ⧉ compile path
// Replaces browser scraping with AI-powered construction research

import { llm, LLMProvider } from '../llmInterface';

/**
 * ENHANCED JOB RESEARCH OUTPUT (same interface as playwrightSearch)
 * 
 * Uses Claude Haiku for:
 * - Accurate material costs and quantities
 * - Labor rate estimates by trade
 * - Safety and code requirements
 * - Timeline projections
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
  
  // Service Provider Info (optional)
  providerName?: string;
  providerBusinessName?: string;
  providerPhone?: string;
  providerEmail?: string;
  providerAddress?: string;
  providerGuildRole?: string;
  clientCompany?: string;
  clientEmail?: string;
}

/**
 * Trade detection from query text
 */
function detectTrade(query: string): string {
  const q = query.toLowerCase();
  
  if (q.includes('brick') || q.includes('chimney') || q.includes('fireplace') || 
      q.includes('masonry') || q.includes('mortar') || q.includes('stone')) {
    return 'MASONRY';
  }
  if (q.includes('wire') || q.includes('outlet') || q.includes('electrical') || 
      q.includes('panel') || q.includes('circuit') || q.includes('switch')) {
    return 'ELECTRICAL';
  }
  if (q.includes('pipe') || q.includes('plumbing') || q.includes('drain') || 
      q.includes('faucet') || q.includes('toilet') || q.includes('water heater')) {
    return 'PLUMBING';
  }
  if (q.includes('roof') || q.includes('shingle') || q.includes('gutter') || 
      q.includes('flashing') || q.includes('leak')) {
    return 'ROOFING';
  }
  if (q.includes('hvac') || q.includes('air condition') || q.includes('furnace') || 
      q.includes('duct') || q.includes('heating') || q.includes('cooling')) {
    return 'HVAC';
  }
  if (q.includes('paint') || q.includes('drywall') || q.includes('finish') || 
      q.includes('texture') || q.includes('primer')) {
    return 'PAINTING';
  }
  if (q.includes('carpenter') || q.includes('framing') || q.includes('trim') || 
      q.includes('cabinet') || q.includes('deck') || q.includes('wood')) {
    return 'CARPENTRY';
  }
  
  return 'GENERAL';
}

/**
 * Extract client name from query
 */
function extractClient(query: string): string | null {
  const clientMatch = query.match(/(?:for|client[:\s]+|customer[:\s]+)([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)/);
  return clientMatch ? clientMatch[1] : null;
}

/**
 * Extract location from query
 */
function extractLocation(query: string): string | null {
  const locationMatch = query.match(/(?:in|at|location[:\s]+|address[:\s]+)([^,.\n]+)/i);
  return locationMatch ? locationMatch[1].trim() : null;
}

/**
 * Estimate urgency from query
 */
function estimateUrgency(query: string): 'low' | 'moderate' | 'high' | 'urgent' {
  const q = query.toLowerCase();
  if (q.includes('emergency') || q.includes('urgent') || q.includes('asap') || q.includes('immediately')) {
    return 'urgent';
  }
  if (q.includes('soon') || q.includes('priority') || q.includes('important')) {
    return 'high';
  }
  if (q.includes('whenever') || q.includes('no rush') || q.includes('when available')) {
    return 'low';
  }
  return 'moderate';
}

/**
 * Generate job title from query
 */
function generateJobTitle(query: string, trade: string, clientName: string | null, location: string | null): string {
  // Extract main action from query
  const actionWords = ['repair', 'install', 'replace', 'fix', 'build', 'renovate', 'inspect', 'maintain'];
  let action = 'Service';
  
  for (const word of actionWords) {
    if (query.toLowerCase().includes(word)) {
      action = word.charAt(0).toUpperCase() + word.slice(1);
      break;
    }
  }
  
  // Build title
  let title = `${trade.charAt(0) + trade.slice(1).toLowerCase()} ${action}`;
  if (clientName) {
    title += ` for ${clientName}`;
  }
  if (location) {
    title += ` - ${location}`;
  }
  
  return title;
}

/**
 * Main Claude-based resolver function
 * Replaces browser scraping with AI research
 */
export async function claudeResolve(query: string): Promise<EnhancedJobData | null> {
  try {
    console.log(`[ClaudeResearch] Starting AI research for: ${query}`);
    
    // Detect trade and extract basic info
    const trade = detectTrade(query);
    const clientName = extractClient(query);
    const location = extractLocation(query);
    const urgency = estimateUrgency(query);
    const jobTitle = generateJobTitle(query, trade, clientName, location);
    
    console.log(`[ClaudeResearch] Detected: trade=${trade}, client=${clientName}, location=${location}`);
    
    // ════════════════════════════════════════════════════════════════
    // CLAUDE HAIKU RESEARCH PROMPT
    // ════════════════════════════════════════════════════════════════
    
    const researchPrompt = `You are a professional construction estimator with 25+ years experience in the ${trade} trade.
Analyze this job request and provide comprehensive, realistic construction research.

JOB REQUEST: "${query}"

DETECTED DETAILS:
- Trade: ${trade}
- Location: ${location || 'Local area (use typical US market rates)'}
- Client: ${clientName || 'Not specified'}
- Urgency: ${urgency}

RESEARCH REQUIREMENTS:
1. Provide detailed, realistic estimates based on current 2024 market rates
2. Include specific material quantities and current costs
3. Include labor breakdown by role with typical hourly rates for ${trade} work
4. Include relevant safety requirements and building codes
5. Provide realistic timeline estimates

OUTPUT FORMAT: Return ONLY valid JSON (no markdown, no explanation) with this exact structure:
{
  "scope": "Brief description of the work scope",
  "scopeDetails": ["Detailed scope item 1", "Detailed scope item 2"],
  "tasks": ["Task 1", "Task 2", "Task 3"],
  "materials": [
    {"name": "Material name", "quantity": "10", "unit": "ea", "estimatedCost": 150.00}
  ],
  "labor": [
    {"role": "Lead ${trade} Technician", "hours": 8, "rate": 85.00}
  ],
  "safetyRequirements": ["Safety item 1", "Safety item 2"],
  "codeRequirements": ["Code requirement 1"],
  "phases": [
    {"name": "Phase 1", "description": "Description", "order": 1}
  ],
  "estimatedDays": 2,
  "estimatedLaborCost": 680.00,
  "estimatedMaterialCost": 450.00,
  "estimatedTotal": 1230.00,
  "assumptions": ["Assumption 1"],
  "exclusions": ["Exclusion 1"],
  "notes": ["Additional note 1"]
}`;

    // ════════════════════════════════════════════════════════════════
    // CALL CLAUDE HAIKU
    // ════════════════════════════════════════════════════════════════
    
    const response = await llm.complete({
      messages: [{ role: 'user', content: researchPrompt }],
      model: 'claude-3-haiku-20240307',
      maxTokens: 4000,
      temperature: 0.1  // Low temperature for consistent estimates
    });

    console.log(`[ClaudeResearch] Response received: ${response.content.length} chars`);
    
    // Parse Claude's JSON response
    let researchData: any;
    try {
      // Try to extract JSON from response (handle potential markdown wrapping)
      let jsonContent = response.content.trim();
      
      // Remove markdown code blocks if present
      if (jsonContent.startsWith('```json')) {
        jsonContent = jsonContent.slice(7);
      } else if (jsonContent.startsWith('```')) {
        jsonContent = jsonContent.slice(3);
      }
      if (jsonContent.endsWith('```')) {
        jsonContent = jsonContent.slice(0, -3);
      }
      
      researchData = JSON.parse(jsonContent.trim());
    } catch (parseError) {
      console.error('[ClaudeResearch] Failed to parse JSON response:', parseError);
      console.log('[ClaudeResearch] Raw response:', response.content);
      return null;
    }
    
    // ════════════════════════════════════════════════════════════════
    // BUILD ENHANCED JOB DATA
    // ════════════════════════════════════════════════════════════════
    
    const crewSize = Math.max(1, Math.ceil((researchData.labor?.reduce((sum: number, l: any) => sum + l.hours, 0) || 8) / 8));
    
    const jobData: EnhancedJobData = {
      // Job Header
      jobTitle,
      clientName,
      location,
      jobType: `${trade} / Professional Service`,
      primaryTrade: trade,
      urgency,
      
      // Scope from Claude
      scope: researchData.scope || `${trade} service work`,
      scopeDetails: researchData.scopeDetails || [],
      
      // Tasks from Claude
      tasks: researchData.tasks || [],
      
      // Materials from Claude
      materials: researchData.materials || [],
      
      // Labor from Claude
      labor: researchData.labor || [],
      crewSize,
      
      // Timeline from Claude
      estimatedDays: researchData.estimatedDays || 1,
      phases: researchData.phases || [
        { name: 'Assessment', description: 'Initial inspection and planning', order: 1 },
        { name: 'Execution', description: 'Main work completion', order: 2 },
        { name: 'Completion', description: 'Final inspection and cleanup', order: 3 }
      ],
      
      // Financial from Claude
      estimatedLaborCost: researchData.estimatedLaborCost || 0,
      estimatedMaterialCost: researchData.estimatedMaterialCost || 0,
      estimatedTotal: researchData.estimatedTotal || 
        (researchData.estimatedLaborCost || 0) + (researchData.estimatedMaterialCost || 0) + 100, // +100 for service fee
      depositRequired: '50% on approval',
      warranty: trade === 'MASONRY' ? '2 years workmanship' : '1 year workmanship',
      
      // Safety & Code from Claude
      safetyRequirements: researchData.safetyRequirements || ['Standard PPE required', 'Work area to be secured'],
      codeRequirements: researchData.codeRequirements || [],
      permitRequired: ['ELECTRICAL', 'PLUMBING', 'MASONRY', 'ROOFING'].includes(trade),
      inspectionRequired: ['ELECTRICAL', 'PLUMBING', 'MASONRY'].includes(trade),
      
      // Notes from Claude
      assumptions: researchData.assumptions || [],
      exclusions: researchData.exclusions || [],
      notes: researchData.notes || [`AI research completed with Claude Haiku`],
      
      // Metadata
      detectedKeywords: extractKeywords(query),
      tradeClassification: trade,
      researchSources: ['Claude AI Research', 'Trade Knowledge Base'],
    };

    // Calculate total if not provided
    if (!jobData.estimatedTotal || jobData.estimatedTotal === 0) {
      jobData.estimatedTotal = jobData.estimatedLaborCost + jobData.estimatedMaterialCost + 100;
    }

    console.log(`[ClaudeResearch] Enhanced job data created:`);
    console.log(`  - ${jobData.materials.length} materials`);
    console.log(`  - ${jobData.labor.length} labor items`);
    console.log(`  - ${jobData.tasks.length} tasks`);
    console.log(`  - Total estimate: $${jobData.estimatedTotal}`);
    
    return jobData;
    
  } catch (error) {
    console.error('[ClaudeResearch] Failed:', error);
    return null;
  }
}

/**
 * Extract keywords from query for metadata
 */
function extractKeywords(query: string): string[] {
  const keywords: string[] = [];
  const words = query.toLowerCase().split(/\s+/);
  
  const importantWords = [
    'repair', 'install', 'replace', 'fix', 'build', 'renovate', 'inspect',
    'brick', 'chimney', 'fireplace', 'mortar', 'stone', 'concrete',
    'wire', 'outlet', 'electrical', 'panel', 'circuit', 'switch',
    'pipe', 'plumbing', 'drain', 'faucet', 'toilet', 'water',
    'roof', 'shingle', 'gutter', 'flashing', 'leak',
    'hvac', 'air', 'furnace', 'duct', 'heating', 'cooling',
    'paint', 'drywall', 'finish', 'texture', 'primer',
    'wood', 'trim', 'cabinet', 'deck', 'framing'
  ];
  
  for (const word of words) {
    if (importantWords.includes(word) && !keywords.includes(word)) {
      keywords.push(word);
    }
  }
  
  return keywords.slice(0, 10);
}

/**
 * Fallback resolver using local knowledge base (no API call)
 * Used when Claude is unavailable
 */
export async function localFallbackResolve(query: string): Promise<EnhancedJobData | null> {
  console.log(`[LocalFallback] Using local knowledge base for: ${query}`);
  
  const trade = detectTrade(query);
  const clientName = extractClient(query);
  const location = extractLocation(query);
  const urgency = estimateUrgency(query);
  const jobTitle = generateJobTitle(query, trade, clientName, location);
  
  // Basic estimates based on trade
  const tradeDefaults: Record<string, { laborRate: number; typicalHours: number; materialCost: number }> = {
    'MASONRY': { laborRate: 85, typicalHours: 16, materialCost: 500 },
    'ELECTRICAL': { laborRate: 95, typicalHours: 8, materialCost: 300 },
    'PLUMBING': { laborRate: 90, typicalHours: 6, materialCost: 250 },
    'ROOFING': { laborRate: 75, typicalHours: 24, materialCost: 800 },
    'HVAC': { laborRate: 100, typicalHours: 8, materialCost: 400 },
    'PAINTING': { laborRate: 55, typicalHours: 12, materialCost: 200 },
    'CARPENTRY': { laborRate: 70, typicalHours: 16, materialCost: 350 },
    'GENERAL': { laborRate: 65, typicalHours: 8, materialCost: 200 },
  };
  
  const defaults = tradeDefaults[trade] || tradeDefaults['GENERAL'];
  const laborCost = defaults.laborRate * defaults.typicalHours;
  const total = laborCost + defaults.materialCost + 100;
  
  return {
    jobTitle,
    clientName,
    location,
    jobType: `${trade} / Professional Service`,
    primaryTrade: trade,
    urgency,
    scope: `${trade} service work - ${query}`,
    scopeDetails: ['Scope details to be determined on site'],
    tasks: ['Initial assessment', 'Main work execution', 'Final inspection and cleanup'],
    materials: [{ name: 'Materials (to be specified)', quantity: null, unit: null, estimatedCost: defaults.materialCost }],
    labor: [{ role: `${trade} Technician`, hours: defaults.typicalHours, rate: defaults.laborRate }],
    crewSize: 1,
    estimatedDays: Math.ceil(defaults.typicalHours / 8),
    phases: [
      { name: 'Assessment', description: 'Initial inspection', order: 1 },
      { name: 'Execution', description: 'Main work', order: 2 },
      { name: 'Completion', description: 'Final inspection', order: 3 }
    ],
    estimatedLaborCost: laborCost,
    estimatedMaterialCost: defaults.materialCost,
    estimatedTotal: total,
    depositRequired: '50% on approval',
    warranty: '1 year workmanship',
    safetyRequirements: ['Standard PPE required'],
    codeRequirements: [],
    permitRequired: ['ELECTRICAL', 'PLUMBING', 'MASONRY'].includes(trade),
    inspectionRequired: ['ELECTRICAL', 'PLUMBING'].includes(trade),
    assumptions: ['Standard site conditions', 'Access to work area available'],
    exclusions: ['Permits (if required)', 'Unforeseen structural issues'],
    notes: ['Estimate based on local knowledge base - online research unavailable'],
    detectedKeywords: extractKeywords(query),
    tradeClassification: trade,
    researchSources: ['Local Knowledge Base'],
  };
}
