/**
 * ELECTRICIAN TOOLS — Trade-Specific Tooling
 * ==========================================
 *
 * Feature #15 from gosv2.txt:
 * - Circuit diagram support
 * - Job checklists (NEC/OSHA compliance)
 * - Material estimations
 * - Code compliance checks
 * - Embedded into Smith UI
 */

export interface CircuitDiagram {
  id: string;
  jobId: string;
  name: string;
  type: 'panel_upgrade' | 'new_installation' | 'renovation' | 'troubleshooting';
  circuitCount: number;
  voltage: 120 | 240 | 277 | 480;
  amperage: number;
  phases: 1 | 3;
  created: number;
  modified: number;
  // Could integrate with KiCad/EasyEDA format
  diagramData?: string; // JSON or XML format
}

export interface ElectricalChecklist {
  id: string;
  category: 'pre_job' | 'installation' | 'testing' | 'final_inspection';
  jobType: string;
  items: ChecklistItem[];
}

export interface ChecklistItem {
  id: string;
  description: string;
  necReference?: string; // e.g., "NEC 210.8(B)"
  oshaReference?: string; // e.g., "OSHA 1926.404"
  required: boolean;
  completed: boolean;
  completedAt?: number;
  completedBy?: string;
  notes?: string;
  photosRequired: boolean;
  photos?: string[];
}

export interface MaterialEstimate {
  jobId: string;
  jobType: string;
  estimatedBy: string;
  createdAt: number;
  items: MaterialItem[];
  totalCost: number;
  laborHours: number;
  margin: number; // percentage
}

export interface MaterialItem {
  category: 'wire' | 'breaker' | 'panel' | 'conduit' | 'device' | 'fixture' | 'misc';
  description: string;
  partNumber?: string;
  quantity: number;
  unit: string;
  unitCost: number;
  totalCost: number;
  supplier?: string;
  necCompliant: boolean;
}

export interface NECCheck {
  code: string; // e.g., "210.8(B)"
  title: string;
  description: string;
  applies: boolean;
  compliant: boolean;
  notes?: string;
}

export class ElectricianTools {

  /**
   * Generate job-specific electrical checklist
   * Based on job type and NEC/OSHA requirements
   */
  generateChecklist(jobType: string, voltage: number, amperage: number): ElectricalChecklist {
    const items: ChecklistItem[] = [];

    // PRE-JOB CHECKLIST
    if (jobType.includes('panel_upgrade') || jobType.includes('installation')) {
      items.push({
        id: 'pre_001',
        description: 'Verify utility service disconnect location and accessibility',
        oshaReference: 'OSHA 1926.416',
        required: true,
        completed: false,
        photosRequired: true
      });

      items.push({
        id: 'pre_002',
        description: 'Confirm load calculations meet NEC requirements',
        necReference: 'NEC 220.40',
        required: true,
        completed: false,
        photosRequired: false
      });

      items.push({
        id: 'pre_003',
        description: 'Verify grounding electrode system',
        necReference: 'NEC 250.50',
        required: true,
        completed: false,
        photosRequired: true
      });
    }

    // INSTALLATION CHECKLIST
    items.push({
      id: 'inst_001',
      description: 'All conductors properly sized per NEC Table 310.16',
      necReference: 'NEC 310.16',
      required: true,
      completed: false,
      photosRequired: false
    });

    items.push({
      id: 'inst_002',
      description: 'GFCI protection installed in required locations',
      necReference: 'NEC 210.8',
      required: true,
      completed: false,
      photosRequired: true
    });

    items.push({
      id: 'inst_003',
      description: 'AFCI protection installed per code requirements',
      necReference: 'NEC 210.12',
      required: true,
      completed: false,
      photosRequired: false
    });

    items.push({
      id: 'inst_004',
      description: 'Proper working clearances maintained around panel',
      necReference: 'NEC 110.26',
      oshaReference: 'OSHA 1926.403',
      required: true,
      completed: false,
      photosRequired: true
    });

    // TESTING CHECKLIST
    items.push({
      id: 'test_001',
      description: 'Insulation resistance test (megger) >200MΩ all circuits',
      required: true,
      completed: false,
      photosRequired: false
    });

    items.push({
      id: 'test_002',
      description: 'Voltage verification: L-N and L-G within tolerance',
      required: true,
      completed: false,
      photosRequired: false
    });

    items.push({
      id: 'test_003',
      description: 'Ground fault testing: all GFCI devices trip within spec',
      necReference: 'NEC 210.8',
      required: true,
      completed: false,
      photosRequired: false
    });

    items.push({
      id: 'test_004',
      description: 'Load balance check: phases within 10% of each other',
      required: true,
      completed: false,
      photosRequired: false
    });

    // FINAL INSPECTION
    items.push({
      id: 'final_001',
      description: 'All circuits properly labeled at panel',
      necReference: 'NEC 408.4',
      required: true,
      completed: false,
      photosRequired: true
    });

    items.push({
      id: 'final_002',
      description: 'Panel cover installed with all knockouts filled',
      necReference: 'NEC 110.12(A)',
      oshaReference: 'OSHA 1926.403',
      required: true,
      completed: false,
      photosRequired: true
    });

    items.push({
      id: 'final_003',
      description: 'Arc flash labeling applied per NFPA 70E',
      required: true,
      completed: false,
      photosRequired: true
    });

    return {
      id: `checklist_${Date.now()}`,
      category: 'installation',
      jobType,
      items
    };
  }

