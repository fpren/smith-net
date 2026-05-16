/**
 * Phase 4 Slice 3: aggregator. Each domain lives in its own router file;
 * api.ts just composes them and applies authenticateToken once at the
 * parent.
 *
 * server.ts mounts `apiRouter` at /api and `proposalPublicRouter` at /p.
 */

import { Router } from 'express';
import { authenticateToken } from './auth';
import { channelsRouter } from './channelsRoutes';
import { presenceGatewayRouter } from './presenceGatewayRoutes';
import { engagementsInvoicesRouter } from './engagementsInvoicesRoutes';
import { reportsRouter } from './reportsRoutes';
import { settingsRouter } from './settingsRoutes';
import { phase0Router } from './phase0Routes';
import { proposalsRouter, proposalPublicRouter as _proposalPublicRouter } from './proposalsRoutes';

export const apiRouter = Router();

// All /api/* routes below require a valid JWT. Public routes (/api/auth/*,
// /api/admin/*, /api/health) are mounted BEFORE this router in server.ts.
apiRouter.use(authenticateToken);

apiRouter.use(channelsRouter);
apiRouter.use(presenceGatewayRouter);
apiRouter.use(engagementsInvoicesRouter);
apiRouter.use(reportsRouter);
apiRouter.use(settingsRouter);
apiRouter.use(phase0Router);
apiRouter.use(proposalsRouter);

// Re-export so existing `import { proposalPublicRouter } from './api'`
// continues to resolve (server.ts mounts it at /p).
export const proposalPublicRouter = _proposalPublicRouter;
