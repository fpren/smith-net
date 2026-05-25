// backend/src/clientsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireClientOwner, ClientOwnerRequest } from './middleware/requireClientOwner';
import { validateBody } from './middleware/validate';
import * as clientsService from './clientsService';
import { requestLogger } from './log';
import { CreateClientBody, UpdateClientBody } from './schemas/clients';

export const clientsRouter = Router();
clientsRouter.use(authenticateToken, requireConsoleTier);

clientsRouter.get('/', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const q = typeof req.query.q === 'string' ? req.query.q : undefined;
    const clients = await clientsService.listByOwner(req.user!.id, q);
    res.json({ clients });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_list_error', err: e }, 'clients list error');
    res.status(500).json({ error: 'Failed to list clients' });
  }
});

clientsRouter.post('/', validateBody(CreateClientBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateClientBody;
    const client = await clientsService.create({ ownerId: req.user!.id, ...body });
    res.status(201).json({ client });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_create_error', err: e }, 'clients create error');
    res.status(500).json({ error: 'Failed to create client' });
  }
});

clientsRouter.get('/:id', requireClientOwner, async (req: ClientOwnerRequest, res: Response) => {
  try {
    // T6 replaces this stub with jobsService.listByClient(req.params.id, req.user!.id)
    const jobs: any[] = [];
    res.json({ client: req.client, jobs });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_get_error', err: e }, 'clients get error');
    res.status(500).json({ error: 'Failed to get client' });
  }
});

clientsRouter.patch('/:id', requireClientOwner, validateBody(UpdateClientBody), async (req: ClientOwnerRequest, res: Response) => {
  try {
    const client = await clientsService.update(req.user!.id, req.params.id, req.body as UpdateClientBody);
    if (!client) return res.status(404).json({ error: 'Client not found' });
    res.json({ client });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_update_error', err: e }, 'clients update error');
    res.status(500).json({ error: 'Failed to update client' });
  }
});

clientsRouter.delete('/:id', requireClientOwner, async (req: ClientOwnerRequest, res: Response) => {
  try {
    await clientsService.softDelete(req.user!.id, req.params.id);
    res.status(204).end();
  } catch (e: any) {
    requestLogger().error({ event: 'clients_delete_error', err: e }, 'clients delete error');
    res.status(500).json({ error: 'Failed to delete client' });
  }
});

requestLogger().info({ event: 'clients_routes_initialized' }, 'clients routes initialized');
