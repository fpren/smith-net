# ENVIRONMENT-SETUP

**Project:** Smith Net (TradeMesh / Guild of Smiths)
**Project root:** `/Users/fegensprenelon/smith-net`
**Sigma run mode:** retrofit (existing codebase, reverse-engineering specs)
**Setup date:** 2026-04-30
**Sigma Protocol version:** 2.6.0
**Score:** 92 / 100

---

## 1. System

| Field | Value |
|---|---|
| OS | macOS 15.6 (Darwin 24.6.0) |
| Architecture | arm64 (Apple Silicon) |
| Hostname | Fegenss-Mini.lan |
| Shell | zsh |

## 2. Toolchain

| Tool | Version | Required | Status |
|---|---|---|---|
| Node.js | v24.12.0 | 18+ | ✅ |
| npm | 11.6.2 | 9+ | ✅ |
| pnpm | 10.28.0 | optional | ✅ |
| yarn | 1.22.22 | optional | ✅ |
| bun | — | optional | ❌ not installed |
| git | 2.50.1 | required | ✅ configured (Fegens Prenelon / fprenelon@gmail.com) |
| Python | 3.14.3 | optional | ✅ |
| Docker | 28.0.4 | optional | ✅ |
| GitHub CLI | 2.83.2 | recommended | ✅ logged in as `fpren` |
| Claude Code | 2.1.123 | required (selected platform) | ✅ |
| Cursor | 2.1.49 | optional | ✅ |
| jq | 1.7.1 | recommended | ✅ |

## 3. MCP Servers

### Connected
| MCP | Transport | Used by Sigma |
|---|---|---|
| `plugin:context7:context7` | stdio (npx) | Steps 2, 5, 6, 8, 11 (live library docs) |
| `playwright` | stdio (npx) | Steps 5, 7 (UI inspection / mockups) |
| `claude.ai PubMed` | HTTP | not used by Sigma |
| `claude.ai Gmail` | HTTP | not used by Sigma |
| `claude.ai Google Calendar` | HTTP | not used by Sigma |

### Not connected (optional, can install later)
| MCP | Recommended for | Install command |
|---|---|---|
| `supabase` | Steps 2, 8 (project uses Supabase) | `claude mcp add supabase -s user -- npx -y @supabase/mcp-server-supabase@latest --access-token=$SUPABASE_PAT` |
| `exa` | Step 1 (market research), Step 9 (avatars) | `claude mcp add exa -s user -- npx -y exa-mcp-server --api-key $EXA_KEY` |
| `firecrawl` | Step 1 (competitor scrapes) | `claude mcp add firecrawl -s user -- env FIRECRAWL_API_KEY=$KEY npx -y firecrawl-mcp` |
| `21st-magic` | Steps 6, 7 (UI components) | `claude mcp add magic -s user -- npx -y @21st-dev/magic@latest --api-key $KEY` |

### Failed
| MCP | Issue |
|---|---|
| `plugin:greptile:greptile` | HTTP connect failure (token / endpoint). Not required for Sigma. |

## 4. Foundation Skills

Sigma references foundation skills by name but does not ship them. Existing skill ecosystem covers them via mapping:

| Sigma slot | Mapped existing skill(s) | Coverage |
|---|---|---|
| `research` | `ecc-deep-research`, `ecc-exa-search`, `ecc-search-first`, `ecc-market-research` | ✅ |
| `verification` | `superpowers:verification-before-completion` | ✅ |
| `frontend-design` | `frontend-design:frontend-design` | ✅ |
| `bdd-scenarios` | `superpowers:test-driven-development` | ⚠️ TDD-not-BDD, BDD scenarios will be authored inline in Step 11 |
| `hormozi-frameworks` | inline in Step 1 / Step 1.5 step files | ✅ |
| `output-generation` | inline templates per step file | ✅ |
| `database` | `ecc-postgres-patterns` | ✅ |
| `architecture` | `ecc-android-clean-architecture` (Android-specific) | ✅ for Android layer; backend/desktop handled inline |
| `agent-harness` | `ecc-agent-harness-construction` | ✅ bonus |

## 5. Project Scaffold

`/docs/` directory created with all 32 Sigma subdirectories (idempotent — preserved existing `superpowers/` directory):

```
docs/
├── accessibility/   ├── analysis/       ├── api/           ├── architecture/
├── avatars/         ├── components/     ├── database/      ├── deployments/
├── design/          ├── development/    ├── flows/         ├── implementation/
├── journeys/        ├── landing-page/   ├── legal/         ├── ops/
├── performance/     ├── prds/           ├── reports/       ├── research/
├── screens/         ├── security/       ├── seo/           ├── specs/
├── states/          ├── superpowers/    ├── tech-debt/     ├── technical/
├── testing/         ├── tokens/         ├── ux/            └── wireframes/
.cursor/rules/       (created for Step 12)
.sigma/config.json   (platform + mode lock — Claude Code, retrofit)
```

## 6. Existing workspace structure (for retrofit context)

Sigma Step 1 (stack-profile.json) will need to capture this multi-workspace layout:

| Workspace | Purpose (inferred from name) |
|---|---|
| `android/` | Native Android app (likely Kotlin / Jetpack) |
| `backend/` | Server / API |
| `components/` | Shared UI components |
| `desktop/portal/` | Desktop / web portal (Vite + React + TypeScript per recent commits) |
| `shared/` | Cross-platform shared code |
| `supabase/` + `supabase-migrations/` | Supabase database & SQL migrations |
| `THE GOS INSRUCTIOS/` | Guild of Smiths product docs (legacy folder, sic) |

**Repo state at setup:** dirty working tree (uncommitted changes in `desktop/portal/`), most recent commit `3ba1138 feat(android): persistence + cross-midnight clock + live financials`.

## 7. Decisions logged

- **Run mode:** retrofit (vs. greenfield) — Smith Net is mid-build, so Steps 1-8 will reverse-engineer specs from existing code; Steps 10-13 will plan forward.
- **Target platform:** Claude Code only (Cursor/OpenCode skipped for Step 12 outputs).
- **MCP gaps deferred:** Supabase/Exa/Firecrawl/Magic install commands documented above; install when blocked, not preemptively.
- **Step 1.5 (Offer Architecture):** decision deferred until Step 1 reveals whether the project is monetized.
- **Step 9 (Landing Page):** optional — decision deferred.

## 8. Quality gate

| Category | Score |
|---|---|
| File existence (this report + scaffold + .sigma/config.json) | 20 / 20 |
| Section completeness | 30 / 30 |
| Content quality (tables, mappings, decision log) | 26 / 30 (no diagrams) |
| Checkpoints (A, B, C, D, E completed with explicit user approval) | 10 / 10 |
| Success criteria (toolchain + MCPs + skills + scaffold + report) | 6 / 10 (Bun missing, optional MCPs deferred) |
| **Total** | **92 / 100** — Grade A — Ready for Step 1 |