  /**
   * Estimate materials for common electrical jobs
   */
  estimateMaterials(
    jobType: string,
    amperage: number,
    circuitCount: number,
    squareFeet?: number
  ): MaterialEstimate {
    const items: MaterialItem[] = [];

    // Panel upgrade materials
    if (jobType.includes('panel_upgrade')) {
      // Main breaker
      items.push({
        category: 'breaker',
        description: `${amperage}A Main Breaker (Square D QO)`,
        partNumber: `QO${amperage/100}100`,
        quantity: 1,
        unit: 'ea',
        unitCost: amperage === 100 ? 68.40 : amperage === 200 ? 145.00 : 89.00,
        totalCost: amperage === 100 ? 68.40 : amperage === 200 ? 145.00 : 89.00,
        supplier: 'Home Depot / Grainger',
        necCompliant: true
      });

      // Feeder wire
      const wireSize = amperage === 100 ? '#2 AWG' : amperage === 200 ? '#2/0 AWG' : '#4 AWG';
      const wireCost = amperage === 100 ? 1.40 : amperage === 200 ? 2.80 : 1.80;
      const wireFeet = 30;

      items.push({
        category: 'wire',
        description: `${wireSize} THHN wire`,
        quantity: wireFeet,
        unit: 'ft',
        unitCost: wireCost,
        totalCost: wireFeet * wireCost,
        necCompliant: true
      });

      // Conduit and fittings
      items.push({
        category: 'conduit',
        description: 'Conduit, fittings, connectors, strain relief',
        quantity: 1,
        unit: 'lot',
        unitCost: 45.00,
        totalCost: 45.00,
        necCompliant: true
      });
    }

    // GFCI/AFCI devices (estimate 20% of circuits need GFCI, 80% need AFCI)
    const gfciCount = Math.ceil(circuitCount * 0.2);
    const afciCount = Math.ceil(circuitCount * 0.8);

    items.push({
      category: 'device',
      description: 'GFCI outlets (15A or 20A)',
      quantity: gfciCount,
      unit: 'ea',
      unitCost: 22.50,
      totalCost: gfciCount * 22.50,
      necCompliant: true
    });

    items.push({
      category: 'device',
      description: 'AFCI breakers',
      quantity: afciCount,
      unit: 'ea',
      unitCost: 45.00,
      totalCost: afciCount * 45.00,
      necCompliant: true
    });

    // Miscellaneous
    items.push({
      category: 'misc',
      description: 'Wire nuts, labels, tape, misc hardware',
      quantity: 1,
      unit: 'lot',
      unitCost: 35.00,
      totalCost: 35.00,
      necCompliant: true
    });

    const totalCost = items.reduce((sum, item) => sum + item.totalCost, 0);

    // Estimate labor hours (rule of thumb: panel upgrade = 4-8 hours)
    const laborHours = jobType.includes('panel_upgrade')
      ? (amperage / 25) + (circuitCount * 0.5)
      : circuitCount * 0.75;

    return {
      jobId: '',
      jobType,
      estimatedBy: 'Smith AI',
      createdAt: Date.now(),
      items,
      totalCost,
      laborHours: Math.round(laborHours * 10) / 10,
      margin: 15 // 15% markup
    };
  }

