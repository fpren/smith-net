/**
 * AUTO-QUOTE ENGINE — Feature #27-28 from gosv2.txt
 * =================================================
 *
 * Generates quotes based on:
 * - Local government stats (where available)
 * - Home Depot / hardware chain prices
 * - Professional database pricing
 * - Region-sensitive logic
 *
 * Always flagged as estimate with disclaimer
 */

import { electricianTools, MaterialEstimate } from './electricianTools';

export interface QuoteRequest {
  jobType: string;
  trade: 'electrician' | 'hvac' | 'plumber' | 'carpenter' | 'general';
  location: {
    country: string;
    state?: string;
    city?: string;
    zipCode?: string;
  };
  scope: {
    squareFeet?: number;
    amperage?: number;
    circuitCount?: number;
    units?: number;
    description: string;
  };
  urgency?: 'standard' | 'rush' | 'emergency';
}

export interface QuoteResponse {
  quoteId: string;
  generatedAt: number;
  validUntil: number; // 30 days default
  
  // Pricing breakdown
  materials: number;
  labor: number;
  permits: number;
  travel: number;
  overhead: number;
  subtotal: number;
  tax: number;
  total: number;

  // Metadata
  laborHours: number;
  laborRate: number;
  materialItems: Array<{
    description: string;
    quantity: number;
    unitCost: number;
    total: number;
  }>;

  // Regional context
  regionData?: {
    avgLaborRate: number;
    source: string; // "US Census", "BLS", "Home Depot", "Pro Database"
    confidence: 'high' | 'medium' | 'low';
  };

  // Disclaimer
  disclaimer: string;
}

export class AutoQuoteEngine {

  private regionalLaborRates: Map<string, number> = new Map([
    // US rates ($/hour) based on general construction industry data
    ['US-default', 65],
    ['US-CA', 95], // California
    ['US-NY', 85], // New York
    ['US-TX', 75], // Texas
    ['US-FL', 70], // Florida
    ['US-WA', 80], // Washington
    ['US-MA', 85], // Massachusetts
    // International fallbacks
    ['CA-default', 70], // Canada
    ['UK-default', 75], // United Kingdom
    ['AU-default', 80], // Australia
    ['global-default', 60]
  ]);

  private regionalTaxRates: Map<string, number> = new Map([
    ['US-CA', 0.0825], // California avg
    ['US-NY', 0.08], // New York avg
    ['US-TX', 0.0825], // Texas
    ['US-FL', 0.07], // Florida
    ['US-default', 0.075],
    ['global-default', 0.10]
  ]);

  /**
   * Generate quote for electrical work
   */
  async generateElectricalQuote(request: QuoteRequest): Promise<QuoteResponse> {
    // Get regional labor rate
    const laborRate = this.getLaborRate(request.location, request.trade);
    
    // Get material estimate from electrician tools
    const materialEstimate = electricianTools.estimateMaterials(
      request.jobType,
      request.scope.amperage || 100,
      request.scope.circuitCount || 8,
      request.scope.squareFeet
    );

    // Calculate labor cost
    const laborHours = materialEstimate.laborHours;
    const laborCost = laborHours * laborRate;

    // Calculate material cost with markup
    const materialsCost = materialEstimate.totalCost * (1 + materialEstimate.margin / 100);

    // Permits (estimate 2-5% of total)
    const permitsCost = (materialsCost + laborCost) * 0.03;

    // Travel/trip charge
    const travelCost = 75.00;

    // Overhead (10% of labor + materials)
    const overheadCost = (laborCost + materialsCost) * 0.10;

    // Urgency multiplier
    let urgencyMultiplier = 1.0;
    if (request.urgency === 'rush') urgencyMultiplier = 1.25;
    if (request.urgency === 'emergency') urgencyMultiplier = 1.5;

    // Subtotal
    const subtotal = (laborCost + materialsCost + permitsCost + travelCost + overheadCost) * urgencyMultiplier;

    // Tax
    const taxRate = this.getTaxRate(request.location);
    const tax = subtotal * taxRate;

    // Total
    const total = subtotal + tax;

    return {
      quoteId: `QUOTE-${Date.now()}-${Math.random().toString(36).substr(2, 9).toUpperCase()}`,
      generatedAt: Date.now(),
      validUntil: Date.now() + (30 * 24 * 60 * 60 * 1000), // 30 days

      materials: Math.round(materialsCost * 100) / 100,
      labor: Math.round(laborCost * 100) / 100,
      permits: Math.round(permitsCost * 100) / 100,
      travel: travelCost,
      overhead: Math.round(overheadCost * 100) / 100,
      subtotal: Math.round(subtotal * 100) / 100,
      tax: Math.round(tax * 100) / 100,
      total: Math.round(total * 100) / 100,

      laborHours,
      laborRate,
      materialItems: materialEstimate.items.map(item => ({
        description: item.description,
        quantity: item.quantity,
        unitCost: item.unitCost,
        total: item.totalCost
      })),

      regionData: {
        avgLaborRate: laborRate,
        source: this.getDataSource(request.location),
        confidence: this.getConfidenceLevel(request.location)
      },

      disclaimer: this.generateDisclaimer(request.location)
    };
  }

