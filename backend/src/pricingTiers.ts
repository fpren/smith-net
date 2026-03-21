/**
 * PRICING TIERS — Features #33-34 from gosv2.txt
 * ===============================================
 *
 * 3-6-9 Pyramid Pricing Logic (Internal Financial Code)
 * - Aggressively drops per-head cost as headcount rises
 * - Standard vs Hybrid product tiers
 * - Solo / Foreman / Enterprise / Nation account types
 *
 * This is internal pricing logic, not exposed to UI as "3-6-9"
 */

export type AccountTier = 'solo' | 'foreman' | 'enterprise' | 'nation';
export type ProductMode = 'standard' | 'hybrid';

export interface PricingResult {
  accountTier: AccountTier;
  productMode: ProductMode;
  headcount: number;
  
  // Per-user pricing
  basePrice: number;
  volumeDiscount: number;
  finalPricePerUser: number;
  
  // Total
  monthlyTotal: number;
  annualTotal: number;
  
  // Breakdown
  breakdown: {
    baseTier: string;
    headcountBracket: string;
    discountApplied: string;
  };
}

export class PricingTiers {

  // Base prices per user/month (before volume discount)
  private basePrices: Record<AccountTier, Record<ProductMode, number>> = {
    solo: {
      standard: 15, // Solo Standard: $15/month
      hybrid: 30    // Solo Hybrid (AI Supervisor): $30/month
    },
    foreman: {
      standard: 12, // Foreman Standard: $12/user/month
      hybrid: 25    // Foreman Hybrid: $25/user/month
    },
    enterprise: {
      standard: 10, // Enterprise Standard: $10/user/month
      hybrid: 20    // Enterprise Hybrid: $20/user/month
    },
    nation: {
      standard: 8,  // Nation Standard: $8/user/month
      hybrid: 15    // Nation Hybrid: $15/user/month
    }
  };

  /**
   * 3-6-9 Pyramid: Volume discount tiers
   * 
   * The discount increases dramatically as headcount grows:
   * - 1-3 users: 0% discount (full price)
   * - 4-6 users: 10% discount
   * - 7-9 users: 20% discount
   * - 10-20 users: 30% discount
   * - 21-50 users: 40% discount
   * - 51-100 users: 50% discount
   * - 101-500 users: 60% discount
   * - 501+ users: 70% discount
   *
   * This creates aggressive per-head cost reduction for large organizations
   */
  private getVolumeDiscount(headcount: number): number {
    if (headcount <= 3) return 0;
    if (headcount <= 6) return 0.10;
    if (headcount <= 9) return 0.20;
    if (headcount <= 20) return 0.30;
    if (headcount <= 50) return 0.40;
    if (headcount <= 100) return 0.50;
    if (headcount <= 500) return 0.60;
    return 0.70; // 501+ users get 70% discount
  }

  /**
   * Calculate pricing for an account
   */
  calculate(
    accountTier: AccountTier,
    productMode: ProductMode,
    headcount: number
  ): PricingResult {
    // Get base price
    const basePrice = this.basePrices[accountTier][productMode];

    // Get volume discount
    const volumeDiscount = this.getVolumeDiscount(headcount);

    // Calculate final price per user
    const finalPricePerUser = basePrice * (1 - volumeDiscount);

    // Calculate total
    const monthlyTotal = finalPricePerUser * headcount;
    const annualTotal = monthlyTotal * 12;

    // Get headcount bracket description
    const bracket = this.getHeadcountBracket(headcount);

    return {
      accountTier,
      productMode,
      headcount,
      basePrice,
      volumeDiscount,
      finalPricePerUser: Math.round(finalPricePerUser * 100) / 100,
      monthlyTotal: Math.round(monthlyTotal * 100) / 100,
      annualTotal: Math.round(annualTotal * 100) / 100,
      breakdown: {
        baseTier: `${accountTier.charAt(0).toUpperCase() + accountTier.slice(1)} - ${productMode.charAt(0).toUpperCase() + productMode.slice(1)}`,
        headcountBracket: bracket,
        discountApplied: `${(volumeDiscount * 100).toFixed(0)}%`
      }
    };
  }

  /**
   * Get headcount bracket description
   */
  private getHeadcountBracket(headcount: number): string {
    if (headcount <= 3) return '1-3 users';
    if (headcount <= 6) return '4-6 users';
    if (headcount <= 9) return '7-9 users';
    if (headcount <= 20) return '10-20 users';
    if (headcount <= 50) return '21-50 users';
    if (headcount <= 100) return '51-100 users';
    if (headcount <= 500) return '101-500 users';
    return '501+ users';
  }

  /**
   * Calculate bulk token credits for large tenants
   * (Feature #32: Bulk Token / Credit Support)
   */
  calculateBulkAICredits(
    accountTier: AccountTier,
    headcount: number,
    monthlyAIUsagePerUser: number = 1000000 // 1M tokens per user estimate
  ): {
    totalMonthlyTokens: number;
    bulkRateDiscount: number;
    estimatedCost: number;
  } {
    const totalTokens = headcount * monthlyAIUsagePerUser;
    
    // Bulk discount on AI credits
    let bulkDiscount = 0;
    if (totalTokens >= 1000000000) bulkDiscount = 0.50; // 1B+ tokens: 50% off
    else if (totalTokens >= 100000000) bulkDiscount = 0.40; // 100M+ tokens: 40% off
    else if (totalTokens >= 10000000) bulkDiscount = 0.30; // 10M+ tokens: 30% off
    else if (totalTokens >= 1000000) bulkDiscount = 0.20; // 1M+ tokens: 20% off

    // Base rate: $0.50 per 1M tokens (simplified)
    const baseRate = 0.50;
    const effectiveRate = baseRate * (1 - bulkDiscount);
    const estimatedCost = (totalTokens / 1000000) * effectiveRate;

    return {
      totalMonthlyTokens: totalTokens,
      bulkRateDiscount: bulkDiscount,
      estimatedCost: Math.round(estimatedCost * 100) / 100
    };
  }

  /**
   * Example pricing scenarios
   */
  getExampleScenarios(): PricingResult[] {
    return [
      // Solo scenarios
      this.calculate('solo', 'standard', 1),
      this.calculate('solo', 'hybrid', 1),
      
      // Foreman scenarios
      this.calculate('foreman', 'standard', 5),
      this.calculate('foreman', 'hybrid', 5),
      this.calculate('foreman', 'standard', 10),
      this.calculate('foreman', 'hybrid', 10),
      
      // Enterprise scenarios
      this.calculate('enterprise', 'standard', 25),
      this.calculate('enterprise', 'hybrid', 25),
      this.calculate('enterprise', 'standard', 100),
      this.calculate('enterprise', 'hybrid', 100),
      
      // Nation scenarios
      this.calculate('nation', 'standard', 1000),
      this.calculate('nation', 'hybrid', 1000),
      this.calculate('nation', 'standard', 10000),
      this.calculate('nation', 'hybrid', 10000)
    ];
  }

  /**
   * Format pricing result as human-readable string
   */
  formatPricing(result: PricingResult): string {
    return `
${result.breakdown.baseTier}
Headcount: ${result.headcount} users (${result.breakdown.headcountBracket})
─────────────────────────────────────
Base Price:         $${result.basePrice.toFixed(2)}/user/month
Volume Discount:    ${result.breakdown.discountApplied}
Final Price:        $${result.finalPricePerUser.toFixed(2)}/user/month
─────────────────────────────────────
Monthly Total:      $${result.monthlyTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}
Annual Total:       $${result.annualTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}
`;
  }
}

export const pricingTiers = new PricingTiers();