  /**
   * Check NEC compliance for common scenarios
   */
  checkNECCompliance(
    jobType: string,
    voltage: number,
    amperage: number,
    location: string
  ): NECCheck[] {
    const checks: NECCheck[] = [];

    // GFCI requirements
    if (location.toLowerCase().includes('kitchen') || 
        location.toLowerCase().includes('bathroom') ||
        location.toLowerCase().includes('garage') ||
        location.toLowerCase().includes('outdoor')) {
      checks.push({
        code: '210.8(A)',
        title: 'GFCI Protection - Dwelling Units',
        description: 'GFCI protection required for receptacles in kitchens, bathrooms, garages, outdoors, and other specified locations',
        applies: true,
        compliant: false, // Must be verified during installation
        notes: `Location "${location}" requires GFCI protection`
      });
    }

    // AFCI requirements
    if (jobType.includes('dwelling') || jobType.includes('residential')) {
      checks.push({
        code: '210.12(A)',
        title: 'AFCI Protection - Dwelling Units',
        description: 'AFCI protection required for all 120V, 15A and 20A branch circuits supplying outlets in dwelling unit bedrooms, family rooms, dining rooms, living rooms, parlors, libraries, dens, sunrooms, recreation rooms, closets, hallways, laundry areas',
        applies: true,
        compliant: false,
        notes: 'AFCI breakers or combination devices required'
      });
    }

    // Working clearance
    checks.push({
      code: '110.26(A)',
      title: 'Working Space Around Electrical Equipment',
      description: `Minimum working space: ${voltage <= 150 ? '3 feet' : voltage <= 600 ? '3.5 feet' : '4 feet'} clear in front of equipment`,
      applies: true,
      compliant: false,
      notes: 'Verify clearance before final inspection'
    });

    // Conductor ampacity
    checks.push({
      code: '310.16',
      title: 'Conductor Ampacity',
      description: 'Conductors must be sized appropriately for the load and installation conditions',
      applies: true,
      compliant: false,
      notes: `${amperage}A service requires minimum conductor size per NEC Table 310.16`
    });

    // Grounding
    checks.push({
      code: '250.50',
      title: 'Grounding Electrode System',
      description: 'All grounding electrodes present at the building must be bonded together',
      applies: true,
      compliant: false,
      notes: 'Verify grounding electrode conductor size and connections'
    });

    return checks;
  }

  /**
   * Generate load calculation (simplified)
   * Real implementation would use NEC Article 220
   */
  calculateLoad(squareFeet: number, additionalLoads: { [key: string]: number }): {
    generalLighting: number;
    smallAppliance: number;
    laundry: number;
    additional: number;
    totalVA: number;
    demandVA: number;
    recommendedAmperage: number;
  } {
    // General lighting: 3 VA per sq ft (NEC 220.12)
    const generalLighting = squareFeet * 3;

    // Small appliance: 1500 VA per circuit, minimum 2 circuits (NEC 220.52(A))
    const smallAppliance = 2 * 1500;

    // Laundry: 1500 VA (NEC 220.52(B))
    const laundry = 1500;

    // Additional loads (from input)
    const additional = Object.values(additionalLoads).reduce((sum, val) => sum + val, 0);

    const totalVA = generalLighting + smallAppliance + laundry + additional;

    // Apply demand factors (simplified from NEC 220.42)
    let demandVA = 0;
    if (totalVA <= 3000) {
      demandVA = totalVA;
    } else if (totalVA <= 120000) {
      demandVA = 3000 + (totalVA - 3000) * 0.35;
    } else {
      demandVA = 3000 + (117000 * 0.35) + (totalVA - 120000) * 0.25;
    }

    // Convert to amperage at 240V
    const amperage = demandVA / 240;

    // Round up to next standard service size
    const recommendedAmperage = amperage <= 100 ? 100 :
                                 amperage <= 150 ? 150 :
                                 amperage <= 200 ? 200 : 400;

    return {
      generalLighting,
      smallAppliance,
      laundry,
      additional,
      totalVA,
      demandVA,
      recommendedAmperage
    };
  }
}

export const electricianTools = new ElectricianTools();
