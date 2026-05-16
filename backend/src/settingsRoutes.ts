/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Settings (non-executive configuration) + connectivity status.
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response } from 'express';

export const settingsRouter = Router();

// GET SYSTEM SETTINGS
// System Law: Settings configure reality, they do not execute work.
settingsRouter.get('/settings', (_req: Request, res: Response) => {
  // TODO: Fetch from database with user context
  // Settings never participate in payroll, planning, or reporting logic.

  res.json({
    identity: {
      userId: 'current_user',
      displayName: 'Current User',
      role: 'worker', // 'worker' | 'foreman' | 'admin'
      organizationId: 'org_123'
    },
    permissions: {
      canCreateJobs: true,
      canApproveBreaks: false,
      canFinalizePlans: false,
      canAccessArchive: true
    },
    connectivity: {
      bleMeshEnabled: true,
      onlineSyncEnabled: true,
      gatewayMode: 'hybrid', // 'online' | 'gateway' | 'hybrid'
      relayConnected: true
    },
    ai: {
      summarizationEnabled: true,
      breakRequestAssistEnabled: true,
      contextAnalysisEnabled: false
    },
    archive: {
      readOnlyAccess: true,
      exportFormats: ['pdf', 'html', 'xlsx'],
      retentionPolicy: 'forever' // System Law: Archive is forever
    },
    ui: {
      theme: 'system',
      notifications: {
        breakRequests: true,
        jobCompletions: true,
        planFinalizations: false
      }
    }
  });
});

// UPDATE SETTINGS
// System Law: Settings never change data, only configuration.
settingsRouter.patch('/settings', (req: Request, res: Response) => {
  const updates = req.body;

  // Validate that updates are configuration-only
  const allowedCategories = ['connectivity', 'ai', 'ui'];
  const requestedCategories = Object.keys(updates);

  const invalidCategories = requestedCategories.filter((cat) => !allowedCategories.includes(cat));

  if (invalidCategories.length > 0) {
    return res.status(400).json({
      error: 'Invalid settings categories',
      allowed: allowedCategories,
      requested: invalidCategories,
      systemLaw: 'Settings configure reality, they do not execute work or change data'
    });
  }

  // TODO: Validate and store settings updates
  console.log('[Settings] Updated configuration:', updates);

  res.json({
    success: true,
    updated: updates,
    systemLaw: {
      enforced: true,
      reminder: 'Settings configure reality, they do not execute work'
    }
  });
});

// GET CONNECTIVITY STATUS
// Infrastructure status, not workflow status.
settingsRouter.get('/settings/connectivity', (_req: Request, res: Response) => {
  // TODO: Get actual connectivity status from gateway manager
  res.json({
    bleMesh: {
      enabled: true,
      connectedPeers: 5,
      lastActivity: Date.now() - 30000,
      status: 'active'
    },
    online: {
      enabled: true,
      connected: true,
      lastSync: Date.now() - 60000,
      status: 'online'
    },
    gateway: {
      mode: 'hybrid',
      relayConnected: true,
      relayId: 'relay_001',
      capabilities: ['mesh_bridge', 'cloud_sync']
    },
    systemLaw: {
      enforced: true,
      reminder: 'Connectivity is infrastructure, not workflow'
    }
  });
});
