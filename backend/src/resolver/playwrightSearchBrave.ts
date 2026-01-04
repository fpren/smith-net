// resolver/playwrightSearchBrave.ts
import { chromium } from 'playwright';

export async function playwrightResolveBrave(query: string) {
  let browser;

  try {
    browser = await chromium.launch({ headless: true });

    const context = await browser.newContext({
      userAgent:
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
    });

    const page = await context.newPage();

    // --- SEARCH QUERY ---
    const searchQuery = encodeURIComponent(
      `${query} inspection steps safety requirements`
    );

    // Brave Search
    const searchUrl = `https://search.brave.com/search?q=${searchQuery}`;

    await page.goto(searchUrl, {
      waitUntil: 'domcontentloaded',
      timeout: 5000,
    });

    // --- EXTRACT TEXT (TOP RESULTS ONLY) ---
    const text = await page.evaluate(() => {
      const results: string[] = [];

      // Brave Search result selectors
      document.querySelectorAll('.snippet-title, .snippet-description, .result-header, .result-snippet').forEach(el => {
        const t = el.textContent?.trim();
        if (t && t.length < 200) results.push(t);
      });

      // Fallback: generic content extraction
      if (results.length === 0) {
        document.querySelectorAll('h2, h3, p').forEach(el => {
          const t = el.textContent?.trim();
          if (t && t.length > 20 && t.length < 200) results.push(t);
        });
      }

      return results.slice(0, 10).join('\n');
    });

    // --- VERY LIGHT NORMALIZATION ---
    const tasks: string[] = [];
    const assumptions: string[] = [];

    if (/inspect|inspection/i.test(text)) {
      tasks.push('Perform inspection prior to any work');
    }

    if (/safety|precaution|ESD|hazard/i.test(text)) {
      assumptions.push('Safety precautions required before execution');
    }

    if (/power|electrical/i.test(text)) {
      assumptions.push('Power must remain disconnected until inspection');
    }

    if (/permit|code|compliance/i.test(text)) {
      assumptions.push('Check local codes and permit requirements');
    }

    return {
      scope: `General assessment and preparation for: ${query}`,
      tasks: tasks.length ? tasks : [`Assess scope for ${query}`],
      assumptions,
      notes: ['Generated via TEST ⧉ online resolver (Brave)'],
      detectedKeywords: extractKeywords(query, text),
    };
  } catch (err) {
    // Silent failure — fallback handled by caller
    return null;
  } finally {
    if (browser) await browser.close();
  }
}

// --- VERY SIMPLE KEYWORD EXTRACTION ---
function extractKeywords(input: string, text: string): string[] {
  const candidates = new Set<string>();

  const words = (input + ' ' + text)
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, '')
    .split(/\s+/);

  words.forEach(w => {
    if (w.length > 5) candidates.add(w);
  });

  return Array.from(candidates).slice(0, 10);
}
