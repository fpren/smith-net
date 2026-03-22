/**
 * BLS Wage Data Service
 * Looks up median hourly wages by zip code and trade (SOC code)
 */

import { supabase } from './supabase';

export interface WageResult {
  metroName: string;
  occupationTitle: string;
  medianHourly: number | null;
  meanHourly: number | null;
  p25Hourly: number | null;
  p75Hourly: number | null;
}

export class WageDataService {
  async getWageByZipAndTrade(zipCode: string, socCode: string): Promise<WageResult | null> {
    // Step 1: look up metro area from zip code
    const { data: zipRow, error: zipError } = await supabase
      .from('zip_metro_map')
      .select('cbsa_code, metro_name')
      .eq('zip_code', zipCode)
      .single();

    if (zipError || !zipRow) {
      console.warn(`[WageData] No metro mapping found for zip: ${zipCode}`);
      return null;
    }

    const { cbsa_code: cbsaCode, metro_name: metroName } = zipRow;

    // Step 2: look up wage data by metro + SOC code
    const { data: wageRow, error: wageError } = await supabase
      .from('wage_data')
      .select('occupation_title, median_hourly, mean_hourly, p25_hourly, p75_hourly')
      .eq('metro_area_code', cbsaCode)
      .eq('soc_code', socCode)
      .single();

    if (wageError || !wageRow) {
      console.warn(`[WageData] No wage data found for metro: ${cbsaCode}, SOC: ${socCode}`);
      return null;
    }

    return {
      metroName: metroName ?? cbsaCode,
      occupationTitle: wageRow.occupation_title,
      medianHourly: wageRow.median_hourly,
      meanHourly: wageRow.mean_hourly,
      p25Hourly: wageRow.p25_hourly,
      p75Hourly: wageRow.p75_hourly,
    };
  }
}

export const wageDataService = new WageDataService();