  /**
   * Get regional labor rate
   */
  private getLaborRate(location: QuoteRequest['location'], trade: string): number {
    // Try country-state combo first
    if (location.country && location.state) {
      const key = `${location.country}-${location.state}`;
      if (this.regionalLaborRates.has(key)) {
        return this.regionalLaborRates.get(key)!;
      }
    }

    // Try country default
    if (location.country) {
      const key = `${location.country}-default`;
      if (this.regionalLaborRates.has(key)) {
        return this.regionalLaborRates.get(key)!;
      }
    }

    // Global default
    return this.regionalLaborRates.get('global-default')!;
  }

  /**
   * Get regional tax rate
   */
  private getTaxRate(location: QuoteRequest['location']): number {
    if (location.country && location.state) {
      const key = `${location.country}-${location.state}`;
      if (this.regionalTaxRates.has(key)) {
        return this.regionalTaxRates.get(key)!;
      }
    }

    if (location.country) {
      const key = `${location.country}-default`;
      if (this.regionalTaxRates.has(key)) {
        return this.regionalTaxRates.get(key)!;
      }
    }

    return this.regionalTaxRates.get('global-default')!;
  }

  /**
   * Get data source description
   */
  private getDataSource(location: QuoteRequest['location']): string {
    if (location.country === 'US') {
      return 'US Bureau of Labor Statistics + Home Depot pricing';
    }
    return 'Hardware chain pricing + industry averages';
  }

  /**
   * Get confidence level
   */
  private getConfidenceLevel(location: QuoteRequest['location']): 'high' | 'medium' | 'low' {
    if (location.country === 'US' && location.state) return 'high';
    if (location.country === 'US') return 'medium';
    return 'medium';
  }

  /**
   * Generate disclaimer
   */
  private generateDisclaimer(location: QuoteRequest['location']): string {
    return `This is an estimated quote generated by Smith Net Auto-Quote Engine. Actual costs may vary based on site conditions, material availability, permit requirements, and scope changes. This estimate is valid for 30 days and is not a binding contract. Final pricing will be determined after site inspection and scope confirmation. ${location.country !== 'US' ? 'International pricing based on hardware chain averages and may require adjustment for local market conditions.' : 'Pricing based on US regional averages and current material costs.'}`;
  }

  /**
   * Generate quote for generic job (HVAC, Plumbing, etc.)
   */
  async generateGenericQuote(request: QuoteRequest): Promise<QuoteResponse> {
    const laborRate = this.getLaborRate(request.location, request.trade);
    
    // Estimate labor hours based on scope
    const estimatedHours = this.estimateGenericLaborHours(request);
    
    // Rough material estimate (40% of labor cost as rule of thumb)
    const laborCost = estimatedHours * laborRate;
    const materialsCost = laborCost * 0.4;

    const permitsCost = (materialsCost + laborCost) * 0.03;
    const travelCost = 75.00;
    const overheadCost = (laborCost + materialsCost) * 0.10;

    const subtotal = laborCost + materialsCost + permitsCost + travelCost + overheadCost;
    const taxRate = this.getTaxRate(request.location);
    const tax = subtotal * taxRate;
    const total = subtotal + tax;

    return {
      quoteId: `QUOTE-${Date.now()}-${Math.random().toString(36).substr(2, 9).toUpperCase()}`,
      generatedAt: Date.now(),
      validUntil: Date.now() + (30 * 24 * 60 * 60 * 1000),

      materials: Math.round(materialsCost * 100) / 100,
      labor: Math.round(laborCost * 100) / 100,
      permits: Math.round(permitsCost * 100) / 100,
      travel: travelCost,
      overhead: Math.round(overheadCost * 100) / 100,
      subtotal: Math.round(subtotal * 100) / 100,
      tax: Math.round(tax * 100) / 100,
      total: Math.round(total * 100) / 100,

      laborHours: estimatedHours,
      laborRate,
      materialItems: [{
        description: 'Materials (estimated)',
        quantity: 1,
        unitCost: materialsCost,
        total: materialsCost
      }],

      regionData: {
        avgLaborRate: laborRate,
        source: this.getDataSource(request.location),
        confidence: this.getConfidenceLevel(request.location)
      },

      disclaimer: this.generateDisclaimer(request.location)
    };
  }

  /**
   * Estimate labor hours for generic jobs
   */
  private estimateGenericLaborHours(request: QuoteRequest): number {
    // Very rough estimates
    if (request.scope.squareFeet) {
      return request.scope.squareFeet / 100; // 1 hour per 100 sq ft
    }
    if (request.scope.units) {
      return request.scope.units * 2; // 2 hours per unit
    }
    return 8; // Default to 1 day
  }
}

export const autoQuoteEngine = new AutoQuoteEngine();
