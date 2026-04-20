/**
 * BLS Wage Data Service
 * Looks up median hourly wages by zip code and trade (SOC code).
 *
 * Relies on two reference tables that must be populated from BLS/Census data:
 *   - zip_metro_map  (zip_code, cbsa_code, metro_name)
 *   - wage_data      (metro_area_code, soc_code, occupation_title, median_hourly, ...)
 * Returns null when tables are absent or empty (the default post-install state).
 */

import { pg, isPgEnabled } from './db';

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
    if (!isPgEnabled() || !pg) return null;
    try {
      const { rows: zipRows } = await pg.query(
        `SELECT cbsa_code, metro_name FROM zip_metro_map WHERE zip_code = $1 LIMIT 1`,
        [zipCode]
      );
      if (zipRows.length === 0) {
        console.warn(`[WageData] No metro mapping found for zip: ${zipCode}`);
        return null;
      }
      const { cbsa_code, metro_name } = zipRows[0];

      const { rows: wageRows } = await pg.query(
        `SELECT occupation_title, median_hourly, mean_hourly, p25_hourly, p75_hourly
           FROM wage_data
          WHERE metro_area_code = $1 AND soc_code = $2 LIMIT 1`,
        [cbsa_code, socCode]
      );
      if (wageRows.length === 0) {
        console.warn(`[WageData] No wage data found for metro: ${cbsa_code}, SOC: ${socCode}`);
        return null;
      }
      const w = wageRows[0];
      return {
        metroName: metro_name ?? cbsa_code,
        occupationTitle: w.occupation_title,
        medianHourly: w.median_hourly !== null ? Number(w.median_hourly) : null,
        meanHourly: w.mean_hourly !== null ? Number(w.mean_hourly) : null,
        p25Hourly: w.p25_hourly !== null ? Number(w.p25_hourly) : null,
        p75Hourly: w.p75_hourly !== null ? Number(w.p75_hourly) : null,
      };
    } catch (e) {
      console.warn('[WageData] Query failed (likely missing reference tables):', (e as Error).message);
      return null;
    }
  }
}

export const wageDataService = new WageDataService();
